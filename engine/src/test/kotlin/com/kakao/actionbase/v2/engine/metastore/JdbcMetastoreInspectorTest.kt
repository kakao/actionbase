package com.kakao.actionbase.v2.engine.metastore

import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.service.ddl.ServiceCreateRequest
import com.kakao.actionbase.v2.engine.service.ddl.ServiceDeleteRequest
import com.kakao.actionbase.v2.engine.service.ddl.ServiceUpdateRequest
import com.kakao.actionbase.v2.engine.storage.jdbc.BaseTableConstants
import com.kakao.actionbase.v2.engine.test.GraphFixtures

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcMetastoreInspectorTest {
    private lateinit var graph: Graph
    private lateinit var inspector: JdbcMetastoreInspector

    @BeforeEach
    fun setup() {
        // withTestData: a bare fixture graph keeps its metadata in the local store, leaving the
        // JDBC table empty.
        graph = GraphFixtures.create(withTestData = true)
        inspector = JdbcMetastoreInspector(graph.metastore, graph.metadataTable)
    }

    @AfterEach
    fun teardown() {
        graph.close()
    }

    private fun dump(
        limit: Int,
        offset: Long,
    ): List<JdbcMetastoreRow> = inspector.dump(limit, offset).block()!!

    @Test
    fun `counts every row the metastore holds`() {
        val count = inspector.count().block()!!
        val all = dump(count.toInt() + 10, 0)

        assertTrue(count > 0, "the fixture writes metadata to the metastore")
        assertEquals(count, all.size.toLong(), "the count and the rows must agree")
    }

    @Test
    fun `pages without dropping or repeating a row`() {
        val all = dump(1000, 0)
        val paged = (0 until all.size step 2).flatMap { dump(2, it.toLong()) }

        assertEquals(all.map { it.id }, paged.map { it.id }, "paging must cover the table exactly once")
    }

    @Test
    fun `orders by id ascending, whatever the caller asks`() {
        val ids = dump(1000, 0).map { it.id }

        assertEquals(ids.sorted(), ids, "id ASC is the only order this serves")
    }

    @Test
    fun `decodes a row into the window it belongs to`() {
        val decoded = dump(1000, 0).mapNotNull { it.decoded }

        assertTrue(decoded.isNotEmpty(), "metadata rows decode")
        assertTrue(decoded.all { it.src != null }, "src names the scan window and must survive the decode")
        assertTrue(decoded.any { it.active }, "a bootstrapped graph has live metadata")
    }

    @Test
    fun `keeps a row the codec cannot read, so it still occupies its window`() {
        transaction(graph.metastore) {
            graph.metadataTable.insert {
                it[k] = "not-an-encoded-key"
                it[v] = "not-an-encoded-value"
                it[createdBy] = javaClass.canonicalName.take(BaseTableConstants.MAX_LENGTH)
                it[modifiedBy] = javaClass.canonicalName.take(BaseTableConstants.MAX_LENGTH)
            }
        }

        val undecodable = dump(1000, 0).single { it.k == "not-an-encoded-key" }

        assertNull(undecodable.decoded, "the codec cannot read it")
        assertNotNull(undecodable.k, "but the row is still reported, because it still fills a window")
    }

    /** The premise: DELETE rewrites the row with `Active.INACTIVE` rather than removing it. */
    @Test
    fun `reports a deleted row as a tombstone still occupying its window`() {
        val name = EntityName.fromOrigin("doomed")
        graph.serviceDdl.create(name, ServiceCreateRequest(desc = "doomed")).block()
        val before = inspector.count().block()!!

        graph.serviceDdl.update(name, ServiceUpdateRequest(active = false, desc = null)).block()
        graph.serviceDdl.delete(name, ServiceDeleteRequest()).block()

        val row = dump(1000, 0).single { it.decoded?.tgt == "doomed" }
        assertEquals(false, row.decoded?.active, "a deleted row is a tombstone, not a removal")
        assertEquals(before, inspector.count().block()!!, "and it still takes up its place in the table")
    }
}
