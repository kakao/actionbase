package com.kakao.actionbase.core.v2.metadata

import com.kakao.actionbase.core.metadata.DatabaseDescriptor
import com.kakao.actionbase.core.metadata.payload.DatabaseCreateRequest
import com.kakao.actionbase.core.metadata.payload.DatabaseUpdateRequest
import com.kakao.actionbase.test.json.PrettyObjectWriter

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

import com.fasterxml.jackson.module.kotlin.readValue

class V2ServiceSerializationTest {
    val prettyWriter = PrettyObjectWriter(indentSize = 2, lineLengthLimit = 80)

    val objectMapper = prettyWriter.objectMapper

    @Test
    fun `test service serialization`() {
        // given
        val v2ServiceDescriptor = V2ServiceDescriptor(name = "gift", desc = "Gift", active = true)

        // when
        val actual = prettyWriter.writeValueAsString(v2ServiceDescriptor)

        // then
        val expected =
            """{"name": "gift", "desc": "Gift", "active": true}"""

        assertEquals(expected, actual)
    }

    @Ignore
    @Test
    fun `test service deserialization`() {
        // given
        val json =
            """{"active": true, "name": "gift", "desc": "Gift"}"""

        // when
        val actual = objectMapper.readValue<V2ServiceDescriptor>(json)

        // then
        val expected = V2ServiceDescriptor(name = "gift", desc = "Gift", active = true)
        assertEquals(expected, actual)
    }

    @Test
    fun `test service to database object`() {
        // given
        val v2ServiceDescriptor = V2ServiceDescriptor(name = "gift", desc = "Gift", active = true)

        // when
        val actual = v2ServiceDescriptor.toV3("test_tenant")

        // then
        val expected = DatabaseDescriptor(tenant = "test_tenant", database = "gift", comment = "Gift", active = true)
        assertEquals(expected, actual)
    }

    @Test
    fun `test service to database json string`() {
        // given
        val v2ServiceDescriptor = V2ServiceDescriptor(name = "gift", desc = "Gift", active = true)

        // when
        val v3 = v2ServiceDescriptor.toV3("test_tenant")
        val actual = prettyWriter.writeValueAsString(v3)

        // then
        val expected =
            """
            {
              "tenant": "test_tenant",
              "database": "gift",
              "active": true,
              "comment": "Gift",
              "revision": -1,
              "createdAt": -1,
              "createdBy": "",
              "updatedAt": -1,
              "updatedBy": ""
            }
            """.trimIndent()
        assertEquals(expected, actual)
    }

    @Test
    fun `test service to database create request`() {
        // given
        val v2ServiceDescriptor = V2ServiceDescriptor(name = "gift", desc = "Gift", active = true)

        // when
        val actual = v2ServiceDescriptor.toV3("test_tenant").toCreateRequest()

        // then
        val expected = DatabaseCreateRequest(database = "gift", comment = "Gift")

        val sameVersion = 0L
        assertEquals(expected.copy(version = sameVersion), actual.copy(version = sameVersion))
    }

    @Test
    fun `test service to database update request`() {
        // given
        val v2ServiceDescriptor = V2ServiceDescriptor(name = "gift", desc = "Gift", active = true)

        // when
        val actual = v2ServiceDescriptor.toV3("test_tenant").toUpdateRequest()

        // then
        val expected = DatabaseUpdateRequest(active = true, comment = "Gift")

        val sameVersion = 0L
        assertEquals(expected.copy(version = sameVersion), actual.copy(version = sameVersion))
    }
}
