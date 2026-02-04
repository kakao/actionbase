package com.kakao.actionbase.v2.engine.v3.edge

import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest
import com.kakao.actionbase.core.edge.payload.MultiEdgeBulkMutationRequest
import com.kakao.actionbase.v2.core.metadata.MutationMode
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.GraphConfig
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.service.ddl.LabelCreateRequest
import com.kakao.actionbase.v2.engine.service.ddl.ServiceCreateRequest
import com.kakao.actionbase.v2.engine.test.GraphFixtures
import com.kakao.actionbase.v2.engine.test.cdc.InMemoryCdc
import com.kakao.actionbase.v2.engine.test.wal.InMemoryWal
import com.kakao.actionbase.v2.engine.v3.V3MutationService
import com.kakao.actionbase.v2.engine.v3.V3QueryService

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import reactor.kotlin.test.test

class V3MutationServiceAsyncSpec :
    StringSpec({

        val multiEdgeTableName = EntityName(GraphFixtures.serviceName, "edge_table_async_multi_edge")
        val edgeTableName = EntityName(GraphFixtures.serviceName, "edge_table_async_edge")

        lateinit var graph: Graph
        lateinit var wal: InMemoryWal
        lateinit var cdc: InMemoryCdc
        lateinit var v3MutationService: V3MutationService
        lateinit var v3QueryService: V3QueryService

        val multiEdgeRequestString =
            """
            {
              "mutations": [
                {"type": "INSERT", "edge": {"version": 1234567890, "id": 100000, "source": 1, "target": 2, "properties": {"paidAt": 1234567890, "productId": 200}}},
                {"type": "INSERT", "edge": {"version": 1234567890, "id": 100001, "source": 1, "target": 2, "properties": {"paidAt": 1234567890, "productId": 201}}},
                {"type": "INSERT", "edge": {"version": 1234567892, "id": 100002, "source": 1, "target": 0, "properties": {"paidAt": 1234567892, "productId": 202}}}
              ]
            }
            """.trimIndent()

        val edgeRequestString =
            """
            {
              "mutations": [
                {"type": "INSERT", "edge": {"version": 1234567890, "source": 1, "target": 2, "properties": {"paidAt": 1234567890, "productId": 200}}},
                {"type": "INSERT", "edge": {"version": 1234567890, "source": 1, "target": 2, "properties": {"paidAt": 1234567890, "productId": 201}}},
                {"type": "INSERT", "edge": {"version": 1234567892, "source": 1, "target": 0, "properties": {"paidAt": 1234567892, "productId": 202}}}
              ]
            }
            """.trimIndent()

        val multiEdgeDescriptor =
            """
            {
              "desc": "multi edge",
              "type": "MULTI_EDGE",
              "schema": {
                "src": {
                  "type": "LONG",
                  "desc": "sender"
                },
                "tgt": {
                  "type": "LONG",
                  "desc": "receiver"
                },
                "fields": [
                  {
                    "name": "_id",
                    "type": "LONG",
                    "nullable": false,
                    "desc": "order.id"
                  },
                  {
                    "name": "paidAt",
                    "type": "LONG",
                    "nullable": false,
                    "desc": "payment time"
                  },
            	  {
                    "name": "productId",
                    "type": "LONG",
                    "nullable": false,
                    "desc": "product id"
                  }
                ]
              },
              "dirType": "BOTH",
              "storage": "${GraphFixtures.hbaseStorage}",
              "indices": [
                {
                  "name": "paid_at_desc",
                  "fields": [
                    {
                      "name": "paidAt",
                      "order": "DESC"
                    }
                  ],
                  "desc": "recently paid first"
                }
              ],
              "event": false,
              "readOnly": true,
              "mode": "ASYNC"
            }
            """.trimIndent()

        val edgeDescriptor =
            """
            {
              "desc": "edge",
              "type": "INDEXED",
              "schema": {
                "src": {
                  "type": "LONG",
                  "desc": "sender"
                },
                "tgt": {
                  "type": "LONG",
                  "desc": "receiver"
                },
                "fields": [
                  {
                    "name": "paidAt",
                    "type": "LONG",
                    "nullable": false,
                    "desc": "payment time"
                  },
            	  {
                    "name": "productId",
                    "type": "LONG",
                    "nullable": false,
                    "desc": "product id"
                  }
                ]
              },
              "dirType": "BOTH",
              "storage": "${GraphFixtures.hbaseStorage}",
              "indices": [
                {
                  "name": "paid_at_desc",
                  "fields": [
                    {
                      "name": "paidAt",
                      "order": "DESC"
                    }
                  ],
                  "desc": "recently paid first"
                }
              ],
              "event": false,
              "readOnly": true,
              "mode": "ASYNC"
            }
            """.trimIndent()

        beforeTest {
            graph = GraphFixtures.create()
            wal = graph.wal as InMemoryWal
            cdc = graph.cdc as InMemoryCdc
            val request1 = mapper.readValue<LabelCreateRequest>(multiEdgeDescriptor)
            graph.labelDdl.create(multiEdgeTableName, request1).block()

            val request2 = mapper.readValue<LabelCreateRequest>(edgeDescriptor)
            graph.labelDdl.create(edgeTableName, request2).block()
            v3MutationService = V3MutationService(graph)
            v3QueryService = V3QueryService(graph)
        }

        afterTest {
            graph.close()
            wal.init()
            cdc.init()
        }

        fun verifyWal(
            tableName: EntityName,
            expectedSize: Int,
            queue: Boolean,
            tableMode: MutationMode = MutationMode.ASYNC,
            requestMode: MutationMode? = null,
            globalMode: MutationMode? = null,
            internalMode: MutationMode? = null,
            walSource: InMemoryWal = wal,
        ) {
            val walActual = walSource.readWal().filter { it.label == tableName }
            walActual.size shouldBe expectedSize
            walActual.all { it.mode.queue == queue } shouldBe true
            walActual.all { it.mode.t == tableMode } shouldBe true
            walActual.all { it.mode.r == requestMode } shouldBe true
            walActual.all { it.mode.g == globalMode } shouldBe true
            walActual.all { it.mode.i == internalMode } shouldBe true
        }

        fun verifyCdc(
            tableName: EntityName,
            expectedSize: Int = 0,
            cdcSource: InMemoryCdc = cdc,
        ) {
            val cdcActual = cdcSource.readCdc().filter { it.label == tableName }
            if (expectedSize == 0) {
                cdcActual.shouldBeEmpty()
            } else {
                cdcActual.size shouldBe expectedSize
            }
        }

        fun verifyEmptyQuery(
            tableName: EntityName,
            sources: List<Long>,
            targets: List<Long>,
            queryService: V3QueryService = v3QueryService,
        ) {
            queryService
                .gets(tableName.service, tableName.nameNotNull, sources, targets)
                .test()
                .assertNext { it.edges.size shouldBe 0 }
                .verifyComplete()
        }

        "ASYNC MULTI_EDGE table with sync request produces WAL and CDC" {
            val request = mapper.readValue<MultiEdgeBulkMutationRequest>(multiEdgeRequestString)

            v3MutationService
                .mutateMultiEdge(
                    multiEdgeTableName.service,
                    multiEdgeTableName.nameNotNull,
                    request,
                    mode = MutationMode.SYNC,
                ).test()
                .assertNext {
                    mapper.writeValueAsString(it) shouldBe """{"results":[{"id":100000,"status":"CREATED","count":1},{"id":100001,"status":"CREATED","count":1},{"id":100002,"status":"CREATED","count":1}]}"""
                }.verifyComplete()

            verifyWal(multiEdgeTableName, 3, queue = false, requestMode = MutationMode.SYNC)
            verifyCdc(multiEdgeTableName, 3)

            v3QueryService
                .gets(multiEdgeTableName.service, multiEdgeTableName.nameNotNull, listOf(100000L), listOf(100000L))
                .test()
                .assertNext { it.edges.size shouldBe 1 }
                .verifyComplete()
        }

        "ASYNC MULTI_EDGE table produces WAL but not CDC" {
            val request = mapper.readValue<MultiEdgeBulkMutationRequest>(multiEdgeRequestString)

            v3MutationService
                .mutateMultiEdge(multiEdgeTableName.service, multiEdgeTableName.nameNotNull, request)
                .test()
                .assertNext {
                    mapper.writeValueAsString(it) shouldBe """{"results":[{"id":100000,"status":"QUEUED","count":1},{"id":100001,"status":"QUEUED","count":1},{"id":100002,"status":"QUEUED","count":1}]}"""
                }.verifyComplete()

            verifyWal(multiEdgeTableName, 3, queue = true, requestMode = null)
            verifyCdc(multiEdgeTableName)
            verifyEmptyQuery(multiEdgeTableName, listOf(100000L), listOf(100000L))
        }

        "ASYNC EDGE table produces WAL but not CDC" {
            val request = mapper.readValue<EdgeBulkMutationRequest>(edgeRequestString)

            v3MutationService
                .mutateEdge(edgeTableName.service, edgeTableName.nameNotNull, request)
                .test()
                .assertNext {
                    mapper.writeValueAsString(it) shouldBe """{"results":[{"source":1,"target":0,"status":"QUEUED","count":1},{"source":1,"target":2,"status":"QUEUED","count":2}]}"""
                }.verifyComplete()

            verifyWal(edgeTableName, 3, queue = true, requestMode = null)
            verifyCdc(edgeTableName)
            verifyEmptyQuery(edgeTableName, listOf(1L), listOf(0L))
        }

        "ASYNC EDGE table with sync request produces WAL and CDC" {
            val request = mapper.readValue<EdgeBulkMutationRequest>(edgeRequestString)

            v3MutationService
                .mutateEdge(edgeTableName.service, edgeTableName.nameNotNull, request, mode = MutationMode.SYNC)
                .test()
                .assertNext {
                    mapper.writeValueAsString(it) shouldBe """{"results":[{"source":1,"target":0,"status":"CREATED","count":1},{"source":1,"target":2,"status":"CREATED","count":2}]}"""
                }.verifyComplete()

            verifyWal(edgeTableName, 3, queue = false, requestMode = MutationMode.SYNC)
            verifyCdc(edgeTableName, 2)

            v3QueryService
                .gets(edgeTableName.service, edgeTableName.nameNotNull, listOf(1L), listOf(2L))
                .test()
                .assertNext { it.edges.size shouldBe 1 }
                .verifyComplete()

            v3QueryService
                .gets(edgeTableName.service, edgeTableName.nameNotNull, listOf(1L), listOf(0L))
                .test()
                .assertNext { it.edges.size shouldBe 1 }
                .verifyComplete()
        }

        "ASYNC EDGE table with internal=SYNC produces WAL and CDC" {
            val request = mapper.readValue<EdgeBulkMutationRequest>(edgeRequestString)

            v3MutationService
                .internalMutateEdge(edgeTableName.service, edgeTableName.nameNotNull, request, internal = MutationMode.SYNC)
                .test()
                .assertNext {
                    mapper.writeValueAsString(it) shouldBe """{"results":[{"source":1,"target":0,"status":"CREATED","count":1},{"source":1,"target":2,"status":"CREATED","count":2}]}"""
                }.verifyComplete()

            verifyWal(edgeTableName, 3, queue = false, requestMode = null, internalMode = MutationMode.SYNC)
            verifyCdc(edgeTableName, 2)

            v3QueryService
                .gets(edgeTableName.service, edgeTableName.nameNotNull, listOf(1L), listOf(2L))
                .test()
                .assertNext { it.edges.size shouldBe 1 }
                .verifyComplete()

            v3QueryService
                .gets(edgeTableName.service, edgeTableName.nameNotNull, listOf(1L), listOf(0L))
                .test()
                .assertNext { it.edges.size shouldBe 1 }
                .verifyComplete()
        }

        "ASYNC MULTI_EDGE table with internal=SYNC produces WAL and CDC" {
            val request = mapper.readValue<MultiEdgeBulkMutationRequest>(multiEdgeRequestString)

            v3MutationService
                .internalMutateMultiEdge(
                    multiEdgeTableName.service,
                    multiEdgeTableName.nameNotNull,
                    request,
                    internal = MutationMode.SYNC,
                ).test()
                .assertNext {
                    mapper.writeValueAsString(it) shouldBe """{"results":[{"id":100000,"status":"CREATED","count":1},{"id":100001,"status":"CREATED","count":1},{"id":100002,"status":"CREATED","count":1}]}"""
                }.verifyComplete()

            verifyWal(multiEdgeTableName, 3, queue = false, requestMode = null, internalMode = MutationMode.SYNC)
            verifyCdc(multiEdgeTableName, 3)

            v3QueryService
                .gets(multiEdgeTableName.service, multiEdgeTableName.nameNotNull, listOf(100000L), listOf(100000L))
                .test()
                .assertNext { it.edges.size shouldBe 1 }
                .verifyComplete()
        }

        // mutual exclusivity between mode and internal is now enforced structurally:
        // mutateEdge accepts only mode, internalMutateEdge accepts only mode (as internal).
        // MutationModeContext-level mutual exclusivity is tested in MutationModeContextSpec.

        "global=ASYNC makes SYNC table queue mutations" {
            val globalAsyncGraph =
                GraphFixtures.create(
                    configBuilder = GraphConfig.Builder().withGlobalMutationMode(MutationMode.ASYNC),
                    withTestData = false,
                )
            globalAsyncGraph.use { graph ->
                val globalWal = graph.wal as InMemoryWal
                val globalCdc = graph.cdc as InMemoryCdc

                val database = GraphFixtures.serviceName
                val table = "sync_edge_for_global_test"
                val syncTableName = EntityName(database, table)

                graph.serviceDdl
                    .create(EntityName.fromOrigin(database), ServiceCreateRequest(desc = "test service"))
                    .block()
                graph.labelDdl
                    .create(
                        syncTableName,
                        mapper.readValue<LabelCreateRequest>(syncEdgeDescriptor),
                    ).block()

                val globalMutationService = V3MutationService(graph)
                val globalQueryService = V3QueryService(graph)
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

                globalMutationService
                    .mutateEdge(database, table, request)
                    .test()
                    .assertNext {
                        mapper.writeValueAsString(it) shouldBe """{"results":[{"source":1000,"target":9000,"status":"QUEUED","count":1}]}"""
                    }.verifyComplete()

                verifyWal(syncTableName, 1, queue = true, tableMode = MutationMode.SYNC, globalMode = MutationMode.ASYNC, walSource = globalWal)
                verifyCdc(syncTableName, cdcSource = globalCdc)
                verifyEmptyQuery(syncTableName, listOf(1000L), listOf(9000L), queryService = globalQueryService)
            }
        }

        "internal=SYNC overrides global=ASYNC - mutations are written synchronously" {
            val globalAsyncGraph =
                GraphFixtures.create(
                    configBuilder = GraphConfig.Builder().withGlobalMutationMode(MutationMode.ASYNC),
                    withTestData = false,
                )
            globalAsyncGraph.use { graph ->
                val globalWal = graph.wal as InMemoryWal
                val globalCdc = graph.cdc as InMemoryCdc

                val database = GraphFixtures.serviceName
                val table = "sync_edge_for_global_test"
                val syncTableName = EntityName(database, table)

                graph.serviceDdl
                    .create(EntityName.fromOrigin(database), ServiceCreateRequest(desc = "test service"))
                    .block()
                graph.labelDdl
                    .create(
                        syncTableName,
                        mapper.readValue<LabelCreateRequest>(syncEdgeDescriptor),
                    ).block()

                val globalMutationService = V3MutationService(graph)
                val globalQueryService = V3QueryService(graph)
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

                globalMutationService
                    .internalMutateEdge(database, table, request, internal = MutationMode.SYNC)
                    .test()
                    .assertNext {
                        mapper.writeValueAsString(it) shouldBe """{"results":[{"source":1000,"target":9000,"status":"CREATED","count":1}]}"""
                    }.verifyComplete()

                verifyWal(syncTableName, 1, queue = false, tableMode = MutationMode.SYNC, globalMode = MutationMode.ASYNC, internalMode = MutationMode.SYNC, walSource = globalWal)
                verifyCdc(syncTableName, 1, cdcSource = globalCdc)

                globalQueryService
                    .gets(database, table, listOf(1000L), listOf(9000L))
                    .test()
                    .assertNext { it.edges.size shouldBe 1 }
                    .verifyComplete()
            }
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
    }
}
