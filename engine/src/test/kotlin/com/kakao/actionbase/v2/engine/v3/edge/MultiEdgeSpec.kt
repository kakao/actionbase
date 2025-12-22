package com.kakao.actionbase.v2.engine.v3.edge

import com.kakao.actionbase.core.edge.payload.MultiEdgeBulkMutationRequest
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.label.EdgeOperationStatus
import com.kakao.actionbase.v2.engine.service.ddl.LabelCreateRequest
import com.kakao.actionbase.v2.engine.test.GraphFixtures
import com.kakao.actionbase.v2.engine.test.cdc.InMemoryCdc
import com.kakao.actionbase.v2.engine.v3.V3MutationService
import com.kakao.actionbase.v2.engine.v3.V3QueryService

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import reactor.kotlin.test.test
import reactor.test.StepVerifier

class MultiEdgeSpec :
    StringSpec({

        val keyEdgeLabelName = EntityName(GraphFixtures.serviceName, "key_edge_label")
        val database = keyEdgeLabelName.service
        val table = keyEdgeLabelName.nameNotNull

        lateinit var graph: Graph
        lateinit var cdc: InMemoryCdc
        lateinit var v3MutationService: V3MutationService
        lateinit var v3QueryService: V3QueryService

        val labelDefinition =
            """
            {
              "desc": "gift sent",
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
              "mode": "SYNC"
            }
            """.trimIndent()

        beforeTest {
            graph = GraphFixtures.create()
            cdc = graph.cdc as InMemoryCdc
            val request = mapper.readValue<LabelCreateRequest>(labelDefinition)
            graph.labelDdl.create(keyEdgeLabelName, request).block()
            v3MutationService = V3MutationService(graph)
            v3QueryService = V3QueryService(graph)
        }

        afterTest {
            graph.close()
            cdc.init()
        }

        "mutate using v3 API should be processed even if the label is readOnly" {
            fun doStep1() {
                val requestAsString =
                    """
                    {
                      "mutations": [
                        {
                          "type": "INSERT",
                          "edge": { "version": 1234567890, "id": 100000, "source": 1, "target": 2, "properties": { "paidAt": 1234567890, "productId": 200 } }
                        },
                        {
                          "type": "INSERT",
                          "edge": { "version": 1234567890, "id": 100001, "source": 1, "target": 2, "properties": { "paidAt": 1234567890, "productId": 201 } }
                        },
                        {
                          "type": "INSERT",
                          "edge": { "version": 1234567892, "id": 100002, "source": 1, "target": 0, "properties": { "paidAt": 1234567892, "productId": 202 } }
                        },
                        {
                          "type": "INSERT",
                          "edge": { "version": 1234567893, "id": 100003, "source": 0, "target": 0, "properties": { "paidAt": 1234567893, "productId": 203 } }
                        }
                      ]
                    }
                    """.trimIndent()
                val request = mapper.readValue<MultiEdgeBulkMutationRequest>(requestAsString)

                v3MutationService
                    .mutateMultiEdge(database, table, request)
                    .test()
                    .assertNext { result ->
                        val expected =
                            """{"results":[{"id":100000,"status":"CREATED","count":1},{"id":100001,"status":"CREATED","count":1},{"id":100002,"status":"CREATED","count":1},{"id":100003,"status":"CREATED","count":1}]}"""
                        mapper.writeValueAsString(result) shouldBe expected
                    }.verifyComplete()

                // same as EdgeState
                v3QueryService
                    .gets(database, table, listOf(100000L), listOf(100000L))
                    .test()
                    .assertNext { result ->
                        val expected =
                            """
                            {
                              "edges" : [ {
                                "version" : 1234567890,
                                "source" : 1,
                                "target" : 2,
                                "properties" : {
                                  "_id" : 100000,
                                  "paidAt" : 1234567890,
                                  "productId" : 200
                                },
                                "context" : { }
                              } ],
                              "count" : 1,
                              "total" : 1,
                              "offset" : null,
                              "hasNext" : false,
                              "context" : { }
                            }
                            """.trimIndent()
                        result.normalize() shouldBe expected.normalize()
                        result.edges.size shouldBe 1
                    }.verifyComplete()

                v3QueryService
                    .scan(database, table, "paid_at_desc", 100000L, Direction.OUT, limit = 10)
                    .test()
                    .assertNext { result ->
                        result.edges.size shouldBe 0
                    }.verifyComplete()

                // but indexes are made based on multiEdge
                v3QueryService
                    .count(database, table, 1, Direction.OUT)
                    .test()
                    .assertNext { result ->
                        val expected =
                            """
                            {
                              "start" : 1,
                              "direction" : "OUT",
                              "count" : 3,
                              "context" : { }
                            }
                            """.trimIndent()
                        result.normalize() shouldBe expected.normalize()
                        result.count shouldBe 3L
                    }.verifyComplete()

                v3QueryService
                    .count(database, table, 2, Direction.IN)
                    .test()
                    .assertNext { result ->
                        val expected =
                            """
                            {
                              "start" : 2,
                              "direction" : "IN",
                              "count" : 2,
                              "context" : { }
                            }
                            """.trimIndent()
                        result.normalize() shouldBe expected.normalize()
                        result.count shouldBe 2L
                    }.verifyComplete()

                v3QueryService
                    .scan(database, table, "paid_at_desc", 1, Direction.OUT, limit = 10)
                    .test()
                    .assertNext { result ->
                        val expectedAsString =
                            """
                            {
                              "edges": [
                                {
                                  "version": 1234567892,
                                  "source": 1,
                                  "target": 0,
                                  "properties": {
                                    "_id": 100002,
                                    "paidAt": 1234567892,
                                    "productId": 202
                                  },
                                  "context": {}
                                },
                                {
                                  "version": 1234567890,
                                  "source": 1,
                                  "target": 2,
                                  "properties": {
                                    "_id": 100000,
                                    "paidAt": 1234567890,
                                    "productId": 200
                                  },
                                  "context": {}
                                },
                                {
                                  "version": 1234567890,
                                  "source": 1,
                                  "target": 2,
                                  "properties": {
                                    "_id": 100001,
                                    "paidAt": 1234567890,
                                    "productId": 201
                                  },
                                  "context": {}
                                }
                              ],
                              "count": 3,
                              "total": -1,
                              "offset": null,
                              "hasNext": false,
                              "context": {}
                            }
                            """.trimIndent()
                        val expected = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(expectedAsString))
                        val actual = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result)
                        actual shouldBe expected
                        result.edges.size shouldBe 3
                    }.verifyComplete()

                v3QueryService
                    .scan(database, table, "paid_at_desc", 2, Direction.IN, limit = 10)
                    .test()
                    .assertNext { result ->
                        val expected =
                            """
                            {
                              "edges" : [ {
                                "version" : 1234567890,
                                "source" : 1,
                                "target" : 2,
                                "properties" : {
                                  "_id" : 100000,
                                  "paidAt" : 1234567890,
                                  "productId" : 200
                                },
                                "context" : { }
                              }, {
                                "version" : 1234567890,
                                "source" : 1,
                                "target" : 2,
                                "properties" : {
                                  "_id" : 100001,
                                  "paidAt" : 1234567890,
                                  "productId" : 201
                                },
                                "context" : { }
                              } ],
                              "count" : 2,
                              "total" : -1,
                              "offset" : null,
                              "hasNext" : false,
                              "context" : { }
                            }
                            """.trimIndent()
                        result.normalize() shouldBe expected.normalize()
                        result.edges.size shouldBe 2
                    }.verifyComplete()
            }

            fun doStep2() {
                val requestAsString =
                    """
                    {
                      "mutations": [
                        {
                          "type": "DELETE",
                          "edge": { "version": 1234567894, "id": 100001 }
                        },
                        {
                          "type": "UPDATE",
                          "edge": { "version": 1234567895, "id": 100002, "target": 2 }
                        },
                        {
                          "type": "UPDATE",
                          "edge": { "version": 1234567896, "id": 100003, "source": 1, "target": 3 }
                        },
                        {
                          "type": "DELETE",
                          "edge": { "version": 1234567897, "id": 900001 }
                        }
                      ]
                    }
                    """.trimIndent()
                val request = mapper.readValue<MultiEdgeBulkMutationRequest>(requestAsString)

                v3MutationService
                    .mutateMultiEdge(database, table, request)
                    .test()
                    .assertNext { result ->
                        val expected =
                            """{"results":[{"id":100001,"status":"DELETED","count":1},{"id":100002,"status":"UPDATED","count":1},{"id":100003,"status":"UPDATED","count":1},{"id":900001,"status":"IDLE","count":1}]}"""
                        mapper.writeValueAsString(result) shouldBe expected
                    }.verifyComplete()

                // same as EdgeState
                v3QueryService
                    .gets(database, table, listOf(100000L), listOf(100000L))
                    .test()
                    .assertNext { result ->
                        val expected =
                            """
                            {
                              "edges" : [ {
                                "version" : 1234567890,
                                "source" : 1,
                                "target" : 2,
                                "properties" : {
                                  "_id" : 100000,
                                  "paidAt" : 1234567890,
                                  "productId" : 200
                                },
                                "context" : { }
                              } ],
                              "count" : 1,
                              "total" : 1,
                              "offset" : null,
                              "hasNext" : false,
                              "context" : { }
                            }
                            """.trimIndent()
                        result.normalize() shouldBe expected.normalize()
                        result.edges.size shouldBe 1
                    }.verifyComplete()

                v3QueryService
                    .scan(database, table, "paid_at_desc", 100000L, Direction.OUT, limit = 10)
                    .test()
                    .assertNext { result ->
                        result.edges.size shouldBe 0
                    }.verifyComplete()

                // but indexes are made based on multiEdge
                v3QueryService
                    .count(database, table, 1, Direction.OUT)
                    .test()
                    .assertNext { result ->
                        val expected =
                            """
                            {
                              "start" : 1,
                              "direction" : "OUT",
                              "count" : 3,
                              "context" : { }
                            }
                            """.trimIndent()
                        result.normalize() shouldBe expected.normalize()
                        result.count shouldBe 3L
                    }.verifyComplete()

                v3QueryService
                    .count(database, table, 2, Direction.IN)
                    .test()
                    .assertNext { result ->
                        val expected =
                            """
                            {
                              "start" : 2,
                              "direction" : "IN",
                              "count" : 2,
                              "context" : { }
                            }
                            """.trimIndent()
                        result.normalize() shouldBe expected.normalize()
                        result.count shouldBe 2L
                    }.verifyComplete()

                v3QueryService
                    .count(database, table, 3, Direction.IN)
                    .test()
                    .assertNext { result ->
                        val expected =
                            """
                            {
                              "start" : 3,
                              "direction" : "IN",
                              "count" : 1,
                              "context" : { }
                            }
                            """.trimIndent()
                        result.normalize() shouldBe expected.normalize()
                        result.count shouldBe 1L
                    }.verifyComplete()

                v3QueryService
                    .scan(database, table, "paid_at_desc", 1, Direction.OUT, limit = 10)
                    .test()
                    .assertNext { result ->
                        val expected =
                            """
                            {
                              "edges" : [ {
                                "version" : 1234567896,
                                "source" : 1,
                                "target" : 3,
                                "properties" : {
                                  "_id" : 100003,
                                  "paidAt" : 1234567893,
                                  "productId" : 203
                                },
                                "context" : { }
                              }, {
                                "version" : 1234567895,
                                "source" : 1,
                                "target" : 2,
                                "properties" : {
                                  "_id" : 100002,
                                  "paidAt" : 1234567892,
                                  "productId" : 202
                                },
                                "context" : { }
                              }, {
                                "version" : 1234567890,
                                "source" : 1,
                                "target" : 2,
                                "properties" : {
                                  "_id" : 100000,
                                  "paidAt" : 1234567890,
                                  "productId" : 200
                                },
                                "context" : { }
                              } ],
                              "count" : 3,
                              "total" : -1,
                              "offset" : null,
                              "hasNext" : false,
                              "context" : { }
                            }
                            """.trimIndent()
                        result.normalize() shouldBe expected.normalize()
                        result.edges.size shouldBe 3
                    }.verifyComplete()

                v3QueryService
                    .scan(database, table, "paid_at_desc", 2, Direction.IN, limit = 10)
                    .test()
                    .assertNext { result ->
                        val expected =
                            """
                            {
                              "edges" : [ {
                                "version" : 1234567895,
                                "source" : 1,
                                "target" : 2,
                                "properties" : {
                                  "_id" : 100002,
                                  "paidAt" : 1234567892,
                                  "productId" : 202
                                },
                                "context" : { }
                              }, {
                                "version" : 1234567890,
                                "source" : 1,
                                "target" : 2,
                                "properties" : {
                                  "_id" : 100000,
                                  "paidAt" : 1234567890,
                                  "productId" : 200
                                },
                                "context" : { }
                              } ],
                              "count" : 2,
                              "total" : -1,
                              "offset" : null,
                              "hasNext" : false,
                              "context" : { }
                            }
                            """.trimIndent()
                        result.normalize() shouldBe expected.normalize()
                        result.edges.size shouldBe 2
                    }.verifyComplete()

                v3QueryService
                    .scan(database, table, "paid_at_desc", 3, Direction.IN, limit = 10)
                    .test()
                    .assertNext { result ->
                        val expected =
                            """
                            {
                              "edges" : [ {
                                "version" : 1234567896,
                                "source" : 1,
                                "target" : 3,
                                "properties" : {
                                  "_id" : 100003,
                                  "paidAt" : 1234567893,
                                  "productId" : 203
                                },
                                "context" : { }
                              } ],
                              "count" : 1,
                              "total" : -1,
                              "offset" : null,
                              "hasNext" : false,
                              "context" : { }
                            }
                            """.trimIndent()
                        result.normalize() shouldBe expected.normalize()
                        result.edges.size shouldBe 1
                    }.verifyComplete()
            }

            doStep1()
            doStep2()
        }

        "when the status of edge is IDLE, the source and target of cdc should have a value of the latest record" {
            val edges =
                listOf(
                    """
                    {
                      "mutations": [
                        {
                          "type": "INSERT",
                          "edge": { "version": 1000000000, "id": 100000, "source": 1, "target": 1, "properties": { "paidAt": 1234567890, "productId": 200 } }
                        }
                      ]
                    }
                    """.trimIndent(),
                    """
                    {
                      "mutations": [
                        {
                          "type": "INSERT",
                          "edge": { "version": 1000000001, "id": 100000, "source": 1, "target": 2, "properties": { "paidAt": 1234567890, "productId": 200 } }
                        }
                      ]
                    }
                    """.trimIndent(),
                    """
                    {
                      "mutations": [
                        {
                          "type": "INSERT",
                          "edge": { "version": 1000000001, "id": 100000, "source": 1, "target": 2, "properties": { "paidAt": 1234567890, "productId": 200 } }
                        }
                      ]
                    }
                    """.trimIndent(),
                    """
                    {
                      "mutations": [
                        {
                          "type": "INSERT",
                          "edge": { "version": 1000000000, "id": 100000, "source": 1, "target": 1, "properties": { "paidAt": 1234567890, "productId": 200 } }
                        }
                      ]
                    }
                    """.trimIndent(),
                )

            edges
                .forEach {
                    StepVerifier
                        .create(
                            v3MutationService
                                .mutateMultiEdge(
                                    database,
                                    table,
                                    request = mapper.readValue<MultiEdgeBulkMutationRequest>(it),
                                ),
                        ).expectNextCount(1)
                        .verifyComplete()
                }

            val actual = cdc.readCdc().filter { it.label == keyEdgeLabelName }

            actual[0].status shouldBe EdgeOperationStatus.CREATED
            actual[1].status shouldBe EdgeOperationStatus.UPDATED
            actual[2].status shouldBe EdgeOperationStatus.IDLE
            actual[3].status shouldBe EdgeOperationStatus.IDLE

            val actualLastCdc = actual.last()

            actualLastCdc.before?.src shouldBe 1
            actualLastCdc.before?.tgt shouldBe 2
            actualLastCdc.after?.src shouldBe 1
            actualLastCdc.after?.tgt shouldBe 2

            actualLastCdc.before?.props?.get("_source") shouldBe 1
            actualLastCdc.before?.props?.get("_target") shouldBe 2

            actualLastCdc.after?.props?.get("_source") shouldBe 1
            actualLastCdc.after?.props?.get("_target") shouldBe 2
        }
    }) {
    companion object {
        private val mapper = jacksonObjectMapper()

        fun Any.normalize(): String =
            if (this is String) {
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(this))
            } else {
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this)
            }
    }
}
