package com.kakao.actionbase.core.v2.metadata

import com.kakao.actionbase.test.json.PrettyObjectWriter

import kotlin.test.Test
import kotlin.test.assertEquals

import com.fasterxml.jackson.module.kotlin.readValue

class V2StorageSerializationTest {
    val prettyWriter = PrettyObjectWriter(indentSize = 2, lineLengthLimit = 80)

    val objectMapper = prettyWriter.objectMapper

    @Test
    fun `test storage serialization`() {
        // given
        val v2StorageDescriptor =
            V2StorageDescriptor(
                type = "HBASE",
                name = "st3_talkstore_view_product_v1_20240730_231948",
                desc = "Shopping recently viewed products",
                active = true,
                conf =
                    mapOf(
                        "namespace" to "kc_graph",
                        "tableName" to "st3_talkstore_view_product_v1_20240730_231948",
                    ),
            )

        // when
        val actual = prettyWriter.writeValueAsString(v2StorageDescriptor)

        // then
        val expected =
            """
            {
              "type": "HBASE",
              "name": "st3_talkstore_view_product_v1_20240730_231948",
              "desc": "Shopping recently viewed products",
              "active": true,
              "conf": {
                "namespace": "kc_graph",
                "tableName": "st3_talkstore_view_product_v1_20240730_231948"
              }
            }
            """.trimIndent()

        assertEquals(expected, actual)
    }

    @Test
    fun `test storage deserialization`() {
        // given
        val json =
            """
            {
              "active": true,
              "name": "st3_talkstore_view_product_v1_20240730_231948",
              "desc": "Shopping recently viewed products",
              "type": "HBASE",
              "conf": {
                "namespace": "kc_graph",
                "tableName": "st3_talkstore_view_product_v1_20240730_231948"
              }
            }
            """.trimIndent()

        // when
        val actual = objectMapper.readValue<V2StorageDescriptor>(json)

        // then
        val expected =
            V2StorageDescriptor(
                type = "HBASE",
                name = "st3_talkstore_view_product_v1_20240730_231948",
                desc = "Shopping recently viewed products",
                active = true,
                conf =
                    mapOf(
                        "namespace" to "kc_graph",
                        "tableName" to "st3_talkstore_view_product_v1_20240730_231948",
                    ),
            )
        assertEquals(expected, actual)
    }
}
