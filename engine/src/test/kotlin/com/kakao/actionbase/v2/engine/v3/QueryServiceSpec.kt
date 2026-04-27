package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.core.edge.payload.EdgePayload
import com.kakao.actionbase.engine.service.QueryService
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
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.service.ddl.DdlStatus
import com.kakao.actionbase.v2.engine.test.GraphFixtures
import com.kakao.actionbase.v2.engine.test.toRequest

import org.junit.jupiter.api.assertThrows

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import reactor.kotlin.test.test

class QueryServiceSpec :
    StringSpec({

        lateinit var graph: Graph
        lateinit var queryService: QueryService

        beforeTest {
            graph = GraphFixtures.create()
            queryService = QueryService(V2BackedEngine(graph))
        }

        afterTest {
            graph.close()
        }

        "count" {
            val database = GraphFixtures.serviceName
            val table = GraphFixtures.hbaseIndexed
            val sampleEdge = GraphFixtures.sampleEdges.first()
            val expectedCount = GraphFixtures.sampleEdges.count { it.src == sampleEdge.src }.toLong()
            queryService
                .count(database, table, sampleEdge.src, Direction.OUT)
                .test()
                .assertNext {
                    it.count shouldBe expectedCount
                }.verifyComplete()
        }

        "get" {
            val database = GraphFixtures.serviceName
            val table = GraphFixtures.hbaseIndexed
            val sampleEdge = GraphFixtures.sampleEdges.first()
            val expectedEdgePayload =
                EdgePayload(
                    version = sampleEdge.ts,
                    source = sampleEdge.src,
                    target = sampleEdge.tgt,
                    properties = (mapOf("receivedFrom" to null) + sampleEdge.props),
                    context = emptyMap(),
                )
            queryService
                .gets(database, table, listOf(sampleEdge.src), listOf(sampleEdge.tgt))
                .test()
                .assertNext {
                    it.edges.map { edge -> edge.toStringValues() } shouldBe listOf(expectedEdgePayload.toStringValues())
                    it.count shouldBe 1
                    it.total shouldBe 1L
                }.verifyComplete()
        }

        "scan" {
            val database = GraphFixtures.serviceName
            val table = GraphFixtures.hbaseIndexed
            val index = GraphFixtures.index2 // created_at_desc
            val sampleEdge = GraphFixtures.sampleEdges.first()
            val expectedCount = GraphFixtures.sampleEdges.count { it.src == sampleEdge.src }
            val expectedEdges =
                GraphFixtures.sampleEdges
                    .filter { it.src == sampleEdge.src }
                    .map { EdgePayload(it.ts, it.src, it.tgt, mapOf("receivedFrom" to null) + it.props, emptyMap()) }
                    .sortedByDescending { it.properties["createdAt"].toString().toLong() }

            queryService
                .scan(database, table, index, sampleEdge.src, Direction.OUT, limit = 10)
                .test()
                .assertNext {
                    it.edges.size shouldBe expectedCount
                    it.count shouldBe expectedCount
                    it.edges.map { edge -> edge.toStringValues() } shouldBe expectedEdges.map { edge -> edge.toStringValues() }
                    it.total shouldBe -1L // total is not provided in this case
                }.verifyComplete()
        }

        "scan with offset" {
            val firstStepLimit = 2
            val database = GraphFixtures.serviceName
            val table = GraphFixtures.hbaseIndexed
            val index = GraphFixtures.index2 // created_at_desc
            val sampleEdge = GraphFixtures.sampleEdges.first()

            val expectedCount = GraphFixtures.sampleEdges.count { it.src == sampleEdge.src } - firstStepLimit
            val expectedEdges =
                GraphFixtures.sampleEdges
                    .filter { it.src == sampleEdge.src }
                    .map { EdgePayload(it.ts, it.src, it.tgt, mapOf("receivedFrom" to null) + it.props, emptyMap()) }
                    .sortedByDescending { it.properties["createdAt"].toString().toLong() }
                    .drop(firstStepLimit)

            queryService
                .scan(database, table, index, sampleEdge.src, Direction.OUT, limit = firstStepLimit)
                .flatMap {
                    val offset = it.offset
                    queryService.scan(database, table, index, sampleEdge.src, Direction.OUT, offset = offset, limit = 10)
                }.test()
                .assertNext {
                    it.edges.size shouldBe expectedCount
                    it.count shouldBe expectedCount
                    it.edges.map { edge -> edge.toStringValues() } shouldBe expectedEdges.map { edge -> edge.toStringValues() }
                    it.total shouldBe -1L // total is not provided in this case
                }.verifyComplete()
        }

        "scan with features=total" {
            val database = GraphFixtures.serviceName
            val table = GraphFixtures.hbaseIndexed
            val index = GraphFixtures.index2 // created_at_desc
            val sampleEdge = GraphFixtures.sampleEdges.first()
            val expectedCount = GraphFixtures.sampleEdges.count { it.src == sampleEdge.src }
            val expectedEdges =
                GraphFixtures.sampleEdges
                    .filter { it.src == sampleEdge.src }
                    .map { EdgePayload(it.ts, it.src, it.tgt, mapOf("receivedFrom" to null) + it.props, emptyMap()) }
                    .sortedByDescending { it.properties["createdAt"].toString().toLong() }

            queryService
                .scan(database, table, index, sampleEdge.src, Direction.OUT, limit = 10, features = listOf("total"))
                .test()
                .assertNext {
                    it.edges.size shouldBe expectedCount
                    it.count shouldBe expectedCount
                    it.edges.map { edge -> edge.toStringValues() } shouldBe expectedEdges.map { edge -> edge.toStringValues() }
                    it.total shouldBe expectedCount
                }.verifyComplete()
        }

        "scan with valid ranges" {
            val database = GraphFixtures.serviceName
            val table = GraphFixtures.hbaseIndexed
            val index = GraphFixtures.index1 // permission_created_at_desc
            val sampleEdge = GraphFixtures.sampleEdges.first()
            val permission = sampleEdge.props["permission"].toString()
            val createdAt = (sampleEdge.props["createdAt"] as Number).toLong()
            val expectedEdges =
                GraphFixtures.sampleEdges
                    .filter { it.src == sampleEdge.src && it.props["permission"] == permission && (it.props["createdAt"] as Number).toLong() > createdAt }
                    .map { EdgePayload(it.ts, it.src, it.tgt, mapOf("receivedFrom" to null) + it.props, emptyMap()) }
                    .sortedByDescending { it.properties["createdAt"].toString().toLong() }
            val expectedCount = expectedEdges.size

            // valid ranges: none, (permission), (permission, createdAt)
            queryService
                .scan(database, table, index, sampleEdge.src, Direction.OUT, limit = 10, ranges = "permission:eq:na;createdAt:gt:10")
                .test()
                .assertNext {
                    it.edges.size shouldBe expectedCount
                    it.count shouldBe expectedCount
                    it.edges.map { edge -> edge.toStringValues() } shouldBe expectedEdges.map { edge -> edge.toStringValues() }
                    it.total shouldBe -1L // total is not provided in this case
                }.verifyComplete()
        }

        // region V3 system property name collision (e2e)

        // A user-defined property field whose name collides with a V3 system field
        // (`version` / `source` / `target` / `direction`) is escaped with backticks at the
        // V2 -> V3 conversion boundary, so it cannot shadow the actual edge metadata.
        // (Multi-hop usage on such tables is not supported -- see operational guide.)
        val collisionTable = "system_prop_collision"
        val collisionIndex = "created_at_desc"
        val collisionEdge =
            Edge(
                100L,
                1L,
                2L,
                mapOf(
                    "createdAt" to 50L,
                    "version" to 999L,
                    "source" to "userSrcValue",
                    "target" to "userTgtValue",
                ),
            )

        fun setupCollisionLabel() {
            val schema =
                EdgeSchema(
                    VertexField(VertexType.LONG),
                    VertexField(VertexType.LONG),
                    listOf(
                        Field("createdAt", DataType.LONG, false),
                        Field("version", DataType.LONG, true),
                        Field("source", DataType.STRING, true),
                        Field("target", DataType.STRING, true),
                    ),
                )
            val name = EntityName(GraphFixtures.serviceName, collisionTable)
            val entity =
                LabelEntity(
                    active = true,
                    name = name,
                    desc = "v3 system property collision",
                    type = LabelType.INDEXED,
                    schema = schema,
                    dirType = DirectionType.BOTH,
                    storage = GraphFixtures.datastoreStorage,
                    indices = listOf(Index(collisionIndex, listOf(Index.Field("createdAt", Order.DESC)))),
                )
            graph.labelDdl
                .create(name, entity.toRequest())
                .test()
                .assertNext { it.status shouldBe DdlStatus.Status.CREATED }
                .verifyComplete()
            graph.updateAllMetadata().test().verifyComplete()
            val label = graph.getLabel(name)
            graph
                .mutate(label.name, label, listOf(collisionEdge.toTraceEdge()), EdgeOperation.INSERT)
                .test()
                .assertNext { }
                .verifyComplete()
        }

        "get: colliding user property fields surface in properties without leaking backticks" {
            setupCollisionLabel()
            queryService
                .gets(GraphFixtures.serviceName, collisionTable, listOf(collisionEdge.src), listOf(collisionEdge.tgt))
                .test()
                .assertNext {
                    it.edges.size shouldBe 1
                    val payload = it.edges.first()
                    payload.version shouldBe collisionEdge.ts
                    payload.source.toString() shouldBe collisionEdge.src.toString()
                    payload.target.toString() shouldBe collisionEdge.tgt.toString()
                    payload.properties["version"] shouldBe 999L
                    payload.properties["source"] shouldBe "userSrcValue"
                    payload.properties["target"] shouldBe "userTgtValue"
                    payload.properties["createdAt"] shouldBe 50L
                }.verifyComplete()
        }

        "count: a label with colliding user property names still counts correctly" {
            setupCollisionLabel()
            queryService
                .count(GraphFixtures.serviceName, collisionTable, collisionEdge.src, Direction.OUT)
                .test()
                .assertNext { it.count shouldBe 1L }
                .verifyComplete()
        }

        "scan: colliding user property fields surface in properties without leaking backticks" {
            setupCollisionLabel()
            queryService
                .scan(GraphFixtures.serviceName, collisionTable, collisionIndex, collisionEdge.src, Direction.OUT, limit = 10)
                .test()
                .assertNext {
                    it.edges.size shouldBe 1
                    val payload = it.edges.first()
                    payload.version shouldBe collisionEdge.ts
                    payload.source.toString() shouldBe collisionEdge.src.toString()
                    payload.target.toString() shouldBe collisionEdge.tgt.toString()
                    payload.properties["version"] shouldBe 999L
                    payload.properties["source"] shouldBe "userSrcValue"
                    payload.properties["target"] shouldBe "userTgtValue"
                    payload.properties["createdAt"] shouldBe 50L
                }.verifyComplete()
        }

        // endregion

        "scan with invalid ranges" {
            val database = GraphFixtures.serviceName
            val table = GraphFixtures.hbaseIndexed
            val index = GraphFixtures.index1 // permission_created_at_desc
            val sampleEdge = GraphFixtures.sampleEdges.first()
            val permission = sampleEdge.props["permission"].toString()
            val createdAt = (sampleEdge.props["createdAt"] as Number).toLong()
            val expectedEdges =
                GraphFixtures.sampleEdges
                    .filter { it.src == sampleEdge.src && it.props["permission"] == permission && (it.props["createdAt"] as Number).toLong() > createdAt }
                    .map { EdgePayload(it.ts, it.src, it.tgt, mapOf("receivedFrom" to null) + it.props, emptyMap()) }
                    .sortedByDescending { it.properties["createdAt"].toString().toLong() }
            expectedEdges.size

            // valid ranges: none, (permission), (permission, createdAt)
            assertThrows<IllegalArgumentException> {
                queryService
                    .scan(database, table, index, sampleEdge.src, Direction.OUT, limit = 10, ranges = "createdAt:gt:10")
                    .subscribe()
            }
        }
    }) {
    companion object {
        fun EdgePayload.toStringValues(): EdgePayload =
            this.copy(
                version = this.version,
                source = this.source.toString(),
                target = this.target.toString(),
                properties = this.properties.mapValues { it.value?.toString() },
            )
    }
}
