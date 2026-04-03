package com.kakao.actionbase.v2.engine.v3.edge

import com.kakao.actionbase.engine.metadata.MutationMode as EngineMutationMode

import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest
import com.kakao.actionbase.core.edge.payload.EdgeMutationResponse
import com.kakao.actionbase.core.edge.payload.MultiEdgeBulkMutationRequest
import com.kakao.actionbase.core.edge.payload.MultiEdgeMutationResponse
import com.kakao.actionbase.engine.service.MutationService
import com.kakao.actionbase.engine.service.QueryService
import com.kakao.actionbase.v2.core.metadata.MutationMode
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.GraphConfig
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.label.Label
import com.kakao.actionbase.v2.engine.service.ddl.LabelCreateRequest
import com.kakao.actionbase.v2.engine.service.ddl.ServiceCreateRequest
import com.kakao.actionbase.v2.engine.test.GraphFixtures
import com.kakao.actionbase.v2.engine.test.cdc.InMemoryCdc
import com.kakao.actionbase.v2.engine.test.wal.InMemoryWal
import com.kakao.actionbase.v2.engine.v3.V2BackedEngine

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import reactor.kotlin.test.test

class MutationServiceSystemAsyncSpec :
    StringSpec({

        val database = GraphFixtures.serviceName

        val syncEdgeTableName = "sync_edge"
        val syncMultiEdgeTableName = "sync_multi_edge"
        val asyncEdgeTableName = "async_edge"
        val asyncMultiEdgeTableName = "async_multi_edge"

        lateinit var syncEdgeTable: Label
        lateinit var syncMultiEdgeTable: Label
        lateinit var asyncEdgeTable: Label
        lateinit var asyncMultiEdgeTable: Label

        lateinit var graph: Graph
        lateinit var mutationService: MutationService
        lateinit var queryService: QueryService

        beforeTest {
            graph =
                GraphFixtures.create(
                    configBuilder = GraphConfig.Builder().withSystemMutationMode(MutationMode.ASYNC),
                    withTestData = false,
                )

            graph.serviceDdl
                .create(EntityName.fromOrigin(database), ServiceCreateRequest(desc = "test service"))
                .block()
            graph.labelDdl.create(EntityName(database, syncEdgeTableName), mapper.readValue<LabelCreateRequest>(syncEdgeDescriptor)).block()
            graph.labelDdl.create(EntityName(database, syncMultiEdgeTableName), mapper.readValue<LabelCreateRequest>(syncMultiEdgeDescriptor)).block()
            graph.labelDdl.create(EntityName(database, asyncEdgeTableName), mapper.readValue<LabelCreateRequest>(asyncEdgeDescriptor)).block()
            graph.labelDdl.create(EntityName(database, asyncMultiEdgeTableName), mapper.readValue<LabelCreateRequest>(asyncMultiEdgeDescriptor)).block()

            syncEdgeTable = graph.getLabel(EntityName(database, syncEdgeTableName))
            syncMultiEdgeTable = graph.getLabel(EntityName(database, syncMultiEdgeTableName))
            asyncEdgeTable = graph.getLabel(EntityName(database, asyncEdgeTableName))
            asyncMultiEdgeTable = graph.getLabel(EntityName(database, asyncMultiEdgeTableName))

            mutationService = MutationService(V2BackedEngine(graph))
            queryService = QueryService(graph)
        }

        afterTest {
            graph.close()
            (graph.wal as InMemoryWal).init()
            (graph.cdc as InMemoryCdc).init()
        }

        fun verifyWal(
            graph: Graph,
            table: Label,
            expectedSize: Int,
            queue: Boolean,
        ) {
            val walActual = (graph.wal as InMemoryWal).readWal().filter { it.label == table.name }
            walActual.size shouldBe expectedSize
            walActual.all { it.mode.queue == queue } shouldBe true
        }

        fun verifyCdc(
            graph: Graph,
            table: Label,
            expectedSize: Int = 0,
        ) {
            val cdcActual = (graph.cdc as InMemoryCdc).readCdc().filter { it.label == table.name }
            if (expectedSize == 0) {
                cdcActual.shouldBeEmpty()
            } else {
                cdcActual.size shouldBe expectedSize
            }
        }

        // ---- scenario 1: system=ASYNC overrides table ----

        "system=ASYNC overrides SYNC EDGE table" {
            val request =
                mapper.readValue<EdgeBulkMutationRequest>(
                    """
                    {
                      "mutations": [
                        {"type": "INSERT", "edge": {"version": 10, "source": "1000", "target": "9000", "properties": {"permission": "na", "createdAt": 10}}}
                      ]
                    }
                    """.trimIndent(),
                )

            mutationService
                .mutate(database, syncEdgeTableName, request.mutations)
                .map { EdgeMutationResponse.from(it) }
                .test()
                .assertNext {
                    mapper.writeValueAsString(it) shouldBe """{"results":[{"source":1000,"target":9000,"status":"QUEUED","count":1}]}"""
                }.verifyComplete()

            verifyWal(graph, syncEdgeTable, 1, queue = true)
            verifyCdc(graph, syncEdgeTable)

            queryService
                .gets(database, syncEdgeTableName, listOf(1000L), listOf(9000L))
                .test()
                .assertNext { it.edges.size shouldBe 0 }
                .verifyComplete()
        }

        "system=ASYNC overrides SYNC MULTI_EDGE table" {
            val request =
                mapper.readValue<MultiEdgeBulkMutationRequest>(
                    """
                    {
                      "mutations": [
                        {"type": "INSERT", "edge": {"version": 10, "id": 100000, "source": 1, "target": 2, "properties": {"paidAt": 1234567890, "productId": 200}}}
                      ]
                    }
                    """.trimIndent(),
                )

            mutationService
                .mutate(database, syncMultiEdgeTableName, request.mutations)
                .map { MultiEdgeMutationResponse.from(it) }
                .test()
                .assertNext {
                    mapper.writeValueAsString(it) shouldBe """{"results":[{"id":100000,"status":"QUEUED","count":1}]}"""
                }.verifyComplete()

            verifyWal(graph, syncMultiEdgeTable, 1, queue = true)
            verifyCdc(graph, syncMultiEdgeTable)

            queryService
                .gets(database, syncMultiEdgeTableName, listOf(100000L), listOf(100000L))
                .test()
                .assertNext { it.edges.size shouldBe 0 }
                .verifyComplete()
        }

        // ---- scenario 2: system=ASYNC overrides request=SYNC ----

        "system=ASYNC overrides request=SYNC on ASYNC EDGE table" {
            val request =
                mapper.readValue<EdgeBulkMutationRequest>(
                    """
                    {
                      "mutations": [
                        {"type": "INSERT", "edge": {"version": 10, "source": "1000", "target": "9000", "properties": {"paidAt": 1234567890, "productId": 200}}}
                      ]
                    }
                    """.trimIndent(),
                )

            mutationService
                .mutate(database, asyncEdgeTableName, request.mutations, syncMode = EngineMutationMode.SYNC)
                .map { EdgeMutationResponse.from(it) }
                .test()
                .assertNext {
                    mapper.writeValueAsString(it) shouldBe """{"results":[{"source":1000,"target":9000,"status":"QUEUED","count":1}]}"""
                }.verifyComplete()

            verifyWal(graph, asyncEdgeTable, 1, queue = true)
            verifyCdc(graph, asyncEdgeTable)

            queryService
                .gets(database, asyncEdgeTableName, listOf(1000L), listOf(9000L))
                .test()
                .assertNext { it.edges.size shouldBe 0 }
                .verifyComplete()
        }

        "system=ASYNC overrides request=SYNC on ASYNC MULTI_EDGE table" {
            val request =
                mapper.readValue<MultiEdgeBulkMutationRequest>(
                    """
                    {
                      "mutations": [
                        {"type": "INSERT", "edge": {"version": 10, "id": 100000, "source": 1, "target": 2, "properties": {"paidAt": 1234567890, "productId": 200}}}
                      ]
                    }
                    """.trimIndent(),
                )

            mutationService
                .mutate(database, asyncMultiEdgeTableName, request.mutations, syncMode = EngineMutationMode.SYNC)
                .map { MultiEdgeMutationResponse.from(it) }
                .test()
                .assertNext {
                    mapper.writeValueAsString(it) shouldBe """{"results":[{"id":100000,"status":"QUEUED","count":1}]}"""
                }.verifyComplete()

            verifyWal(graph, asyncMultiEdgeTable, 1, queue = true)
            verifyCdc(graph, asyncMultiEdgeTable)

            queryService
                .gets(database, asyncMultiEdgeTableName, listOf(100000L), listOf(100000L))
                .test()
                .assertNext { it.edges.size shouldBe 0 }
                .verifyComplete()
        }

        // ---- scenario 3: force=true request=SYNC overrides system=ASYNC ----

        "force=true request=SYNC overrides system=ASYNC on ASYNC EDGE table" {
            val request =
                mapper.readValue<EdgeBulkMutationRequest>(
                    """
                    {
                      "mutations": [
                        {"type": "INSERT", "edge": {"version": 10, "source": "1000", "target": "9000", "properties": {"paidAt": 1234567890, "productId": 200}}}
                      ]
                    }
                    """.trimIndent(),
                )

            mutationService
                .mutate(database, asyncEdgeTableName, request.mutations, syncMode = EngineMutationMode.SYNC, forceSyncMode = true)
                .map { EdgeMutationResponse.from(it) }
                .test()
                .assertNext {
                    mapper.writeValueAsString(it) shouldBe """{"results":[{"source":1000,"target":9000,"status":"CREATED","count":1}]}"""
                }.verifyComplete()

            verifyWal(graph, asyncEdgeTable, 1, queue = false)
            verifyCdc(graph, asyncEdgeTable, 1)

            queryService
                .gets(database, asyncEdgeTableName, listOf(1000L), listOf(9000L))
                .test()
                .assertNext { it.edges.size shouldBe 1 }
                .verifyComplete()
        }

        "force=true request=SYNC overrides system=ASYNC on ASYNC MULTI_EDGE table" {
            val request =
                mapper.readValue<MultiEdgeBulkMutationRequest>(
                    """
                    {
                      "mutations": [
                        {"type": "INSERT", "edge": {"version": 10, "id": 100000, "source": 1, "target": 2, "properties": {"paidAt": 1234567890, "productId": 200}}}
                      ]
                    }
                    """.trimIndent(),
                )

            mutationService
                .mutate(database, asyncMultiEdgeTableName, request.mutations, syncMode = EngineMutationMode.SYNC, forceSyncMode = true)
                .map { MultiEdgeMutationResponse.from(it) }
                .test()
                .assertNext {
                    mapper.writeValueAsString(it) shouldBe """{"results":[{"id":100000,"status":"CREATED","count":1}]}"""
                }.verifyComplete()

            verifyWal(graph, asyncMultiEdgeTable, 1, queue = false)
            verifyCdc(graph, asyncMultiEdgeTable, 1)

            queryService
                .gets(database, asyncMultiEdgeTableName, listOf(100000L), listOf(100000L))
                .test()
                .assertNext { it.edges.size shouldBe 1 }
                .verifyComplete()
        }
    }) {
    companion object {
        private val mapper = jacksonObjectMapper()

        private val syncEdgeDescriptor =
            """
            {
              "desc": "sync edge for global mode test",
              "type": "INDEXED",
              "schema": {
                "src": {"type": "LONG", "desc": "sender"},
                "tgt": {"type": "LONG", "desc": "receiver"},
                "fields": [
                  {"name": "permission", "type": "STRING", "nullable": false, "desc": "permission"},
                  {"name": "createdAt", "type": "LONG", "nullable": false, "desc": "created at"}
                ]
              },
              "dirType": "BOTH",
              "storage": "${GraphFixtures.datastoreStorage}",
              "indices": [
                {"name": "created_at_desc", "fields": [{"name": "createdAt", "order": "DESC"}], "desc": "recently created first"}
              ],
              "event": false,
              "readOnly": true
            }
            """.trimIndent()

        private val syncMultiEdgeDescriptor =
            """
            {
              "desc": "sync multi edge for global mode test",
              "type": "MULTI_EDGE",
              "schema": {
                "src": {"type": "LONG", "desc": "sender"},
                "tgt": {"type": "LONG", "desc": "receiver"},
                "fields": [
                  {"name": "_id", "type": "LONG", "nullable": false, "desc": "order.id"},
                  {"name": "paidAt", "type": "LONG", "nullable": false, "desc": "payment time"},
                  {"name": "productId", "type": "LONG", "nullable": false, "desc": "product id"}
                ]
              },
              "dirType": "BOTH",
              "storage": "${GraphFixtures.datastoreStorage}",
              "indices": [
                {"name": "paid_at_desc", "fields": [{"name": "paidAt", "order": "DESC"}], "desc": "recently paid first"}
              ],
              "event": false,
              "readOnly": true
            }
            """.trimIndent()

        private val asyncEdgeDescriptor =
            """
            {
              "desc": "async edge for global mode test",
              "type": "INDEXED",
              "schema": {
                "src": {"type": "LONG", "desc": "sender"},
                "tgt": {"type": "LONG", "desc": "receiver"},
                "fields": [
                  {"name": "paidAt", "type": "LONG", "nullable": false, "desc": "payment time"},
                  {"name": "productId", "type": "LONG", "nullable": false, "desc": "product id"}
                ]
              },
              "dirType": "BOTH",
              "storage": "${GraphFixtures.datastoreStorage}",
              "indices": [
                {"name": "paid_at_desc", "fields": [{"name": "paidAt", "order": "DESC"}], "desc": "recently paid first"}
              ],
              "event": false,
              "readOnly": true,
              "mode": "ASYNC"
            }
            """.trimIndent()

        private val asyncMultiEdgeDescriptor =
            """
            {
              "desc": "async multi edge for global mode test",
              "type": "MULTI_EDGE",
              "schema": {
                "src": {"type": "LONG", "desc": "sender"},
                "tgt": {"type": "LONG", "desc": "receiver"},
                "fields": [
                  {"name": "_id", "type": "LONG", "nullable": false, "desc": "order.id"},
                  {"name": "paidAt", "type": "LONG", "nullable": false, "desc": "payment time"},
                  {"name": "productId", "type": "LONG", "nullable": false, "desc": "product id"}
                ]
              },
              "dirType": "BOTH",
              "storage": "${GraphFixtures.datastoreStorage}",
              "indices": [
                {"name": "paid_at_desc", "fields": [{"name": "paidAt", "order": "DESC"}], "desc": "recently paid first"}
              ],
              "event": false,
              "readOnly": true,
              "mode": "ASYNC"
            }
            """.trimIndent()
    }
}
