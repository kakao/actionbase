package com.kakao.actionbase.core.v2.metadata

import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest
import com.kakao.actionbase.test.json.PrettyObjectWriter

import kotlin.test.Ignore
import kotlin.test.assertEquals

import com.fasterxml.jackson.module.kotlin.readValue

class V2ServiceSerializationTest {
    val prettyWriter = PrettyObjectWriter.DEFAULT

    val objectMapper = prettyWriter.objectMapper

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - descriptor: {"name": "gift", "desc": "Gift", "active": true}
          expected: '{"name": "gift", "desc": "Gift", "active": true}'
        """,
    )
    fun `serializes to JSON`(
        descriptor: V2ServiceDescriptor,
        expected: String,
    ) {
        assertEquals(expected, prettyWriter.writeValueAsString(descriptor))
    }

    @Ignore
    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - input: '{"active": true, "name": "gift", "desc": "Gift"}'
          expected: {"name": "gift", "desc": "Gift", "active": true}
        """,
    )
    fun `deserializes from JSON`(
        input: String,
        expected: V2ServiceDescriptor,
    ) {
        assertEquals(expected, objectMapper.readValue<V2ServiceDescriptor>(input))
    }
}
