package com.kakao.actionbase.core.metadata.common

import com.kakao.actionbase.core.java.codec.common.hbase.Order
import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest
import com.kakao.actionbase.test.json.PrettyObjectWriter

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

import org.junit.jupiter.api.Test

import com.fasterxml.jackson.module.kotlin.readValue

class EdgeSchemaSerializationTest {
    val prettyWriter = PrettyObjectWriter.DEFAULT

    val objectMapper = prettyWriter.objectMapper

    @Test
    fun `immutable edge rejects more than one index`() {
        val twoIndexes =
            listOf(
                Index(index = "by_ts", fields = listOf(IndexField(field = "ts", order = Order.ASC))),
                Index(index = "by_ts_desc", fields = listOf(IndexField(field = "ts", order = Order.DESC))),
            )
        val e =
            assertFailsWith<IllegalArgumentException> {
                ModelSchema.ImmutableEdge(
                    source = Field(type = com.kakao.actionbase.core.types.PrimitiveType.INT, comment = "partition"),
                    target = Field(type = com.kakao.actionbase.core.types.PrimitiveType.STRING, comment = "message id"),
                    properties = listOf(StructField(name = "ts", type = com.kakao.actionbase.core.types.PrimitiveType.LONG, comment = "ts", nullable = false)),
                    direction = DirectionType.OUT,
                    indexes = twoIndexes,
                )
            }
        assertTrue(e.message!!.contains("at most one index"), "message should explain the single-index rule, was: ${e.message}")
    }

    @Test
    fun `immutable edge rejects BOTH direction`() {
        val e =
            assertFailsWith<IllegalArgumentException> {
                ModelSchema.ImmutableEdge(
                    source = Field(type = com.kakao.actionbase.core.types.PrimitiveType.INT, comment = "partition"),
                    target = Field(type = com.kakao.actionbase.core.types.PrimitiveType.STRING, comment = "message id"),
                    properties = listOf(StructField(name = "ts", type = com.kakao.actionbase.core.types.PrimitiveType.LONG, comment = "ts", nullable = false)),
                    direction = DirectionType.BOTH,
                    indexes = listOf(Index(index = "by_ts", fields = listOf(IndexField(field = "ts", order = Order.ASC)))),
                )
            }
        assertTrue(e.message!!.contains("single-direction"), "message should explain the single-direction rule, was: ${e.message}")
    }

    @Test
    fun `immutable edge schema round-trips under the immutableEdge discriminator`() {
        val schema =
            ModelSchema.ImmutableEdge(
                source = Field(type = com.kakao.actionbase.core.types.PrimitiveType.INT, comment = "partition"),
                target = Field(type = com.kakao.actionbase.core.types.PrimitiveType.STRING, comment = "message id"),
                properties = listOf(StructField(name = "ts", type = com.kakao.actionbase.core.types.PrimitiveType.LONG, comment = "enqueue ts", nullable = false)),
                direction = DirectionType.OUT,
                indexes = listOf(Index(index = "by_ts", fields = listOf(IndexField(field = "ts", order = Order.ASC)))),
            )

        val json = objectMapper.writeValueAsString(schema)
        assertTrue(json.contains("\"immutableEdge\""), "discriminator must be immutableEdge, was: $json")
        assertTrue(!json.contains("caches"), "immutable edge schema must not carry a caches field")

        assertEquals(schema, objectMapper.readValue<ModelSchema>(json))
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - name: struct type
          input: |-
            {
              "type": "edge",
              "source": {"type": "long", "comment": "Source node ID"},
              "target": {"type": "long", "comment": "Target node ID"},
              "properties": [
                {
                  "name": "id",
                  "type": "long",
                  "comment": "Identifier",
                  "nullable": false
                },
                {
                  "name": "name",
                  "type": "string",
                  "comment": "name",
                  "nullable": false
                }
              ],
              "direction": "BOTH",
              "indexes": [
                {
                  "index": "updated_at_desc",
                  "fields": [{"field": "version", "order": "DESC"}],
                  "comment": "recent updates"
                }
              ],
              "groups": []
            }
          expected: {
              "type": "edge",
              "source": {"type": "long", "comment": "Source node ID"},
              "target": {"type": "long", "comment": "Target node ID"},
              "properties": [
                {"name": "id", "type": "long", "comment": "Identifier", "nullable": false},
                {"name": "name", "type": "string", "comment": "name", "nullable": false}
              ],
              "direction": "BOTH",
              "groups": [],
              "indexes": [
                {
                  "index": "updated_at_desc",
                  "fields": [{"field": "version", "order": "DESC"}],
                  "comment": "recent updates"
                }
              ]
            }
        - name: with caches
          input: |-
            {
              "type": "edge",
              "source": {"type": "long", "comment": "Source node ID"},
              "target": {"type": "long", "comment": "Target node ID"},
              "properties": [],
              "direction": "OUT",
              "indexes": [
                {
                  "index": "created_at_desc",
                  "fields": [{"field": "version", "order": "DESC"}]
                }
              ],
              "groups": [],
              "caches": [
                {
                  "cache": "created_at_desc",
                  "fields": [{"field": "version", "order": "DESC"}],
                  "limit": 1
                }
              ]
            }
          expected: {
              "type": "edge",
              "source": {"type": "long", "comment": "Source node ID"},
              "target": {"type": "long", "comment": "Target node ID"},
              "properties": [],
              "direction": "OUT",
              "indexes": [
                {
                  "index": "created_at_desc",
                  "fields": [{"field": "version", "order": "DESC"}]
                }
              ],
              "caches": [
                {
                  "cache": "created_at_desc",
                  "fields": [{"field": "version", "order": "DESC"}],
                  "limit": 1
                }
              ]
            }
        - name: with aggregations (per-entity with window)
          input: |-
            {
              "type": "MULTI_EDGE",
              "id": {"type": "long", "comment": ""},
              "source": {"type": "long", "comment": "user id"},
              "target": {"type": "long", "comment": "item id"},
              "properties": [],
              "direction": "BOTH",
              "groups": [
                {
                  "group": "purchased_count_1y",
                  "type": "COUNT",
                  "fields": [
                    {"name": "_target"},
                    {"name": "day", "bucket": {"type": "date", "name": "time", "unit": "MILLISECOND", "timezone": "+09:00", "format": "yyyy-MM-dd"}}
                  ],
                  "directionType": "OUT",
                  "aggregations": {
                    "topk": [
                      {
                        "topk": "top_purchased_1y",
                        "entity": "source",
                        "ranges": "time:bt:now-365d,now",
                        "dimension": "target",
                        "refreshAfterMillis": 31536000000,
                        "rank": "commerce.order_product__topk"
                      }
                    ]
                  }
                }
              ]
            }
          expected: {
              "type": "multiEdge",
              "id": {"type": "long", "comment": ""},
              "source": {"type": "long", "comment": "user id"},
              "target": {"type": "long", "comment": "item id"},
              "properties": [],
              "direction": "BOTH",
              "groups": [
                {
                  "group": "purchased_count_1y",
                  "type": "COUNT",
                  "fields": [
                    {"name": "_target"},
                    {"name": "day", "bucket": {"type": "date", "name": "time", "unit": "MILLISECOND", "timezone": "+09:00", "format": "yyyy-MM-dd"}}
                  ],
                  "directionType": "OUT",
                  "aggregations": {
                    "topk": [
                      {
                        "topk": "top_purchased_1y",
                        "entity": "source",
                        "ranges": "time:bt:now-365d,now",
                        "dimension": "target",
                        "refreshAfterMillis": 31536000000,
                        "rank": "commerce.order_product__topk"
                      }
                    ]
                  }
                }
              ]
            }
        - name: with aggregations (no ranges)
          input: |-
            {
              "type": "EDGE",
              "source": {"type": "long", "comment": ""},
              "target": {"type": "long", "comment": ""},
              "properties": [],
              "direction": "OUT",
              "groups": [
                {
                  "group": "purchased_count",
                  "type": "COUNT",
                  "fields": [{"name": "_target"}],
                  "aggregations": {
                    "topk": [{"topk": "top_purchased", "entity": "__GLOBAL__", "dimension": "target", "rank": "commerce.order_product__topk"}]
                  }
                }
              ]
            }
          expected: {
              "type": "edge",
              "source": {"type": "long", "comment": ""},
              "target": {"type": "long", "comment": ""},
              "properties": [],
              "direction": "OUT",
              "groups": [
                {
                  "group": "purchased_count",
                  "type": "COUNT",
                  "fields": [{"name": "_target"}],
                  "aggregations": {
                    "topk": [{"topk": "top_purchased", "entity": "__GLOBAL__", "dimension": "target", "rank": "commerce.order_product__topk"}]
                  }
                }
              ]
            }
        """,
    )
    fun `deserializes edge schema from JSON`(
        name: String,
        input: String,
        expected: ModelSchema,
    ) {
        assertEquals(expected, objectMapper.readValue<ModelSchema>(input))
    }

    @Test
    fun `a top-K refreshes onto the queue it names, and onto the shared one when it names none`() {
        val named =
            objectMapper.readValue<Topk>(
                """
                {"topk": "top_purchased", "entity": "source", "dimension": "target",
                 "rank": "commerce.order_product__topk", "refreshQueue": "purchase_refresh"}
                """.trimIndent(),
            )
        assertEquals("purchase_refresh", named.refreshQueue)
        assertEquals(named, objectMapper.readValue<Topk>(objectMapper.writeValueAsString(named)))

        val unnamed =
            objectMapper.readValue<Topk>(
                """
                {"topk": "top_purchased", "entity": "source", "dimension": "target",
                 "rank": "commerce.order_product__topk"}
                """.trimIndent(),
            )
        assertEquals(AggregationConstants.Topk.REFRESH_QUEUE, unnamed.refreshQueue)
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - schema: {
              "type": "edge",
              "source": {"type": "long", "comment": "Source node ID"},
              "target": {"type": "long", "comment": "Target node ID"},
              "properties": [
                {"name": "id", "type": "long", "comment": "Identifier", "nullable": false},
                {"name": "name", "type": "string", "comment": "name", "nullable": false}
              ],
              "direction": "BOTH",
              "groups": [],
              "indexes": [
                {
                  "index": "updated_at_desc",
                  "fields": [{"field": "version", "order": "DESC"}],
                  "comment": "recent updates"
                }
              ]
            }
          expected: |-
            {
              "type": "edge",
              "source": {"type": "long", "comment": "Source node ID"},
              "target": {"type": "long", "comment": "Target node ID"},
              "properties": [
                {
                  "name": "id",
                  "type": "long",
                  "comment": "Identifier",
                  "nullable": false
                },
                {
                  "name": "name",
                  "type": "string",
                  "comment": "name",
                  "nullable": false
                }
              ],
              "direction": "BOTH",
              "indexes": [
                {
                  "index": "updated_at_desc",
                  "fields": [{"field": "version", "order": "DESC"}],
                  "comment": "recent updates",
                  "primary": -1,
                  "batch": 0
                }
              ],
              "groups": [],
              "caches": []
            }
        """,
    )
    fun `serializes edge schema to JSON`(
        schema: ModelSchema,
        expected: String,
    ) {
        assertEquals(expected, prettyWriter.writeValueAsString(schema))
    }
}
