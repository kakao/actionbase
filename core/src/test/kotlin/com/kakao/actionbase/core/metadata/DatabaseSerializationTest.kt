package com.kakao.actionbase.core.metadata

import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest
import com.kakao.actionbase.test.json.PrettyObjectWriter

import kotlin.test.assertEquals

import com.fasterxml.jackson.module.kotlin.readValue

class DatabaseSerializationTest {
    val prettyWriter = PrettyObjectWriter.DEFAULT

    val objectMapper = prettyWriter.objectMapper

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - descriptor: {
              "tenant": "test_tenant",
              "database": "test_database"
            }
          expected: |-
            {
              "tenant": "test_tenant",
              "database": "test_database",
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
        descriptor: DatabaseDescriptor,
        expected: String,
    ) {
        assertEquals(expected, prettyWriter.writeValueAsString(descriptor))
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - input: '{"tenant":"test_tenant","database":"test_database"}'
          expected: {
              "tenant": "test_tenant",
              "database": "test_database"
            }
        """,
    )
    fun `deserializes from JSON`(
        input: String,
        expected: DatabaseDescriptor,
    ) {
        assertEquals(expected, objectMapper.readValue<DatabaseDescriptor>(input))
    }
}
