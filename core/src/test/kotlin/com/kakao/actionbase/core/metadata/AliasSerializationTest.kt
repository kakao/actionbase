package com.kakao.actionbase.core.metadata

import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest
import com.kakao.actionbase.test.json.PrettyObjectWriter

import kotlin.test.assertEquals

import com.fasterxml.jackson.module.kotlin.readValue

class AliasSerializationTest {
    val prettyWriter = PrettyObjectWriter.DEFAULT

    val objectMapper = prettyWriter.objectMapper

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - descriptor: {
              "tenant": "test_tenant",
              "database": "test_database",
              "alias": "test_alias",
              "table": "test_table"
            }
          expected: |-
            {
              "tenant": "test_tenant",
              "database": "test_database",
              "alias": "test_alias",
              "table": "test_table",
              "active": true,
              "comment": "",
              "revision": -1,
              "createdAt": -1,
              "createdBy": "",
              "updatedAt": -1,
              "updatedBy": ""
            }
        """,
    )
    fun `serializes to JSON`(
        descriptor: AliasDescriptor,
        expected: String,
    ) {
        assertEquals(expected, prettyWriter.writeValueAsString(descriptor))
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - input: '{"tenant":"test_tenant","database":"test_database","alias":"test_alias","table":"test_table"}'
          expected: {
              "tenant": "test_tenant",
              "database": "test_database",
              "alias": "test_alias",
              "table": "test_table"
            }
        """,
    )
    fun `deserializes from JSON`(
        input: String,
        expected: AliasDescriptor,
    ) {
        assertEquals(expected, objectMapper.readValue<AliasDescriptor>(input))
    }
}
