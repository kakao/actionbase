package com.kakao.actionbase.v2.engine.label

import com.kakao.actionbase.v2.core.code.EdgeEncoderFactory
import com.kakao.actionbase.v2.core.code.Index
import com.kakao.actionbase.v2.core.code.hbase.Order
import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.core.metadata.DirectionType
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.core.types.DataType
import com.kakao.actionbase.v2.core.types.EdgeSchema
import com.kakao.actionbase.v2.core.types.Field
import com.kakao.actionbase.v2.core.types.VertexField
import com.kakao.actionbase.v2.core.types.VertexType
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.sql.ScanFilter
import com.kakao.actionbase.v2.engine.sql.StatKey
import com.kakao.actionbase.v2.engine.sql.toRowFlux

import kotlin.test.assertEquals

import org.junit.jupiter.api.Test

import reactor.kotlin.test.test

/**
 * Backend-agnostic compatibility suite for [Label] implementations. Every backend twin
 * (ByteArrayStore, HBase, ...) is exercised through the standard mutate/read pipeline and must
 * reproduce identical semantics: the hash round-trip, delete, degree counter, and indexed range
 * scan. Subclasses provide backend-specific labels via [hashLabel]/[indexedLabel].
 */
abstract class AbstractLabelCompatibilityTest {
    protected val coder = EdgeEncoderFactory().bytesKeyValueEncoder

    protected val schema =
        EdgeSchema(
            VertexField(VertexType.STRING),
            VertexField(VertexType.STRING),
            listOf(
                Field("createdAt", DataType.LONG, false),
            ),
        )

    protected val indices =
        listOf(Index("createdAt_asc", listOf(Index.Field("createdAt", Order.ASC))))

    protected val hashEntity =
        LabelEntity(
            active = true,
            name = EntityName("test", "hash"),
            desc = "hash label",
            type = LabelType.HASH,
            schema = schema,
            dirType = DirectionType.OUT,
            storage = "mock",
        )

    protected val indexedEntity =
        LabelEntity(
            active = true,
            name = EntityName("test", "indexed"),
            desc = "indexed label",
            type = LabelType.INDEXED,
            schema = schema,
            dirType = DirectionType.BOTH,
            storage = "mock",
            indices = indices,
        )

    protected abstract fun hashLabel(): AbstractLabel<*>

    protected abstract fun indexedLabel(): AbstractLabel<*>

    private fun edge(
        src: String,
        tgt: String,
        createdAt: Long,
    ) = Edge(createdAt, src, tgt, mapOf("createdAt" to createdAt)).toTraceEdge()

    @Test
    fun `hash - insert round-trips through get`() {
        val label = hashLabel()

        label
            .mutate(listOf(edge("u1", "v1", 100L)), EdgeOperation.INSERT)
            .then(label.get("u1", listOf("v1"), Direction.OUT, emptySet()))
            .toRowFlux()
            .map { it["tgt"] }
            .collectList()
            .test()
            .assertNext { assertEquals(listOf("v1"), it) }
            .verifyComplete()
    }

    @Test
    fun `hash - delete deactivates the edge`() {
        val label = hashLabel()

        label
            .mutate(listOf(edge("u1", "v1", 100L)), EdgeOperation.INSERT)
            .then(label.mutate(listOf(edge("u1", "v1", 200L)), EdgeOperation.DELETE))
            .then(label.get("u1", listOf("v1"), Direction.OUT, emptySet()))
            .toRowFlux()
            .map { it["tgt"] }
            .collectList()
            .test()
            .assertNext { assertEquals(emptyList<Any?>(), it) }
            .verifyComplete()
    }

    @Test
    fun `hash - out-degree counter reflects inserts`() {
        val label = hashLabel()

        label
            .mutate(listOf(edge("u1", "v1", 100L), edge("u1", "v2", 110L)), EdgeOperation.INSERT)
            .then(label.count("u1", Direction.OUT))
            .toRowFlux()
            .map { it.getLong("COUNT(1)") }
            .collectList()
            .test()
            .assertNext { assertEquals(listOf(2L), it) }
            .verifyComplete()
    }

    @Test
    fun `indexed - inserts are readable through the index scan`() {
        val label = indexedLabel()
        val scanFilter =
            ScanFilter(
                name = label.name,
                srcSet = setOf("u1"),
                dir = Direction.OUT,
                indexName = "createdAt_asc",
                limit = 100,
            )

        label
            .mutate(listOf(edge("u1", "v1", 100L), edge("u1", "v2", 200L)), EdgeOperation.INSERT)
            .then(label.scan(scanFilter, emptySet<StatKey>()))
            .toRowFlux()
            .map { it["tgt"] }
            .collectList()
            .test()
            .assertNext { assertEquals(listOf("v1", "v2"), it) }
            .verifyComplete()
    }

    @Test
    fun `indexed - offset pagination resumes strictly after the cursor`() {
        val label = indexedLabel()

        label
            .mutate(
                listOf(
                    edge("u1", "v1", 100L),
                    edge("u1", "v2", 200L),
                    edge("u1", "v3", 300L),
                    edge("u1", "v4", 400L),
                ),
                EdgeOperation.INSERT,
            ).block()

        fun page(offset: String?) =
            ScanFilter(
                name = label.name,
                srcSet = setOf("u1"),
                dir = Direction.OUT,
                indexName = "createdAt_asc",
                limit = 2,
                offset = offset,
            )

        val df1 = label.scan(page(null), emptySet<StatKey>()).block()!!
        val page1 = df1.toRowWithSchema().map { it["tgt"] }
        val cursor = df1.offsets.singleOrNull()

        val page2 =
            label
                .scan(page(cursor), emptySet<StatKey>())
                .block()!!
                .toRowWithSchema()
                .map { it["tgt"] }

        // The scan start is exclusive: page 2 resumes strictly after page 1's last row, so the
        // boundary row is neither duplicated nor skipped. This contract is shared by every backend.
        assertEquals(listOf("v1", "v2"), page1)
        assertEquals(listOf("v3", "v4"), page2)
        assertEquals(listOf("v1", "v2", "v3", "v4"), page1 + page2)
    }
}
