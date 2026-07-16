package com.kakao.actionbase.v2.engine.label.metastore

import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.edge.TraceEdge
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.metadata.Metadata
import com.kakao.actionbase.v2.engine.sql.ScanFilter
import com.kakao.actionbase.v2.engine.sql.toRowFlux
import com.kakao.actionbase.v2.engine.test.GraphFixtures

import kotlin.test.assertEquals
import kotlin.test.assertTrue

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

import reactor.kotlin.test.test

/**
 * Verifies the in-memory ByteArray-backed local store inside [LocalBackedJdbcHashLabel] behaves
 * like the H2-backed local store it replaced: indexName=null scans (as issued by
 * DdlService.getAll) resolve via the synthetic __default__ prefix-scan index.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LocalBackedJdbcHashLabelTest {
    private lateinit var graph: Graph

    @BeforeEach
    fun setup() {
        graph = GraphFixtures.create(withTestData = false)
    }

    @AfterEach
    fun teardown() {
        graph.close()
    }

    private fun localLabel(): LocalBackedJdbcHashLabel = graph.getLabel(Metadata.serviceLabelEntity.name) as LocalBackedJdbcHashLabel

    private fun serviceEdge(
        serviceName: String,
        desc: String,
    ): TraceEdge = Edge(1000L, Metadata.origin, serviceName, mapOf("desc" to desc)).toTraceEdge()

    @Test
    fun `indexName=null scan resolves via the __default__ index`() {
        val label = localLabel()
        val edge = serviceEdge("scan_probe", "scan-route")

        val scanFilter =
            ScanFilter(
                name = label.name,
                srcSet = setOf(Metadata.origin),
                dir = Direction.OUT,
                limit = 100,
            )

        label
            .mutate(listOf(edge), EdgeOperation.INSERT)
            .then(label.scan(scanFilter, emptySet()))
            .toRowFlux()
            .map { it["tgt"] }
            .collectList()
            .test()
            .assertNext { assertEquals(listOf("scan_probe"), it) }
            .verifyComplete()
    }

    @Test
    fun `count returns only the local frame without the global sentinel`() {
        val label = localLabel()

        label
            .mutate(listOf(serviceEdge("count_probe", "count-route")), EdgeOperation.INSERT)
            .then(label.count(Metadata.origin, Direction.OUT))
            .toRowFlux()
            .map { it.getLong("COUNT(1)") }
            .collectList()
            .test()
            .assertNext { counts ->
                // Local frame only: the global JdbcHashLabel's -1 sentinel row must not be merged in.
                assertEquals(1, counts.size)
                assertTrue(-1L !in counts)
            }.verifyComplete()
    }
}
