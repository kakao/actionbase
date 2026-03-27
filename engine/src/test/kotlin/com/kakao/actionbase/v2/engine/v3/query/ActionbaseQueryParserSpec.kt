package com.kakao.actionbase.v2.engine.v3.query

import com.kakao.actionbase.engine.query.ActionbaseQuery
import com.kakao.actionbase.engine.query.ActionbaseQuery.Companion.toJson
import com.kakao.actionbase.v2.core.metadata.Direction
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
                      "database": "{database}",
                      "table": "{table}",
                      "source": {
                        "type": "VALUE",
                        "value": [1]
                      },
                      "direction": "OUT",
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
                      "database": "{database}",
                      "table": "{table}",
                      "source": {
                        "type": "REF",
                        "ref": "a",
                        "field": "tgt"
                      },
                      "target": {
                        "type": "VALUE",
                        "value": [1]
                      }
                    },
                    {
                      "type": "COUNT",
                      "name": "d",
                      "include": true,
                      "database": "{database}",
                      "table": "{table}",
                      "source": {
                        "type": "VALUE",
                        "value": [1]
                      },
                      "direction": "OUT"
                    },
                    {
                      "type": "SELF",
                      "name": "e",
                      "database": "{database}",
                      "table": "{table}",
                      "source": {
                          "type": "VALUE",
                          "value": [1, 2, 3]
                      }
                    },
                    {
                      "type": "CACHE",
                      "name": "f",
                      "database": "{database}",
                      "table": "{table}",
                      "source": {
                        "type": "REF",
                        "ref": "a",
                        "field": "tgt"
                      },
                      "direction": "OUT",
                      "cache": "recent_wishlist",
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
                    database = "{database}",
                    table = "{table}",
                    source = ActionbaseQuery.Vertex.Value(listOf(1)),
                    direction = Direction.OUT,
                    index = "created_at_desc",
                    limit = 100,
                    predicates = listOf(WherePredicate.Eq("name", "Alice")),
                    include = false,
                )
            actionBaseQuery.query[1] shouldBe
                ActionbaseQuery.Item.Get(
                    name = "b",
                    database = "{database}",
                    table = "{table}",
                    source = ActionbaseQuery.Vertex.Ref("a", "tgt"),
                    target = ActionbaseQuery.Vertex.Value(listOf(1)),
                    include = false,
                )
            actionBaseQuery.query[2] shouldBe
                ActionbaseQuery.Item.Count(
                    name = "d",
                    database = "{database}",
                    table = "{table}",
                    source = ActionbaseQuery.Vertex.Value(listOf(1)),
                    direction = Direction.OUT,
                    include = true,
                )
            actionBaseQuery.query[3] shouldBe
                ActionbaseQuery.Item.Self(
                    name = "e",
                    database = "{database}",
                    table = "{table}",
                    source = ActionbaseQuery.Vertex.Value(listOf(1, 2, 3)),
                    include = false,
                )
            actionBaseQuery.query[4] shouldBe
                ActionbaseQuery.Item.Cache(
                    name = "f",
                    database = "{database}",
                    table = "{table}",
                    source = ActionbaseQuery.Vertex.Ref("a", "tgt"),
                    direction = Direction.OUT,
                    cache = "recent_wishlist",
                    limit = 10,
                    include = true,
                )

            // serialize and deserialize

            val jsonString = actionBaseQuery.toJson()
            val actionbaseQuery2 = ActionbaseQuery.from(jsonString)

            actionBaseQuery shouldBe actionbaseQuery2
        }
    })
