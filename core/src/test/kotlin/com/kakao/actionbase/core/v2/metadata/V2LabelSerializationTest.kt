package com.kakao.actionbase.core.v2.metadata

import com.kakao.actionbase.core.java.codec.common.hbase.Order
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.v2.metadata.common.V2Field
import com.kakao.actionbase.core.v2.metadata.common.V2Index
import com.kakao.actionbase.core.v2.metadata.common.V2IndexField
import com.kakao.actionbase.core.v2.metadata.common.V2MutationMode
import com.kakao.actionbase.core.v2.metadata.common.V2Schema
import com.kakao.actionbase.core.v2.metadata.common.V2StructField
import com.kakao.actionbase.core.v2.types.V2PrimitiveType
import com.kakao.actionbase.test.json.PrettyObjectWriter

import kotlin.test.Test
import kotlin.test.assertEquals

import com.fasterxml.jackson.module.kotlin.readValue

class V2LabelSerializationTest {
    val prettyWriter = PrettyObjectWriter(indentSize = 2, lineLengthLimit = 80)

    val objectMapper = prettyWriter.objectMapper

    @Test
    fun `test service serialization`() {
        // given
        val v2LabelDescriptor =
            V2LabelDescriptor(
                name = "gift.gift_like_product_v1_20240605_102816",
                desc = "Gift wish / gift_like_product_v1_20240521_185845",
                active = true,
                type = "INDEXED",
                schema =
                    V2Schema(
                        src =
                            V2Field(
                                type = V2PrimitiveType.LONG,
                                desc = "Commerce user ID (cuid)",
                            ),
                        tgt =
                            V2Field(
                                type = V2PrimitiveType.LONG,
                                desc = "Gift product ID (product_id)",
                            ),
                        fields =
                            listOf(
                                V2StructField(
                                    name = "createdAt",
                                    type = V2PrimitiveType.LONG,
                                    nullable = false,
                                    desc = "Creation time={system.currentTimeMillis()}",
                                ),
                                V2StructField(
                                    name = "permission",
                                    type = V2PrimitiveType.STRING,
                                    nullable = false,
                                    desc = "View permission={me | others}",
                                ),
                                V2StructField(
                                    name = "receivedFrom",
                                    type = V2PrimitiveType.STRING,
                                    nullable = false,
                                    desc = "Source after successful order???={me | others | not_received}",
                                ),
                            ),
                    ),
                dirType = DirectionType.BOTH,
                storage = "st3_gift_like_product_v1_20240605_102816",
                indices =
                    listOf(
                        V2Index(
                            name = "permission_created_at_desc",
                            fields =
                                listOf(
                                    V2IndexField(name = "permission", order = Order.ASC),
                                    V2IndexField(name = "createdAt", order = Order.DESC),
                                ),
                            desc = "Wish permission/creation time descending index",
                        ),
                        V2Index(
                            name = "created_at_desc",
                            fields =
                                listOf(V2IndexField(name = "createdAt", order = Order.DESC)),
                            desc = "Wish creation time descending index",
                        ),
                    ),
                groups = emptyList(),
                event = false,
                readOnly = false,
                mode = V2MutationMode.SYNC,
            )

        // when
        val actual = prettyWriter.writeValueAsString(v2LabelDescriptor)

        // then
        val expected =
            """
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
            """.trimIndent()

        assertEquals(expected, actual)
    }

    @Test
    fun `test label deserialization`() {
        // given
        val json =
            """
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
                    {
                      "name": "permission",
                      "order": "ASC"
                    },
                    {
                      "name": "createdAt",
                      "order": "DESC"
                    }
                  ],
                  "desc": "Wish permission/creation time descending index"
                },
                {
                  "name": "created_at_desc",
                  "fields": [
                    {
                      "name": "createdAt",
                      "order": "DESC"
                    }
                  ],
                  "desc": "Wish creation time descending index"
                }
              ],
              "groups": [],
              "event": false,
              "readOnly": false,
              "mode": "SYNC"
            }
            """.trimIndent()

        // when
        val actual = objectMapper.readValue<V2LabelDescriptor>(json)

        // then
        val expected =
            V2LabelDescriptor(
                name = "gift.gift_like_product_v1_20240605_102816",
                desc = "Gift wish / gift_like_product_v1_20240521_185845",
                active = true,
                type = "INDEXED",
                schema =
                    V2Schema(
                        src =
                            V2Field(
                                type = V2PrimitiveType.LONG,
                                desc = "Commerce user ID (cuid)",
                            ),
                        tgt =
                            V2Field(
                                type = V2PrimitiveType.LONG,
                                desc = "Gift product ID (product_id)",
                            ),
                        fields =
                            listOf(
                                V2StructField(
                                    name = "createdAt",
                                    type = V2PrimitiveType.LONG,
                                    nullable = false,
                                    desc = "Creation time={system.currentTimeMillis()}",
                                ),
                                V2StructField(
                                    name = "permission",
                                    type = V2PrimitiveType.STRING,
                                    nullable = false,
                                    desc = "View permission={me | others}",
                                ),
                                V2StructField(
                                    name = "receivedFrom",
                                    type = V2PrimitiveType.STRING,
                                    nullable = false,
                                    desc = "Source after successful order???={me | others | not_received}",
                                ),
                            ),
                    ),
                dirType = DirectionType.BOTH,
                storage = "st3_gift_like_product_v1_20240605_102816",
                indices =
                    listOf(
                        V2Index(
                            name = "permission_created_at_desc",
                            fields =
                                listOf(
                                    V2IndexField(name = "permission", order = Order.ASC),
                                    V2IndexField(name = "createdAt", order = Order.DESC),
                                ),
                            desc = "Wish permission/creation time descending index",
                        ),
                        V2Index(
                            name = "created_at_desc",
                            fields =
                                listOf(V2IndexField(name = "createdAt", order = Order.DESC)),
                            desc = "Wish creation time descending index",
                        ),
                    ),
                groups = emptyList(),
                event = false,
                readOnly = false,
                mode = V2MutationMode.SYNC,
            )
        assertEquals(expected, actual)
    }
}
