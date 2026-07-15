package com.kakao.actionbase.v2.engine.label.bytearray

import com.kakao.actionbase.engine.datastore.impl.ByteArrayStore
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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import reactor.kotlin.test.test

/**
 * Unit tests for the ByteArrayStore-backed labels, exercised without a Graph: the labels are
 * instantiated directly on a fresh [ByteArrayStore] and driven through the standard mutate/read
 * pipeline. Covers the hash round-trip, delete, degree counter, and the indexed range scan.
 */
class ByteArrayLabelSpec :
    StringSpec({

        val coder = EdgeEncoderFactory().bytesKeyValueEncoder

        val schema =
            EdgeSchema(
                VertexField(VertexType.STRING),
                VertexField(VertexType.STRING),
                listOf(
                    Field("createdAt", DataType.LONG, false),
                ),
            )

        fun hashLabel(store: ByteArrayStore = ByteArrayStore()): ByteArrayHashLabel {
            val entity =
                LabelEntity(
                    active = true,
                    name = EntityName("test", "hash"),
                    desc = "hash label",
                    type = LabelType.HASH,
                    schema = schema,
                    dirType = DirectionType.OUT,
                    storage = "mock",
                )
            return ByteArrayHashLabel(entity, coder, store)
        }

        fun indexedLabel(store: ByteArrayStore = ByteArrayStore()): ByteArrayIndexedLabel {
            val entity =
                LabelEntity(
                    active = true,
                    name = EntityName("test", "indexed"),
                    desc = "indexed label",
                    type = LabelType.INDEXED,
                    schema = schema,
                    dirType = DirectionType.BOTH,
                    storage = "mock",
                    indices = listOf(Index("createdAt_asc", listOf(Index.Field("createdAt", Order.ASC)))),
                )
            return ByteArrayIndexedLabel.create(entity, coder, store)
        }

        fun edge(
            src: String,
            tgt: String,
            createdAt: Long,
        ) = Edge(createdAt, src, tgt, mapOf("createdAt" to createdAt)).toTraceEdge()

        "hash: insert round-trips through get" {
            val label = hashLabel()

            label
                .mutate(listOf(edge("u1", "v1", 100L)), EdgeOperation.INSERT)
                .then(label.get("u1", listOf("v1"), Direction.OUT, emptySet()))
                .toRowFlux()
                .map { it["tgt"] }
                .collectList()
                .test()
                .assertNext { it shouldBe listOf("v1") }
                .verifyComplete()
        }

        "hash: delete deactivates the edge" {
            val label = hashLabel()

            label
                .mutate(listOf(edge("u1", "v1", 100L)), EdgeOperation.INSERT)
                .then(label.mutate(listOf(edge("u1", "v1", 200L)), EdgeOperation.DELETE))
                .then(label.get("u1", listOf("v1"), Direction.OUT, emptySet()))
                .toRowFlux()
                .map { it["tgt"] }
                .collectList()
                .test()
                .assertNext { it shouldBe emptyList() }
                .verifyComplete()
        }

        "hash: out-degree counter reflects inserts" {
            val label = hashLabel()

            label
                .mutate(listOf(edge("u1", "v1", 100L), edge("u1", "v2", 110L)), EdgeOperation.INSERT)
                .then(label.count("u1", Direction.OUT))
                .toRowFlux()
                .map { it.getLong("COUNT(1)") }
                .collectList()
                .test()
                .assertNext { it shouldBe listOf(2L) }
                .verifyComplete()
        }

        "indexed: inserts are readable through the index scan" {
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
                .assertNext { it shouldBe listOf("v1", "v2") }
                .verifyComplete()
        }
    })
