package com.kakao.actionbase.core.v2.metadata

import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest
import com.kakao.actionbase.test.json.PrettyObjectWriter

import kotlin.test.assertEquals

import com.fasterxml.jackson.module.kotlin.readValue

class V2LabelSerializationTest {
    val prettyWriter = PrettyObjectWriter.DEFAULT

    val objectMapper = prettyWriter.objectMapper

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - descriptor: {
              "active": true,
              "name": "gift.gift_like_product_v1_20240605_102816",
              "desc": "Gift wish / gift_like_product_v1_20240521_185845",
              "type": "INDEXED",
              "schema": {
                "src": {"type": "LONG", "desc": "Commerce user ID (cuid)"},
                "tgt": {"type": "LONG", "desc": "Gift product ID (product_id)"},
                "fields": [
                  {"name": "createdAt", "type": "LONG", "nullable": false, "desc": "Creation time={system.currentTimeMillis()}"},
                  {"name": "permission", "type": "STRING", "nullable": false, "desc": "View permission={me | others}"},
                  {"name": "receivedFrom", "type": "STRING", "nullable": false, "desc": "Source after successful order???={me | others | not_received}"}
                ]
              },
              "dirType": "BOTH",
              "storage": "st3_gift_like_product_v1_20240605_102816",
              "indices": [
                {
                  "name": "permission_created_at_desc",
                  "fields": [
                    {"name": "permission", "order": "ASC"},
                    {"name": "createdAt", "order": "DESC"}
                  ],
                  "desc": "Wish permission/creation time descending index"
                },
                {
                  "name": "created_at_desc",
                  "fields": [{"name": "createdAt", "order": "DESC"}],
                  "desc": "Wish creation time descending index"
                }
              ],
              "groups": [],
              "event": false,
              "readOnly": false,
              "mode": "SYNC"
            }
          expected: |-
            {
              "active": true,
              "name": "gift.gift_like_product_v1_20240605_102816",
              "desc": "Gift wish / gift_like_product_v1_20240521_185845",
              "type": "INDEXED",
              "schema": {
                "src": {"type": "LONG", "desc": "Commerce user ID (cuid)"},
                "tgt": {"type": "LONG", "desc": "Gift product ID (product_id)"},
                "fields": [
                  {
                    "name": "createdAt",
                    "type": "LONG",
                    "nullable": false,
                    "desc": "Creation time={system.currentTimeMillis()}"
                  },
                  {
                    "name": "permission",
                    "type": "STRING",
                    "nullable": false,
                    "desc": "View permission={me | others}"
                  },
                  {
                    "name": "receivedFrom",
                    "type": "STRING",
                    "nullable": false,
                    "desc": "Source after successful order???={me | others | not_received}"
                  }
                ]
              },
              "dirType": "BOTH",
              "storage": "st3_gift_like_product_v1_20240605_102816",
              "indices": [
                {
                  "name": "permission_created_at_desc",
                  "fields": [
                    {"name": "permission", "order": "ASC"},
                    {"name": "createdAt", "order": "DESC"}
                  ],
                  "desc": "Wish permission/creation time descending index"
                },
                {
                  "name": "created_at_desc",
                  "fields": [{"name": "createdAt", "order": "DESC"}],
                  "desc": "Wish creation time descending index"
                }
              ],
              "groups": [],
              "event": false,
              "readOnly": false,
              "mode": "SYNC"
            }
        """,
    )
    fun `serializes to JSON`(
        descriptor: V2LabelDescriptor,
        expected: String,
    ) {
        assertEquals(expected, prettyWriter.writeValueAsString(descriptor))
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - input: |-
            {
              "active": true,
              "name": "gift.gift_like_product_v1_20240605_102816",
              "desc": "Gift wish / gift_like_product_v1_20240521_185845",
              "type": "INDEXED",
              "schema": {
                "src": {
                  "type": "LONG",
                  "desc": "Commerce user ID (cuid)"
                },
                "tgt": {
                  "type": "LONG",
                  "desc": "Gift product ID (product_id)"
                },
                "fields": [
                  {
                    "name": "createdAt",
                    "type": "LONG",
                    "nullable": false,
                    "desc": "Creation time={system.currentTimeMillis()}"
                  },
                  {
                    "name": "permission",
                    "type": "STRING",
                    "nullable": false,
                    "desc": "View permission={me | others}"
                  },
                  {
                    "name": "receivedFrom",
                    "type": "STRING",
                    "nullable": false,
                    "desc": "Source after successful order???={me | others | not_received}"
                  }
                ]
              },
              "dirType": "BOTH",
              "storage": "st3_gift_like_product_v1_20240605_102816",
              "indices": [
                {
                  "name": "permission_created_at_desc",
                  "fields": [
                    {"name": "permission", "order": "ASC"},
                    {"name": "createdAt", "order": "DESC"}
                  ],
                  "desc": "Wish permission/creation time descending index"
                },
                {
                  "name": "created_at_desc",
                  "fields": [{"name": "createdAt", "order": "DESC"}],
                  "desc": "Wish creation time descending index"
                }
              ],
              "groups": [],
              "event": false,
              "readOnly": false,
              "mode": "SYNC"
            }
          expected: {
              "active": true,
              "name": "gift.gift_like_product_v1_20240605_102816",
              "desc": "Gift wish / gift_like_product_v1_20240521_185845",
              "type": "INDEXED",
              "schema": {
                "src": {"type": "LONG", "desc": "Commerce user ID (cuid)"},
                "tgt": {"type": "LONG", "desc": "Gift product ID (product_id)"},
                "fields": [
                  {"name": "createdAt", "type": "LONG", "nullable": false, "desc": "Creation time={system.currentTimeMillis()}"},
                  {"name": "permission", "type": "STRING", "nullable": false, "desc": "View permission={me | others}"},
                  {"name": "receivedFrom", "type": "STRING", "nullable": false, "desc": "Source after successful order???={me | others | not_received}"}
                ]
              },
              "dirType": "BOTH",
              "storage": "st3_gift_like_product_v1_20240605_102816",
              "indices": [
                {
                  "name": "permission_created_at_desc",
                  "fields": [
                    {"name": "permission", "order": "ASC"},
                    {"name": "createdAt", "order": "DESC"}
                  ],
                  "desc": "Wish permission/creation time descending index"
                },
                {
                  "name": "created_at_desc",
                  "fields": [{"name": "createdAt", "order": "DESC"}],
                  "desc": "Wish creation time descending index"
                }
              ],
              "groups": [],
              "event": false,
              "readOnly": false,
              "mode": "SYNC"
            }
        """,
    )
    fun `deserializes from JSON`(
        input: String,
        expected: V2LabelDescriptor,
    ) {
        assertEquals(expected, objectMapper.readValue<V2LabelDescriptor>(input))
    }
}
