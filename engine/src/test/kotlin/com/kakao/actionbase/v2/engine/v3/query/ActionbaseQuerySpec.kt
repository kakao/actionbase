package com.kakao.actionbase.v2.engine.v3.query

import com.kakao.actionbase.engine.query.ActionbaseQuery
import com.kakao.actionbase.engine.query.ActionbaseQueryExecutor
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.label.Label
import com.kakao.actionbase.v2.engine.test.GraphFixtures
import com.kakao.actionbase.v2.engine.v3.V2BackedEngine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import reactor.kotlin.test.test

class ActionbaseQuerySpec :
    StringSpec({

        lateinit var graph: Graph
        lateinit var queryExecutor: ActionbaseQueryExecutor

        lateinit var hbase: Label

        beforeSpec {
            graph = GraphFixtures.create()
            queryExecutor = ActionbaseQueryExecutor(V2BackedEngine(graph))
            hbase = graph.getLabel(EntityName(GraphFixtures.serviceName, GraphFixtures.hbaseIndexed))
        }

        "ActionbaseQuery.Item.Self" {
            val database = hbase.name.service
            val table = hbase.name.nameNotNull
            val queryName = "get_query_result"

            // language=json
            val actionBaseQueryString =
                """
                {
                  "query": [
                    {
                      "type": "SELF",
                      "name": "$queryName",
                      "database": "$database",
                      "table": "$table",
                      "source": {
                        "type": "VALUE",
                        "value": [100]
                      },
                      "include": true
                    }
                  ]
                }
                """.trimIndent()

            val actionBaseQuery = ActionbaseQuery.from(actionBaseQueryString)

            queryExecutor
                .query(actionBaseQuery)
                .test()
                .assertNext {
                    it.size shouldBe 1
                    it.keys shouldBe setOf(queryName)
                    val df = it[queryName]!!
                    df.rows.size shouldBe 0
                }.verifyComplete()
        }

        "ActionbaseQuery.Item.Get" {
            val database = hbase.name.service
            val table = hbase.name.nameNotNull
            val queryName = "get_query_result"

            // language=json
            val actionBaseQueryString =
                """
                {
                  "query": [
                    {
                      "type": "GET",
                      "name": "$queryName",
                      "database": "$database",
                      "table": "$table",
                      "source": {
                        "type": "VALUE",
                        "value": [100]
                      },
                      "target": {
                        "type": "VALUE",
                        "value": [1000, 1001]
                      },
                      "include": true
                    }
                  ]
                }
                """.trimIndent()
            val actionBaseQuery = ActionbaseQuery.from(actionBaseQueryString)

            queryExecutor
                .query(actionBaseQuery)
                .test()
                .assertNext {
                    it.size shouldBe 1
                    it.keys shouldBe setOf(queryName)
                    val df = it[queryName]!!
                    df.rows.map { row -> row.data["permission"] } shouldContainExactly listOf("na", "others")
                }.verifyComplete()
        }

        "ActionbaseQuery.Item.Count" {
            val database = hbase.name.service
            val table = hbase.name.nameNotNull
            val queryName = "get_query_result"

            // language=json
            val actionBaseQueryString =
                """
                {
                  "query": [
                    {
                      "type": "COUNT",
                      "name": "$queryName",
                      "database": "$database",
                      "table": "$table",
                      "source": {
                        "type": "VALUE",
                        "value": [100]
                      },
                      "direction": "OUT",
                      "include": true
                    }
                  ]
                }
                """.trimIndent()
            val actionBaseQuery = ActionbaseQuery.from(actionBaseQueryString)

            queryExecutor
                .query(actionBaseQuery)
                .test()
                .assertNext {
                    it.size shouldBe 1
                    it.keys shouldBe setOf(queryName)
                    val df = it[queryName]!!
                    df.rows.map { row -> row.data["COUNT(1)"] } shouldContainExactly listOf(6L)
                }.verifyComplete()
        }

        "ActionbaseQuery.Item.Scan" {
            val database = hbase.name.service
            val table = hbase.name.nameNotNull
            val queryName = "get_query_result"

            // language=json
            val actionBaseQueryString =
                """
                {
                  "query": [
                    {
                      "type": "SCAN",
                      "name": "$queryName",
                      "database": "$database",
                      "table": "$table",
                      "source": {
                        "type": "VALUE",
                        "value": [100]
                      },
                      "direction": "OUT",
                      "index": "permission_created_at_desc",
                      "limit": 10,
                      "include": true
                    }
                  ]
                }
                """.trimIndent()
            val actionBaseQuery = ActionbaseQuery.from(actionBaseQueryString)

            queryExecutor
                .query(actionBaseQuery)
                .test()
                .assertNext {
                    it.size shouldBe 1
                    it.keys shouldBe setOf(queryName)
                    val df = it[queryName]!!
                    df.rows.map { row -> row.data["permission"] } shouldContainExactly
                        listOf(
                            "me",
                            "me",
                            "na",
                            "na",
                            "others",
                            "others",
                        )
                }.verifyComplete()
        }

        "Complex Query 1" {
            val database = hbase.name.service
            val table = hbase.name.nameNotNull

            val srcUser = 100

            // language=json
            val actionBaseQueryString =
                """
                {
                  "query": [
                    {
                      "type": "SCAN",
                      "name": "step1",
                      "database": "$database",
                      "table": "$table",
                      "source": {
                        "type": "VALUE",
                        "value": [$srcUser]
                      },
                      "direction": "OUT",
                      "index": "created_at_desc",
                      "limit": 10,
                      "include": true
                    },
                    {
                      "type": "SCAN",
                      "name": "step2",
                      "database": "$database",
                      "table": "$table",
                      "source": {
                        "type": "REF",
                        "ref": "step1",
                        "field": "target"
                      },
                      "direction": "IN",
                      "index": "created_at_desc",
                      "limit": 10,
                      "include": true
                    }
                  ]
                }
                """.trimIndent()
            val actionBaseQuery = ActionbaseQuery.from(actionBaseQueryString)

            queryExecutor
                .query(actionBaseQuery)
                .test()
                .assertNext {
                    it.size shouldBe 2
                    it.keys shouldBe setOf("step1", "step2")
                }.verifyComplete()
        }
    })
