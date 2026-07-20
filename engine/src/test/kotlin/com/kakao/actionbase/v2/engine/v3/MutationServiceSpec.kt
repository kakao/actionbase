package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.core.Constants.VERTEX_MARKER
import com.kakao.actionbase.core.edge.payload.DataFrameEdgePayload
import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest
import com.kakao.actionbase.core.edge.payload.EdgeMutationResponse
import com.kakao.actionbase.core.metadata.features.FeatureFlags
import com.kakao.actionbase.core.metadata.features.MutationFeature
import com.kakao.actionbase.core.vertex.payload.VertexBulkMutationRequest
import com.kakao.actionbase.core.vertex.payload.VertexMutationResponse
import com.kakao.actionbase.engine.service.MutationService
import com.kakao.actionbase.engine.service.QueryService
import com.kakao.actionbase.engine.util.runEvenIfCancelled
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.metadata.Metadata
import com.kakao.actionbase.v2.engine.service.ddl.LabelCreateRequest
import com.kakao.actionbase.v2.engine.test.GraphFixtures

import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import reactor.core.publisher.Mono
import reactor.kotlin.test.test

class MutationServiceSpec :
    StringSpec({

        lateinit var graph: Graph
        lateinit var mutationService: MutationService
        lateinit var mergeMutationService: MutationService
        lateinit var queryService: QueryService

        beforeTest {
            graph = GraphFixtures.create()
            val engine = V2BackedEngine(graph)
            mutationService = MutationService(engine)
            mergeMutationService =
                MutationService(
                    engine,
                    FeatureFlags(listOf(FeatureFlags.Item(MutationFeature.INSERT_MERGE, setOf(GraphFixtures.serviceName)))),
                )
            queryService = QueryService(engine)
        }

        afterTest {
            graph.close()
        }

        "mutation" {
            val database = GraphFixtures.serviceName
            val table = GraphFixtures.hbaseIndexed
            val insertRequest =
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 10, "source": "1000", "target": "9000", "properties": {"permission": "na", "createdAt": 10}}},
                    {"type": "INSERT", "edge": {"version": 20, "source": "1000", "target": "9001", "properties": {"permission": "na", "createdAt": 20}}},
                    {"type": "INSERT", "edge": {"version": 30, "source": "1001", "target": "9000", "properties": {"permission": "na", "createdAt": 30}}},
                    {"type": "INSERT", "edge": {"version": 40, "source": "1001", "target": "9001", "properties": {"permission": "na", "createdAt": 40}}}
                  ]
                }
                """.trimIndent().toEdgeBulkMutationRequest()

            mutationService
                .mutate(database, table, insertRequest.mutations)
                .map { EdgeMutationResponse.from(it) }
                .test()
                .assertNext { actualObject ->
                    val expected =
                        """
                        {
                          "results": [
                            {"status": "CREATED", "source": 1000, "target": 9000, "count": 1},
                            {"status": "CREATED", "source": 1000, "target": 9001, "count": 1},
                            {"status": "CREATED", "source": 1001, "target": 9000, "count": 1},
                            {"status": "CREATED", "source": 1001, "target": 9001, "count": 1}
                          ]
                        }
                        """.trimIndent().toEdgeMutationResponse().toNormalizedString()
                    actualObject.toNormalizedString() shouldBe expected
                }.verifyComplete()

            queryService
                .gets(database, table, listOf("1000"), listOf("9000"))
                .test()
                .assertNext { actualObject ->
                    val expected =
                        """
                        {
                          "edges": [
                            {"version": 10, "source": 1000, "target": 9000, "properties": {"createdAt": 10, "permission": "na", "receivedFrom": null}, "context": {}}
                          ],
                          "count": 1,
                          "total": 1,
                          "offset": null,
                          "hasNext": false,
                          "context": {}
                        }
                        """.trimIndent().toDataFrameEdgePayload().toNormalizedString()
                    actualObject.toNormalizedString() shouldBe expected
                }.verifyComplete()

            queryService
                .scan(database, table, GraphFixtures.index2, "1000", Direction.OUT, limit = 10)
                .test()
                .assertNext { actualObject ->
                    val expected =
                        """
                        {
                          "edges": [
                            {"version": 20, "source": 1000, "target": 9001, "properties": {"createdAt": 20, "permission": "na", "receivedFrom": null}, "context": {}},
                            {"version": 10, "source": 1000, "target": 9000, "properties": {"createdAt": 10, "permission": "na", "receivedFrom": null}, "context": {}}
                          ],
                          "count": 2,
                          "total": -1,
                          "offset": null,
                          "hasNext": false,
                          "context": {}
                        }
                        """.trimIndent().toDataFrameEdgePayload().toNormalizedString()
                    actualObject.toNormalizedString() shouldBe expected
                }.verifyComplete()

            queryService
                .count(database, table, "1000", Direction.OUT)
                .test()
                .assertNext {
                    it.start shouldBe 1000L
                    it.count shouldBe 2L
                }.verifyComplete()

            queryService
                .scan(database, table, GraphFixtures.index1, "1000", Direction.OUT, limit = 10, ranges = "permission:eq:me")
                .test()
                .assertNext { actualObject ->
                    val expected =
                        """
                        {"edges": [], "count": 0, "total": -1, "offset": null, "hasNext": false, "context": {}}
                        """.trimIndent().toDataFrameEdgePayload().toNormalizedString()
                    actualObject.toNormalizedString() shouldBe expected
                }.verifyComplete()

            val insertRequest2 =
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 50, "source": "1002", "target": "9000", "properties": {"permission": "na", "createdAt": 30}}},
                    {"type": "UPDATE", "edge": {"version": 60, "source": "1000", "target": "9000", "properties": {"permission": "me"}}},
                    {"type": "DELETE", "edge": {"version": 70, "source": "1000", "target": "9001"}},
                    {"type": "INSERT", "edge": {"version": 80, "source": "1000", "target": "9001", "properties": {"permission": "others", "createdAt": 80}}},
                    {"type": "UPDATE", "edge": {"version": 90, "source": "1000", "target": "9001", "properties": {"receivedFrom": "others"}}}
                  ]
                }
                """.trimIndent().toEdgeBulkMutationRequest()

            mutationService
                .mutate(database, table, insertRequest2.mutations)
                .map { EdgeMutationResponse.from(it) }
                .test()
                .assertNext { actualObject ->
                    val expected =
                        """
                        {
                          "results": [
                            {"status": "UPDATED", "source": 1000, "target": 9000, "count": 1},
                            {"status": "UPDATED", "source": 1000, "target": 9001, "count": 3},
                            {"status": "CREATED", "source": 1002, "target": 9000, "count": 1}
                          ]
                        }
                        """.trimIndent().toEdgeMutationResponse().toNormalizedString()
                    actualObject.toNormalizedString() shouldBe expected
                }.verifyComplete()

            queryService
                .gets(database, table, listOf("1000"), listOf("9000"))
                .test()
                .assertNext { actualObject ->
                    val expected =
                        """
                        {
                          "edges": [
                            {"version": 60, "source": 1000, "target": 9000, "properties": {"createdAt": 10, "permission": "me", "receivedFrom": null}, "context": {}}
                          ],
                          "count": 1,
                          "total": 1,
                          "offset": null,
                          "hasNext": false,
                          "context": {}
                        }
                        """.trimIndent().toDataFrameEdgePayload().toNormalizedString()
                    actualObject.toNormalizedString() shouldBe expected
                }.verifyComplete()

            queryService
                .scan(database, table, GraphFixtures.index2, "1000", Direction.OUT, limit = 10)
                .test()
                .assertNext { actualObject ->
                    val expected =
                        """
                        {
                          "edges": [
                            {"version": 90, "source": 1000, "target": 9001, "properties": {"createdAt": 80, "permission": "others", "receivedFrom": "others"}, "context": {}},
                            {"version": 60, "source": 1000, "target": 9000, "properties": {"createdAt": 10, "permission": "me", "receivedFrom": null}, "context": {}}
                          ],
                          "count": 2,
                          "total": -1,
                          "offset": null,
                          "hasNext": false,
                          "context": {}
                        }
                        """.trimIndent().toDataFrameEdgePayload().toNormalizedString()
                    actualObject.toNormalizedString() shouldBe expected
                }.verifyComplete()

            queryService
                .count(database, table, "1000", Direction.OUT)
                .test()
                .assertNext {
                    it.start shouldBe 1000L
                    it.count shouldBe 2L
                }.verifyComplete()

            queryService
                .scan(database, table, GraphFixtures.index1, "1000", Direction.OUT, limit = 10, ranges = "permission:eq:me")
                .test()
                .assertNext { actualObject ->
                    val expected =
                        """
                        {
                          "edges": [
                            {"version": 60, "source": 1000, "target": 9000, "properties": {"createdAt": 10, "permission": "me", "receivedFrom": null}, "context": {}}
                          ],
                          "count": 1,
                          "total": -1,
                          "offset": null,
                          "hasNext": false,
                          "context": {}
                        }
                        """.trimIndent().toDataFrameEdgePayload().toNormalizedString()
                    actualObject.toNormalizedString() shouldBe expected
                }.verifyComplete()
        }

        "ISSUE-4130" {
            val database = GraphFixtures.serviceName
            val table = "ts_desc_label"
            val index = "ts_desc"

            val v2LabelDescriptor =
                """
                {
                  "desc": "the label with ts_desc",
                  "type": "INDEXED",
                  "schema": {
                    "src": { "type": "LONG", "desc": "user id" },
                    "tgt": { "type": "LONG", "desc": "product id" },
                    "fields": [ ]
                  },
                  "dirType": "BOTH",
                  "storage": "${GraphFixtures.hbaseStorage}",
                  "indices": [
                    {
                      "name": "$index",
                      "fields": [ { "name": "ts", "order": "DESC" } ],
                      "desc": "ts desc"
                    }
                  ]
                }
                """.trimIndent()
            val createRequest = mapper.readValue<LabelCreateRequest>(v2LabelDescriptor)

            graph.labelDdl
                .create(EntityName(database, table), createRequest)
                .block()

            val insertRequest1 =
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "source": "1", "target": "1"}}
                  ]
                }
                """.trimIndent().toEdgeBulkMutationRequest()

            mutationService
                .mutate(database, table, insertRequest1.mutations)
                .block()

            val insertRequest2 =
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 9, "source": "1", "target": "9"}}
                  ]
                }
                """.trimIndent().toEdgeBulkMutationRequest()

            mutationService
                .mutate(database, table, insertRequest2.mutations)
                .block()

            queryService
                .scan(database, table, index, "1", Direction.OUT, limit = 10)
                .test()
                .assertNext { actualObject ->
                    val expected =
                        """
                        {
                          "edges": [
                            {"version": 9, "source": 1, "target": 9, "properties": {}, "context": {}},
                            {"version": 1, "source": 1, "target": 1, "properties": {}, "context": {}}
                          ],
                          "count": 2,
                          "total": -1,
                          "offset": null,
                          "hasNext": false,
                          "context": {}
                        }
                        """.trimIndent().toDataFrameEdgePayload().toNormalizedString()
                    actualObject.toNormalizedString() shouldBe expected
                }.verifyComplete()
        }

        "NilLabel mutation should return IDLE" {
            val database = Metadata.sysServiceName
            val table = Metadata.sysNilLabelName
            val insertRequest =
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 10, "source": "1000", "target": "9000"}}
                  ]
                }
                """.trimIndent().toEdgeBulkMutationRequest()

            mutationService
                .mutate(database, table, insertRequest.mutations)
                .map { EdgeMutationResponse.from(it) }
                .test()
                .assertNext { actualObject ->
                    val expected =
                        """
                        {
                          "results": [
                            {"status": "IDLE", "source": "1000", "target": "9000", "count": 1}
                          ]
                        }
                        """.trimIndent().toEdgeMutationResponse().toNormalizedString()
                    actualObject.toNormalizedString() shouldBe expected
                }.verifyComplete()
        }

        "INSERT missing non-nullable field should fail before WAL publication" {
            val database = GraphFixtures.serviceName
            val table = GraphFixtures.hbaseIndexed // createdAt: LONG, nullable=false

            val invalidRequest =
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 10, "source": "1000", "target": "9000", "properties": {"permission": "na"}}}
                  ]
                }
                """.trimIndent().toEdgeBulkMutationRequest()

            mutationService
                .mutate(database, table, invalidRequest.mutations)
                .test()
                .verifyError(IllegalArgumentException::class.java)

            // No row should be persisted
            queryService
                .gets(database, table, listOf("1000"), listOf("9000"))
                .test()
                .assertNext { it.edges.isEmpty() shouldBe true }
                .verifyComplete()
        }

        "bulk INSERT with one invalid item should not publish any item (all-or-nothing)" {
            val database = GraphFixtures.serviceName
            val table = GraphFixtures.hbaseIndexed

            val mixedRequest =
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 10, "source": "2000", "target": "9000", "properties": {"permission": "na", "createdAt": 10}}},
                    {"type": "INSERT", "edge": {"version": 11, "source": "2000", "target": "9001", "properties": {"permission": "na"}}}
                  ]
                }
                """.trimIndent().toEdgeBulkMutationRequest()

            mutationService
                .mutate(database, table, mixedRequest.mutations)
                .test()
                .verifyError(IllegalArgumentException::class.java)

            // The valid item (2000→9000) must also not be written
            queryService
                .gets(database, table, listOf("2000"), listOf("9000"))
                .test()
                .assertNext { it.edges.isEmpty() shouldBe true }
                .verifyComplete()
        }

        "runEvenIfCancelled should complete execution even when cancelled" {
            val database = GraphFixtures.serviceName
            val table = GraphFixtures.hbaseIndexed
            val insertRequest =
                """
                {
                    "mutations": [
                        {"type": "INSERT", "edge": {"version": 10, "source": "9999", "target": "9999", "properties": {"permission": "na", "createdAt": 10}}}
                    ]
                }
                """.trimIndent().toEdgeBulkMutationRequest()

            val executionCompleted = AtomicBoolean(false)

            val result =
                Mono
                    .delay(Duration.ofMillis(2000))
                    .then(mutationService.mutate(database, table, insertRequest.mutations).map { EdgeMutationResponse.from(it) })
                    .doOnSuccess { executionCompleted.set(true) }
                    .runEvenIfCancelled()

            // Cancel immediately after first subscription (execution continues in background)
            result.subscribe().dispose()

            // Verify cached result with second subscription
            result
                .test()
                .assertNext { actualObject ->
                    val expected =
                        """
                        {
                          "results": [
                            {"status": "CREATED", "source": 9999, "target": 9999, "count": 1}
                          ]
                        }
                        """.trimIndent().toEdgeMutationResponse().toNormalizedString()
                    actualObject.toNormalizedString() shouldBe expected
                }.verifyComplete()

            executionCompleted.get() shouldBe true
        }
        "vertex mutation and query" {
            val database = GraphFixtures.serviceName
            val table = "users"

            // Create vertex table (LabelType.VERTEX, no index)
            val createRequest =
                mapper.readValue<LabelCreateRequest>(
                    """
                    {
                      "desc": "users vertex table",
                      "type": "VERTEX",
                      "schema": {
                        "src": { "type": "STRING", "desc": "user id" },
                        "tgt": { "type": "STRING", "desc": "<vertex>" },
                        "fields": [
                          { "name": "name", "type": "STRING", "nullable": false }
                        ]
                      },
                      "dirType": "OUT",
                      "storage": "${GraphFixtures.datastoreStorage}",
                      "indices": []
                    }
                    """.trimIndent(),
                )
            graph.labelDdl.create(EntityName(database, table), createRequest).block()

            // Insert two vertices
            val insertRequest =
                mapper.readValue<VertexBulkMutationRequest>(
                    """
                    {
                      "mutations": [
                        {"type": "INSERT", "vertex": {"version": 1, "id": "user1", "properties": {"name": "Alice"}}},
                        {"type": "INSERT", "vertex": {"version": 1, "id": "user2", "properties": {"name": "Bob"}}}
                      ]
                    }
                    """.trimIndent(),
                )

            val mutateResult =
                mutationService
                    .mutate(database, table, insertRequest.mutations)
                    .map { VertexMutationResponse.from(it) }
                    .block()!!

            mutateResult.results.map { it.status } shouldBe listOf("CREATED", "CREATED")
            mutateResult.results.map { it.id.toString() } shouldBe listOf("user1", "user2")

            // Multi-Get: user1, user2 found; user3 absent
            val getResult =
                queryService
                    .getVertices(database, table, listOf("user1", "user2", "user3"))
                    .block()!!

            getResult.edges.size shouldBe 2
            getResult.edges.all { it.target == VERTEX_MARKER } shouldBe true
            getResult.edges.map { it.source.toString() }.sorted() shouldBe listOf("user1", "user2")

            // Update user1
            val updateRequest =
                mapper.readValue<VertexBulkMutationRequest>(
                    """
                    {
                      "mutations": [
                        {"type": "UPDATE", "vertex": {"version": 2, "id": "user1", "properties": {"name": "Alice Updated"}}}
                      ]
                    }
                    """.trimIndent(),
                )
            val updateResult =
                mutationService
                    .mutate(database, table, updateRequest.mutations)
                    .map { VertexMutationResponse.from(it) }
                    .block()!!
            updateResult.results[0].status shouldBe "UPDATED"

            // Delete user2
            val deleteRequest =
                mapper.readValue<VertexBulkMutationRequest>(
                    """
                    {
                      "mutations": [
                        {"type": "DELETE", "vertex": {"version": 3, "id": "user2", "properties": {}}}
                      ]
                    }
                    """.trimIndent(),
                )
            val deleteResult =
                mutationService
                    .mutate(database, table, deleteRequest.mutations)
                    .map { VertexMutationResponse.from(it) }
                    .block()!!
            deleteResult.results[0].status shouldBe "DELETED"

            // After delete: only user1 remains
            val afterDeleteResult =
                queryService
                    .getVertices(database, table, listOf("user1", "user2"))
                    .block()!!
            afterDeleteResult.edges.size shouldBe 1
            afterDeleteResult.edges[0].source.toString() shouldBe "user1"
        }

        "INSERT missing non-nullable field with INSERT_MERGE surfaces INVALID at transit" {
            val database = GraphFixtures.serviceName
            val table = GraphFixtures.hbaseIndexed // createdAt: LONG, nullable=false

            // With the merge gate an omitted field passes request validation (it may be supplied
            // by a prior write); on this empty row completeness fails at transit, post-WAL, and
            // surfaces as a per-item INVALID (permanent) status instead of a transient ERROR.
            val request =
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 10, "source": "4000", "target": "9000", "properties": {"permission": "na"}}}
                  ]
                }
                """.trimIndent().toEdgeBulkMutationRequest()

            mergeMutationService
                .mutate(database, table, request.mutations)
                .test()
                .assertNext { results ->
                    results.size shouldBe 1
                    results[0].status shouldBe "INVALID"
                }.verifyComplete()

            // No row should be persisted
            queryService
                .gets(database, table, listOf("4000"), listOf("9000"))
                .test()
                .assertNext { it.edges.isEmpty() shouldBe true }
                .verifyComplete()
        }

        "partial INSERTs with INSERT_MERGE merge into one row (fan-in)" {
            val database = GraphFixtures.serviceName
            val table = GraphFixtures.hbaseIndexed

            val first =
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 10, "source": "5000", "target": "9000", "properties": {"permission": "na", "createdAt": 10}}}
                  ]
                }
                """.trimIndent().toEdgeBulkMutationRequest()
            val second =
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 20, "source": "5000", "target": "9000", "properties": {"permission": "nb"}}}
                  ]
                }
                """.trimIndent().toEdgeBulkMutationRequest()

            mergeMutationService
                .mutate(database, table, first.mutations)
                .test()
                .assertNext { it[0].status shouldBe "CREATED" }
                .verifyComplete()

            // The second INSERT omits the non-nullable createdAt — legal under merge: the value
            // written by the first source is kept, the row stays active.
            mergeMutationService
                .mutate(database, table, second.mutations)
                .test()
                .assertNext { it[0].status shouldBe "UPDATED" }
                .verifyComplete()

            queryService
                .gets(database, table, listOf("5000"), listOf("9000"))
                .test()
                .assertNext { payload ->
                    payload.edges.size shouldBe 1
                    payload.edges[0].properties["permission"] shouldBe "nb"
                    payload.edges[0].properties["createdAt"] shouldBe 10L
                }.verifyComplete()
        }
    }) {
    companion object {
        val mapper = jacksonObjectMapper()

        fun String.toEdgeBulkMutationRequest(): EdgeBulkMutationRequest = mapper.readValue(this)

        fun String.toEdgeMutationResponse(): EdgeMutationResponse = mapper.readValue(this)

        fun String.toDataFrameEdgePayload(): DataFrameEdgePayload = mapper.readValue(this)

        fun Any?.toNormalizedString(): String = mapper.writeValueAsString(this)
    }
}
