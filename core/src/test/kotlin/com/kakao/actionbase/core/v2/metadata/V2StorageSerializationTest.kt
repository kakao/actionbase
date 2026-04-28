package com.kakao.actionbase.core.v2.metadata

import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest
import com.kakao.actionbase.test.json.PrettyObjectWriter

import kotlin.test.assertEquals

import com.fasterxml.jackson.module.kotlin.readValue

class V2StorageSerializationTest {
    val prettyWriter = PrettyObjectWriter.DEFAULT

    val objectMapper = prettyWriter.objectMapper

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - descriptor: {
              "type": "HBASE",
              "name": "st3_talkstore_view_product_v1_20240730_231948",
              "desc": "Shopping recently viewed products",
              "active": true,
              "conf": {
                "namespace": "kc_graph",
                "tableName": "st3_talkstore_view_product_v1_20240730_231948"
              }
            }
          expected: |-
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
        """,
    )
    fun `serializes to JSON`(
        descriptor: V2StorageDescriptor,
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
              "name": "st3_talkstore_view_product_v1_20240730_231948",
              "desc": "Shopping recently viewed products",
              "type": "HBASE",
              "conf": {
                "namespace": "kc_graph",
                "tableName": "st3_talkstore_view_product_v1_20240730_231948"
              }
            }
          expected: {
              "type": "HBASE",
              "name": "st3_talkstore_view_product_v1_20240730_231948",
              "desc": "Shopping recently viewed products",
              "active": true,
              "conf": {
                "namespace": "kc_graph",
                "tableName": "st3_talkstore_view_product_v1_20240730_231948"
              }
            }
        """,
    )
    fun `deserializes from JSON`(
        input: String,
        expected: V2StorageDescriptor,
    ) {
        assertEquals(expected, objectMapper.readValue<V2StorageDescriptor>(input))
    }
}
