package com.kakao.actionbase.v2.engine

import com.kakao.actionbase.v2.core.code.Index
import com.kakao.actionbase.v2.core.code.hbase.Order
import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.metadata.Active
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
import com.kakao.actionbase.v2.engine.label.EdgeOperationStatus
import com.kakao.actionbase.v2.engine.label.Label
import com.kakao.actionbase.v2.engine.service.ddl.DdlStatus.Status
import com.kakao.actionbase.v2.engine.sql.Row
import com.kakao.actionbase.v2.engine.sql.StatKey
import com.kakao.actionbase.v2.engine.sql.StatLong
import com.kakao.actionbase.v2.engine.sql.show
import com.kakao.actionbase.v2.engine.sql.toRowFlux
import com.kakao.actionbase.v2.engine.test.GraphFixtures
import com.kakao.actionbase.v2.engine.test.toRequest

import kotlin.random.Random

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import reactor.kotlin.test.test

class IssueSpec :
    StringSpec({

        val labelName = EntityName(GraphFixtures.serviceName, GraphFixtures.hbaseIndexed)

        lateinit var graph: Graph
        lateinit var label: Label

        beforeTest {
            graph = GraphFixtures.create()
            label = graph.getLabel(labelName)
        }

        afterTest {
            graph.close()
        }

        "ISSUE-1663: dir must be referenced when processing stats=src_degree" {
            val edge = GraphFixtures.sampleEdges.first()
            val src = edge.src
            val tgt = edge.tgt
            val expectedSrcDegree = GraphFixtures.sampleEdges.count { it.src == src }.toLong()
            val expectedTgtDegree = GraphFixtures.sampleEdges.count { it.tgt == tgt }.toLong()
            graph.queryScan(labelName, src, Direction.OUT, "created_at_desc").show()
            graph
                .queryScan(labelName, src, Direction.OUT, "created_at_desc", setOf(StatKey.SRC_DEGREE))
                .map { it.stats }
                .test()
                .assertNext {
                    it[0] shouldBe StatLong(StatKey.SRC_DEGREE, expectedSrcDegree)
                }.verifyComplete()

            graph
                .queryScan(labelName, tgt, Direction.IN, "created_at_desc", setOf(StatKey.SRC_DEGREE))
                .map { it.stats }
                .test()
                .assertNext {
                    it[0] shouldBe StatLong(StatKey.SRC_DEGREE, expectedTgtDegree)
                }.verifyComplete()
        }

        "ISSUE-1667: Issue where ts changes when in IDLE state" {
            val edge = GraphFixtures.sampleEdges.first()
            val src = edge.src
            val tgt = edge.tgt
            graph
                .queryGet(labelName, src, tgt)
                .toRowFlux()
                .test()
                .assertNext {
                    it["ts"] shouldBe edge.ts
                    it["src"] shouldBe edge.src
                    it["tgt"] shouldBe edge.tgt
                    it["permission"] shouldBe edge.props["permission"]
                }

            label
                .mutate(
                    Edge(edge.ts - 1, edge.src, edge.tgt, edge.props).toTraceEdge(),
                    EdgeOperation.UPDATE,
                ).test()
                .assertNext {
                    it.status shouldBe EdgeOperationStatus.IDLE
                }

            graph
                .queryGet(label.entity.name, src, tgt)
                .toRowFlux()
                .test()
                .assertNext {
                    it["ts"] shouldBe edge.ts
                    it["src"] shouldBe edge.src
                    it["tgt"] shouldBe edge.tgt
                    it["permission"] shouldBe edge.props["permission"]
                }

            label
                .mutate(
                    Edge(edge.ts + 1, edge.src, edge.tgt, edge.props).toTraceEdge(),
                    EdgeOperation.UPDATE,
                ).test()
                .assertNext {
                    it.status shouldBe EdgeOperationStatus.UPDATED
                }

            graph
                .queryGet(label.entity.name, 123, 10000)
                .toRowFlux()
                .test()
                .assertNext {
                    it["ts"] shouldBe edge.ts + 1
                    it["src"] shouldBe edge.src
                    it["tgt"] shouldBe edge.tgt
                    it["permission"] shouldBe edge.props["permission"]
                }
        }

        "ISSUE-1839: Handling situation where data has changed but responds with IDLE" {
            label
                .mutate(
                    Edge(10, 900, 9000, mapOf("permission" to "me", "createdAt" to 10)).toTraceEdge(),
                    EdgeOperation.INSERT,
                ).test()
                .assertNext {
                    it.after?.props shouldBe mapOf("permission" to "me", "createdAt" to 10, "receivedFrom" to null)
                    it.status shouldBe EdgeOperationStatus.CREATED
                }.verifyComplete()

            label
                .mutate(
                    Edge(20, 900, 9000, mapOf("receivedFrom" to "others")).toTraceEdge(),
                    EdgeOperation.INSERT,
                ).test()
                .assertNext {
                    it.after?.props shouldBe mapOf("permission" to "me", "createdAt" to 10, "receivedFrom" to "others")
                    it.status shouldBe EdgeOperationStatus.UPDATED
                }.verifyComplete()

            label
                .mutate(
                    Edge(15, 900, 9000, mapOf("permission" to "others")).toTraceEdge(),
                    EdgeOperation.INSERT,
                ).test()
                .assertNext {
                    it.after?.props shouldBe mapOf("permission" to "others", "createdAt" to 10, "receivedFrom" to "others")
                    it.status shouldBe EdgeOperationStatus.UPDATED
                }.verifyComplete()

            label
                .mutate(
                    Edge(20, 900, 9000, mapOf("receivedFrom" to "others")).toTraceEdge(),
                    EdgeOperation.INSERT,
                ).test()
                .assertNext {
                    it.after?.props shouldBe mapOf("permission" to "others", "createdAt" to 10, "receivedFrom" to "others")
                    it.status shouldBe EdgeOperationStatus.IDLE
                }.verifyComplete()
        }

        "ISSUE-2038: Issue where active=true does not work when insert occurs after update" {
            val tgt = Random.nextLong()
            label
                .mutate(
                    Edge(20, 900, tgt, mapOf("permission" to "me")).toTraceEdge(),
                    EdgeOperation.UPDATE,
                ).test()
                .assertNext {
                    it.after?.active shouldBe Active.INACTIVE
                    it.after?.props shouldBe mapOf("permission" to "me")
                    it.status shouldBe EdgeOperationStatus.IDLE
                }.verifyComplete()

            label
                .mutate(
                    Edge(10, 900, tgt, mapOf("receivedFrom" to "others", "createdAt" to 10)).toTraceEdge(),
                    EdgeOperation.INSERT,
                ).test()
                .assertNext {
                    it.after?.active shouldBe Active.ACTIVE
                    it.after?.props shouldBe mapOf("permission" to "me", "createdAt" to 10, "receivedFrom" to "others")
                    it.status shouldBe EdgeOperationStatus.CREATED
                }.verifyComplete()
        }

        @Suppress("VariableNaming")
        fun `ISSUE-2071`(
            processingOrder: List<String>,
            expectedActive: Boolean,
        ) {
            val src = Random.nextLong()
            val tgt = Random.nextLong()
            println(processingOrder)

            val i1 = Edge(10, src, tgt, mapOf("permission" to "me", "createdAt" to 10, "receivedFrom" to "not_received")).toTraceEdge() to EdgeOperation.INSERT
            val d2 = Edge(20, src, tgt).toTraceEdge() to EdgeOperation.DELETE
            val i3 = Edge(30, src, tgt, mapOf("permission" to "me", "createdAt" to 10, "receivedFrom" to "not_received")).toTraceEdge() to EdgeOperation.INSERT
            val u4 = Edge(40, src, tgt, mapOf("receivedFrom" to "others")).toTraceEdge() to EdgeOperation.UPDATE

            val data = mapOf("I1" to i1, "D2" to d2, "I3" to i3, "U4" to u4)

            processingOrder.forEach {
                val (edge, operation) = data[it]!!
                label
                    .mutate(edge, operation)
                    .test()
                    .expectNextCount(1)
                    .verifyComplete()
            }

            graph
                .queryGet(label.entity.name, src, tgt, setOf(StatKey.WITH_ALL))
                .toRowFlux()
                .test()
                .assertNext {
                    it["active"] shouldBe expectedActive
                }.verifyComplete()
        }

        "ISSUE-2071: Process by processing order" {
            `ISSUE-2071`(listOf("I1", "D2", "U4"), false)

            // before: ACTIVE -> ACTIVE, after: ACTIVE -> INACTIVE
            `ISSUE-2071`(listOf("I1", "U4", "D2"), false)

            `ISSUE-2071`(listOf("D2", "I1", "U4"), false)

            // before: INACTIVE-> ACTIVE, after: INACTIVE -> INACTIVE
            `ISSUE-2071`(listOf("D2", "U4", "I1"), false)

            // before: ACTIVE -> ACTIVE, after: ACTIVE -> INACTIVE
            `ISSUE-2071`(listOf("U4", "I1", "D2"), false)

            // before: INACTIVE -> ACTIVE, after: INACTIVE -> INACTIVE
            `ISSUE-2071`(listOf("U4", "D2", "I1"), false)
        }

        "ISSUE-3233: Data result inconsistency according to processing order" {
            val entity =
                LabelEntity(
                    active = true,
                    name = EntityName("test", "kcdl-3233"),
                    desc = "kcdl-3233 label",
                    type = LabelType.INDEXED,
                    schema =
                        EdgeSchema(
                            VertexField(VertexType.LONG),
                            VertexField(VertexType.LONG),
                            listOf(
                                Field("name", DataType.STRING, false),
                                Field("comment", DataType.STRING, true),
                            ),
                        ),
                    dirType = DirectionType.OUT,
                    storage = GraphFixtures.hbaseStorage,
                    indices = listOf(Index("updated_at_desc", listOf(Index.Field("ts", Order.DESC)))),
                )

            graph.labelDdl
                .create(entity.name, entity.toRequest())
                .test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe Status.CREATED
                }.verifyComplete()

            val thisLabel = graph.getLabel(entity.name)

            val u1 = EdgeOperation.UPDATE to Edge(1, 100, 200, mapOf("comment" to "c"))
            val i2 = EdgeOperation.INSERT to Edge(2, 100, 200, mapOf("name" to "n"))

            val results = mutableMapOf<String, Row>()
            val processingSequences =
                listOf(
                    listOf(i2, u1),
                    listOf(u1, i2),
                )
            processingSequences
                .forEach { processingSequence ->
                    val src = Random.nextLong()
                    val tgt = Random.nextLong()
                    processingSequence.forEach { (op, base) ->
                        val edge = Edge(base.ts, src, tgt, base.props).toTraceEdge()
                        thisLabel
                            .mutate(edge, op)
                            .test()
                            .expectNextCount(1)
                            .verifyComplete()
                    }
                    val sequenceKey = processingSequence.joinToString("; ") { "${it.first.name.first()}${it.second.ts}" }
                    val row =
                        thisLabel
                            .get(listOf(src), listOf(tgt), emptySet())
                            .map { it.rows.first() }
                            .block()!!
                    results[sequenceKey] = row
                }

            results.forEach { (sequenceKey, row) ->
                println("Sequence: $sequenceKey, Result: ${row.array.takeLast(2)}")
            }
            /**
             * Sequence: I2; U1, Result: [n, null] <-- Correct answer
             * Sequence: U1; I2, Result: [n, c] <-- Incorrect result
             */
        }
    })
