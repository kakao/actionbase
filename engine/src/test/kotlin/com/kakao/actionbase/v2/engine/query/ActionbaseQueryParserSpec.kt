package com.kakao.actionbase.v2.engine.query

import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.engine.query.ActionbaseQuery.Companion.toJson
import com.kakao.actionbase.v2.engine.sql.WherePredicate

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ActionbaseQueryParserSpec :
    StringSpec({

        "deserialize ActionbaseQuery" {
            val json =
                """
                {
                  "query": [
                    {
                      "type": "SCAN",
                      "name": "a",
                      "service": "{service}",
                      "label": "{label}",
                      "src": {
                        "type": "VALUE",
                        "value": [1]
                      },
                      "dir": "OUT",
                      "index": "created_at_desc",
                      "limit": 100,
                      "predicates": [
                        {
                          "type": "=",
                          "key": "name",
                          "value": "Alice"
                        }
                      ]
                    },
                    {
                      "type": "GET",
                      "name": "b",
                      "service": "{service}",
                      "label": "{label}",
                      "src": {
                        "type": "REF",
                        "ref": "a",
                        "field": "tgt"
                      },
                      "tgt": {
                        "type": "VALUE",
                        "value": [1]
                      }
                    },
                    {
                      "type": "COUNT",
                      "name": "d",
                      "include": true,
                      "service": "{service}",
                      "label": "{label}",
                      "src": {
                        "type": "VALUE",
                        "value": [1]
                      },
                      "dir": "OUT"
                    },
                    {
                      "type": "SELF",
                      "name": "e",
                      "service": "{service}",
                      "label": "{label}",
                      "src": {
                          "type": "VALUE",
                          "value": [1, 2, 3]
                      }
                    },
                    {
                      "type": "CACHE",
                      "name": "f",
                      "service": "{service}",
                      "label": "{label}",
                      "src": {
                        "type": "REF",
                        "ref": "a",
                        "field": "tgt"
                      },
                      "dir": "OUT",
                      "cacheName": "recent_wishlist",
                      "limit": 10,
                      "include": true
                    }
                  ]
                }
                """.trimIndent()

            val actionBaseQuery = ActionbaseQuery.from(json)

            actionBaseQuery.query.size shouldBe 5
            actionBaseQuery.query[0] shouldBe
                ActionbaseQuery.Item.Scan(
                    name = "a",
                    service = "{service}",
                    label = "{label}",
                    src = ActionbaseQuery.Vertex.Value(listOf(1)),
                    dir = Direction.OUT,
                    index = "created_at_desc",
                    limit = 100,
                    predicates = listOf(WherePredicate.Eq("name", "Alice")),
                    include = false,
                )
            actionBaseQuery.query[1] shouldBe
                ActionbaseQuery.Item.Get(
                    name = "b",
                    service = "{service}",
                    label = "{label}",
                    src = ActionbaseQuery.Vertex.Ref("a", "tgt"),
                    tgt = ActionbaseQuery.Vertex.Value(listOf(1)),
                    include = false,
                )
            actionBaseQuery.query[2] shouldBe
                ActionbaseQuery.Item.Count(
                    name = "d",
                    service = "{service}",
                    label = "{label}",
                    src = ActionbaseQuery.Vertex.Value(listOf(1)),
                    dir = Direction.OUT,
                    include = true,
                )
            actionBaseQuery.query[3] shouldBe
                ActionbaseQuery.Item.Self(
                    name = "e",
                    service = "{service}",
                    label = "{label}",
                    src = ActionbaseQuery.Vertex.Value(listOf(1, 2, 3)),
                    include = false,
                )
            actionBaseQuery.query[4] shouldBe
                ActionbaseQuery.Item.Cache(
                    name = "f",
                    service = "{service}",
                    label = "{label}",
                    src = ActionbaseQuery.Vertex.Ref("a", "tgt"),
                    dir = Direction.OUT,
                    cacheName = "recent_wishlist",
                    limit = 10,
                    include = true,
                )

            // serialize and deserialize

            val jsonString = actionBaseQuery.toJson()
            val actionbaseQuery2 = ActionbaseQuery.from(jsonString)

            actionBaseQuery shouldBe actionbaseQuery2
        }
    })
