package com.kakao.actionbase.core.v2.metadata

import com.kakao.actionbase.core.metadata.AliasDescriptor as V3AliasDescriptor

import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest
import com.kakao.actionbase.test.json.PrettyObjectWriter

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

import com.fasterxml.jackson.module.kotlin.readValue

class V2AliasSerializationTest {
    val prettyWriter = PrettyObjectWriter.DEFAULT

    val objectMapper = prettyWriter.objectMapper

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - descriptor: {
              "name": "gift.gift_like_product_v1",
              "desc": "Gift Wish",
              "active": true,
              "target": "gift.gift_like_product_v1_20240605_102816"
            }
          expected: |-
            {
              "name": "gift.gift_like_product_v1",
              "target": "gift.gift_like_product_v1_20240605_102816",
              "desc": "Gift Wish",
              "active": true
            }
        """,
    )
    fun `serializes to JSON`(
        descriptor: V2AliasDescriptor,
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
              "name": "gift.gift_like_product_v1",
              "desc": "Gift Wish",
              "target": "gift.gift_like_product_v1_20240605_102816",
              "label": {
                "active": true,
                "name": "gift.gift_like_product_v1_20240605_102816",
                "desc": "some redundant label information"
              }
            }
          expected: {
              "name": "gift.gift_like_product_v1",
              "desc": "Gift Wish",
              "active": true,
              "target": "gift.gift_like_product_v1_20240605_102816"
            }
        """,
    )
    fun `deserializes from JSON`(
        input: String,
        expected: V2AliasDescriptor,
    ) {
        assertEquals(expected, objectMapper.readValue<V2AliasDescriptor>(input))
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - tenant: test_tenant
          descriptor: {
              "name": "gift.gift_like_product_v1",
              "desc": "Gift Wish",
              "active": true,
              "target": "gift.gift_like_product_v1_20240605_102816"
            }
          expected: {
              "tenant": "test_tenant",
              "database": "gift",
              "alias": "gift_like_product_v1",
              "table": "gift_like_product_v1_20240605_102816",
              "comment": "Gift Wish"
            }
        """,
    )
    fun `converts to V3 object`(
        tenant: String,
        descriptor: V2AliasDescriptor,
        expected: V3AliasDescriptor,
    ) {
        assertEquals(expected, descriptor.toV3(tenant))
    }

    @Ignore
    @Test
    fun `test service to database json string`() {
        // given
        val v2AliasDescriptor =
            V2AliasDescriptor(
                name = "gift.gift_like_product_v1",
                desc = "Gift Wish",
                active = true,
                target = "gift.gift_like_product_v1_20240605_102816",
            )

        // when
        val v3 = v2AliasDescriptor.toV3("test_tenant")
        val actual = prettyWriter.writeValueAsString(v3)

        // then
        val expected =
            """
            {
              "tenant": "test_tenant",
              "database": "gift",
              "alias": "gift_like_product_v1",
              "table": "gift_like_product_v1_20240605_102816",
              "active": true,
              "comment": "Gift Wish",
              "revision": -1,
              "createdAt": -1,
              "createdBy": "",
              "updatedAt": -1,
              "updatedBy": ""
            }
            """.trimIndent()
        assertEquals(expected, actual)
    }
}
