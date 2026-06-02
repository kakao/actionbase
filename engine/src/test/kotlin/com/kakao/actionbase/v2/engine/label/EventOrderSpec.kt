package com.kakao.actionbase.v2.engine.label

import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.edge.TraceEdge
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.core.metadata.DirectionType
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.core.types.DataType
import com.kakao.actionbase.v2.core.types.EdgeSchema
import com.kakao.actionbase.v2.core.types.Field
import com.kakao.actionbase.v2.core.types.VertexField
import com.kakao.actionbase.v2.core.types.VertexType
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.service.ddl.DdlStatus
import com.kakao.actionbase.v2.engine.sql.RowWithSchema
import com.kakao.actionbase.v2.engine.sql.StatKey
import com.kakao.actionbase.v2.engine.sql.show
import com.kakao.actionbase.v2.engine.test.GraphFixtures
import com.kakao.actionbase.v2.engine.test.toRequest

import kotlin.random.Random

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import reactor.core.publisher.Mono
import reactor.kotlin.test.test

class EventOrderSpec :
    StringSpec({

        lateinit var graph: Graph

        lateinit var hbase: Label

        beforeSpec {
            graph = GraphFixtures.create()
            hbase = graph.getLabel(EntityName(GraphFixtures.serviceName, GraphFixtures.hbaseHash))
        }

        afterSpec {
            graph.close()
        }

        fun createEdgeOperationPairs(): List<Pair<EdgeOperation, TraceEdge>> {
            val src = Random.nextLong()
            val tgt = Random.nextLong()
            val insertOp = EdgeOperation.INSERT to Edge(10L, src, tgt, mapOf("createdAt" to 10L, "permission" to "me")).toTraceEdge()
            val updateOp = EdgeOperation.UPDATE to Edge(20L, src, tgt, mapOf("receivedFrom" to "others")).toTraceEdge()
            val deleteOp = EdgeOperation.DELETE to Edge(30L, src, tgt).toTraceEdge()
            return listOf(insertOp, updateOp, deleteOp)
        }

        fun getRow(
            label: Label,
            src: Any,
            tgt: Any,
        ): Mono<RowWithSchema> {
            val df = label.get(src, listOf(tgt), Direction.OUT, setOf(StatKey.WITH_ALL))
            df.show()
            return df.map { it.toRowWithSchema().first() }
        }

        fun testEdgeOperation(
            label: Label,
            op: Pair<EdgeOperation, TraceEdge>,
            expectedStatuses: Map<String, Any?>,
            expectedStatus: EdgeOperationStatus,
        ) {
            label
                .mutate(op.second, op.first)
                .test()
                .assertNext { it.status shouldBe expectedStatus }
                .verifyComplete()

            getRow(label, op.second.src, op.second.tgt)
                .test()
                .assertNext {
                    expectedStatuses.forEach { (key, value) ->
                        it.getOrNull(key) shouldBe value
                    }
                }.verifyComplete()
        }

        "handles nullable props with the events i, u, d" {
            val (i, u, d) = createEdgeOperationPairs()

            testEdgeOperation(
                hbase,
                i,
                mapOf("createdAt" to 10L, "permission" to "me", "receivedFrom" to null),
                EdgeOperationStatus.CREATED,
            )
            testEdgeOperation(
                hbase,
                u,
                mapOf("createdAt" to 10L, "permission" to "me", "receivedFrom" to "others"),
                EdgeOperationStatus.UPDATED,
            )
            testEdgeOperation(
                hbase,
                d,
                mapOf("createdAt" to null, "permission" to null, "receivedFrom" to null),
                EdgeOperationStatus.DELETED,
            )
        }

        "handles nullable props with the events i, d, u" {
            val (i, u, d) = createEdgeOperationPairs()

            testEdgeOperation(
                hbase,
                i,
                mapOf("createdAt" to 10L, "permission" to "me", "receivedFrom" to null),
                EdgeOperationStatus.CREATED,
            )
            testEdgeOperation(
                hbase,
                d,
                mapOf("createdAt" to null, "permission" to null, "receivedFrom" to null),
                EdgeOperationStatus.DELETED,
            )
            testEdgeOperation(
                hbase,
                u,
                mapOf("createdAt" to null, "permission" to null, "receivedFrom" to null),
                EdgeOperationStatus.IDLE,
            )
        }

        "handles nullable props with the events u, i, d" {
            val (i, u, d) = createEdgeOperationPairs()

            testEdgeOperation(
                hbase,
                u,
                mapOf("createdAt" to null, "permission" to null, "receivedFrom" to "others"),
                EdgeOperationStatus.IDLE,
            )
            testEdgeOperation(
                hbase,
                i,
                mapOf("createdAt" to 10L, "permission" to "me", "receivedFrom" to "others"),
                EdgeOperationStatus.CREATED,
            )
            testEdgeOperation(
                hbase,
                d,
                mapOf("createdAt" to null, "permission" to null, "receivedFrom" to null),
                EdgeOperationStatus.DELETED,
            )
        }

        "handles nullable props with the events u, d, i" {
            val (i, u, d) = createEdgeOperationPairs()

            testEdgeOperation(
                hbase,
                u,
                mapOf("createdAt" to null, "permission" to null, "receivedFrom" to "others"),
                EdgeOperationStatus.IDLE,
            )
            testEdgeOperation(
                hbase,
                d,
                mapOf("createdAt" to null, "permission" to null, "receivedFrom" to null),
                EdgeOperationStatus.IDLE,
            )
            testEdgeOperation(
                hbase,
                i,
                mapOf("createdAt" to null, "permission" to null, "receivedFrom" to null),
                EdgeOperationStatus.IDLE,
            )
        }

        "handles nullable props with the events d, i, u" {
            val (i, u, d) = createEdgeOperationPairs()

            testEdgeOperation(
                hbase,
                d,
                mapOf("createdAt" to null, "permission" to null, "receivedFrom" to null),
                EdgeOperationStatus.IDLE,
            )
            testEdgeOperation(
                hbase,
                i,
                mapOf("createdAt" to null, "permission" to null, "receivedFrom" to null),
                EdgeOperationStatus.IDLE,
            )
            testEdgeOperation(
                hbase,
                u,
                mapOf("createdAt" to null, "permission" to null, "receivedFrom" to null),
                EdgeOperationStatus.IDLE,
            )
        }

        "handles nullable props with the events d, u, i" {
            val (i, u, d) = createEdgeOperationPairs()

            testEdgeOperation(
                hbase,
                d,
                mapOf("createdAt" to null, "permission" to null, "receivedFrom" to null),
                EdgeOperationStatus.IDLE,
            )
            testEdgeOperation(
                hbase,
                u,
                mapOf("createdAt" to null, "permission" to null, "receivedFrom" to null),
                EdgeOperationStatus.IDLE,
            )
            testEdgeOperation(
                hbase,
                i,
                mapOf("createdAt" to null, "permission" to null, "receivedFrom" to null),
                EdgeOperationStatus.IDLE,
            )
        }

        "handles nullable props with the events invalid delete then insert" {
            val src = Random.nextLong()
            val tgt = Random.nextLong()

            val d = EdgeOperation.DELETE to Edge(10L, src, tgt).toTraceEdge()
            val i = EdgeOperation.INSERT to Edge(20L, src, tgt, mapOf("createdAt" to 20L, "permission" to "me")).toTraceEdge()

            testEdgeOperation(
                hbase,
                i,
                mapOf("createdAt" to 20L, "permission" to "me", "receivedFrom" to null),
                EdgeOperationStatus.CREATED,
            )

            testEdgeOperation(
                hbase,
                d,
                mapOf("createdAt" to 20L, "permission" to "me", "receivedFrom" to null),
                EdgeOperationStatus.IDLE,
            )
        }

        "handles nullable props with the events" {
            val schema =
                EdgeSchema(
                    VertexField(VertexType.LONG),
                    VertexField(VertexType.LONG),
                    listOf(
                        Field("a", DataType.STRING, true),
                        Field("b", DataType.STRING, true),
                        Field("c", DataType.STRING, true),
                    ),
                )

            val entity =
                LabelEntity(
                    active = true,
                    name = EntityName(GraphFixtures.serviceName, "nullable_tree_property"),
                    desc = "",
                    type = LabelType.HASH,
                    schema = schema,
                    dirType = DirectionType.OUT,
                    storage = GraphFixtures.hbaseStorage,
                )

            graph.labelDdl
                .create(entity.name, entity.toRequest())
                .test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.CREATED
                }.verifyComplete()

            val label = graph.getLabel(entity.name)

            testEdgeOperation(
                label,
                EdgeOperation.UPDATE to Edge(20L, 1L, 2L, mapOf("a" to "a2", "b" to "b2")).toTraceEdge(),
                mapOf("a" to "a2", "b" to "b2", "c" to null),
                EdgeOperationStatus.IDLE,
            )

            testEdgeOperation(
                label,
                EdgeOperation.INSERT to Edge(10L, 1L, 2L, mapOf("a" to "a", "c" to "c")).toTraceEdge(),
                mapOf("a" to "a2", "b" to "b2", "c" to "c"),
                EdgeOperationStatus.CREATED,
            )
        }
    })
