package com.kakao.actionbase.core.metadata.common

import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest
import com.kakao.actionbase.test.json.PrettyObjectWriter

import kotlin.test.assertEquals

import com.fasterxml.jackson.module.kotlin.readValue

class EdgeSchemaSerializationTest {
    val prettyWriter = PrettyObjectWriter.DEFAULT

    val objectMapper = prettyWriter.objectMapper

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
                        "topkDimension": "target",
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
                        "topkDimension": "target",
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
                    "topk": [{"topk": "top_purchased", "entity": "__GLOBAL__", "topkDimension": "target", "rank": "commerce.order_product__topk"}]
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
                    "topk": [{"topk": "top_purchased", "entity": "__GLOBAL__", "topkDimension": "target", "rank": "commerce.order_product__topk"}]
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
