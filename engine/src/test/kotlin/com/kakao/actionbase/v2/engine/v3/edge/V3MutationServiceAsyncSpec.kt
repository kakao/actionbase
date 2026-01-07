package com.kakao.actionbase.v2.engine.v3.edge

import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest
import com.kakao.actionbase.core.edge.payload.MultiEdgeBulkMutationRequest
import com.kakao.actionbase.v2.core.metadata.MutationMode
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.service.ddl.LabelCreateRequest
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
            requestMode: MutationMode?,
        ) {
            val walActual = wal.readWal().filter { it.label == tableName }
            walActual.size shouldBe expectedSize
            walActual.all { it.mode.queue == queue } shouldBe true
            walActual.all { it.mode.l == MutationMode.ASYNC } shouldBe true
            walActual.all { it.mode.r == requestMode } shouldBe true
        }

        fun verifyCdc(
            tableName: EntityName,
            expectedSize: Int = 0,
        ) {
            val cdcActual = cdc.readCdc().filter { it.label == tableName }
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
        ) {
            v3QueryService
                .gets(tableName.service, tableName.nameNotNull, sources, targets)
                .test()
                .assertNext { it.edges.size shouldBe 0 }
                .verifyComplete()
        }

        "ASYNC MULTI_EDGE table with sync request produces WAL and CDC" {
            val request = mapper.readValue<MultiEdgeBulkMutationRequest>(multiEdgeRequestString)

            v3MutationService
                .mutateMultiEdge(multiEdgeTableName.service, multiEdgeTableName.nameNotNull, request, sync = MutationMode.SYNC)
                .test()
                .assertNext {
                    mapper.writeValueAsString(it) shouldBe """{"results":[{"id":100000,"status":"CREATED","count":1},{"id":100001,"status":"CREATED","count":1},{"id":100002,"status":"CREATED","count":1}]}"""
                }.verifyComplete()

            verifyWal(multiEdgeTableName, 3, queue = false, MutationMode.SYNC)
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

            verifyWal(multiEdgeTableName, 3, queue = true, null)
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

            verifyWal(edgeTableName, 3, queue = true, null)
            verifyCdc(edgeTableName)
            verifyEmptyQuery(edgeTableName, listOf(1L), listOf(0L))
        }

        "ASYNC EDGE table with sync request produces WAL and CDC" {
            val request = mapper.readValue<EdgeBulkMutationRequest>(edgeRequestString)

            v3MutationService
                .mutateEdge(edgeTableName.service, edgeTableName.nameNotNull, request, sync = MutationMode.SYNC)
                .test()
                .assertNext {
                    mapper.writeValueAsString(it) shouldBe """{"results":[{"source":1,"target":0,"status":"CREATED","count":1},{"source":1,"target":2,"status":"CREATED","count":2}]}"""
                }.verifyComplete()

            verifyWal(edgeTableName, 3, queue = false, MutationMode.SYNC)
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
    }) {
    companion object {
        private val mapper = jacksonObjectMapper()
    }
}
