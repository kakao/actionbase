package com.kakao.actionbase.v2.engine.v3.edge

import com.kakao.actionbase.engine.metadata.MutationMode as EngineMutationMode

import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest
import com.kakao.actionbase.core.edge.payload.EdgeMutationResponse
import com.kakao.actionbase.engine.service.MutationService
import com.kakao.actionbase.engine.service.QueryService
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.service.ddl.LabelCreateRequest
import com.kakao.actionbase.v2.engine.test.GraphFixtures
import com.kakao.actionbase.v2.engine.test.cdc.InMemoryCdc
import com.kakao.actionbase.v2.engine.v3.V2BackedEngine

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import reactor.kotlin.test.test

/**
 * End-to-end coverage for immutable edge tables on the HBase mini-cluster: append persists
 * index rows only (no State), scan reads them back, point get is rejected, and no count
 * records are produced.
 */
class ImmutableEdgeSpec :
    StringSpec({

        val labelName = EntityName(GraphFixtures.serviceName, "immutable_log")
        val database = labelName.service
        val table = labelName.nameNotNull

        lateinit var graph: Graph
        lateinit var mutationService: MutationService
        lateinit var queryService: QueryService

        val labelDefinition =
            """
            {
              "desc": "append-only log",
              "type": "IMMUTABLE_INDEXED",
              "schema": {
                "src": {"type": "LONG", "desc": "partition"},
                "tgt": {"type": "STRING", "desc": "message id"},
                "fields": [
                  {"name": "seq", "type": "LONG", "nullable": false, "desc": "enqueue seq"},
                  {"name": "payload", "type": "STRING", "nullable": true, "desc": "payload"}
                ]
              },
              "dirType": "OUT",
              "storage": "${GraphFixtures.hbaseStorage}",
              "indices": [
                {"name": "seq_asc", "fields": [{"name": "seq", "order": "ASC"}], "desc": "oldest first"}
              ],
              "event": false,
              "readOnly": false,
              "mode": "SYNC"
            }
            """.trimIndent()

        val appendRequest =
            """
            {
              "mutations": [
                {"type": "INSERT", "edge": {"version": 1000, "source": 1, "target": "m1", "properties": {"seq": 1000, "payload": "a"}}},
                {"type": "INSERT", "edge": {"version": 1001, "source": 1, "target": "m2", "properties": {"seq": 1001, "payload": "b"}}},
                {"type": "INSERT", "edge": {"version": 1002, "source": 1, "target": "m3", "properties": {"seq": 1002, "payload": "c"}}}
              ]
            }
            """.trimIndent()

        beforeTest {
            graph = GraphFixtures.create()
            val request = mapper.readValue<LabelCreateRequest>(labelDefinition)
            graph.labelDdl.create(labelName, request).block()
            val engine = V2BackedEngine(graph)
            mutationService = MutationService(engine)
            queryService = QueryService(engine)
        }

        afterTest {
            graph.close()
            (graph.cdc as InMemoryCdc).init()
        }

        "append then scan returns the appended edges in index order" {
            val request = mapper.readValue<EdgeBulkMutationRequest>(appendRequest)
            mutationService
                .mutate(database, table, request.mutations, syncMode = EngineMutationMode.SYNC)
                .map { EdgeMutationResponse.from(it) }
                .test()
                .assertNext { result ->
                    result.results.size shouldBe 3
                    result.results.all { it.status == "CREATED" } shouldBe true
                }.verifyComplete()

            queryService
                .scan(database, table, "seq_asc", 1L, Direction.OUT, limit = 10)
                .test()
                .assertNext { result ->
                    result.edges.size shouldBe 3
                    result.edges.map { it.target } shouldBe listOf("m1", "m2", "m3")
                    result.edges.map { it.properties["payload"] } shouldBe listOf("a", "b", "c")
                }.verifyComplete()
        }

        "scanDelete removes the matched edges and leaves the rest" {
            val request = mapper.readValue<EdgeBulkMutationRequest>(appendRequest)
            mutationService
                .mutate(database, table, request.mutations, syncMode = EngineMutationMode.SYNC)
                .block()

            mutationService
                .scanDelete(database, table, "seq_asc", 1L, Direction.OUT, limit = 10, ranges = "seq:lte:1001")
                .test()
                .assertNext { deleted -> deleted shouldBe 2 }
                .verifyComplete()

            queryService
                .scan(database, table, "seq_asc", 1L, Direction.OUT, limit = 10)
                .test()
                .assertNext { result ->
                    result.edges.map { it.target } shouldBe listOf("m3")
                }.verifyComplete()
        }

        "point get is rejected on immutable edge tables" {
            val request = mapper.readValue<EdgeBulkMutationRequest>(appendRequest)
            mutationService
                .mutate(database, table, request.mutations, syncMode = EngineMutationMode.SYNC)
                .block()

            queryService
                .gets(database, table, listOf(1L), listOf("m1"))
                .test()
                .verifyError(UnsupportedOperationException::class.java)
        }

        "immutable edge rejects non-INSERT mutations" {
            val deleteRequest =
                """
                {"mutations": [{"type": "DELETE", "edge": {"version": 2000, "source": 1, "target": "m1", "properties": {}}}]}
                """.trimIndent()
            val request = mapper.readValue<EdgeBulkMutationRequest>(deleteRequest)
            mutationService
                .mutate(database, table, request.mutations, syncMode = EngineMutationMode.SYNC)
                .test()
                .verifyError(IllegalArgumentException::class.java)
        }

        "immutable edge produces no count records" {
            val request = mapper.readValue<EdgeBulkMutationRequest>(appendRequest)
            mutationService
                .mutate(database, table, request.mutations, syncMode = EngineMutationMode.SYNC)
                .block()

            queryService
                .count(database, table, 1L, Direction.OUT)
                .test()
                .assertNext { result -> result.count shouldBe 0L }
                .verifyComplete()
        }

        "immutable edge emits no CDC on append" {
            val request = mapper.readValue<EdgeBulkMutationRequest>(appendRequest)
            val results =
                mutationService
                    .mutate(database, table, request.mutations, syncMode = EngineMutationMode.SYNC)
                    .block()!!
            results.all { it.status == "CREATED" } shouldBe true

            (graph.cdc as InMemoryCdc).readCdc().filter { it.label == labelName }.shouldBeEmpty()
        }
    }) {
    companion object {
        private val mapper = jacksonObjectMapper()
    }
}
