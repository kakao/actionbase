package com.kakao.actionbase.core.v2.metadata

import com.kakao.actionbase.core.metadata.DatabaseDescriptor
import com.kakao.actionbase.core.metadata.payload.DatabaseCreateRequest
import com.kakao.actionbase.core.metadata.payload.DatabaseUpdateRequest
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

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - tenant: test_tenant
          descriptor: {"name": "gift", "desc": "Gift", "active": true}
          expected: {"tenant": "test_tenant", "database": "gift", "comment": "Gift", "active": true}
        """,
    )
    fun `converts to V3 database object`(
        tenant: String,
        descriptor: V2ServiceDescriptor,
        expected: DatabaseDescriptor,
    ) {
        assertEquals(expected, descriptor.toV3(tenant))
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - tenant: test_tenant
          descriptor: {"name": "gift", "desc": "Gift", "active": true}
          expected: |-
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
        """,
    )
    fun `serializes V3 database object to JSON`(
        tenant: String,
        descriptor: V2ServiceDescriptor,
        expected: String,
    ) {
        assertEquals(expected, prettyWriter.writeValueAsString(descriptor.toV3(tenant)))
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - tenant: test_tenant
          descriptor: {"name": "gift", "desc": "Gift", "active": true}
          expected: {"database": "gift", "comment": "Gift"}
        """,
    )
    fun `produces V3 create request`(
        tenant: String,
        descriptor: V2ServiceDescriptor,
        expected: DatabaseCreateRequest,
    ) {
        // version is generated from currentTimeMillis(); normalize before comparing
        val actual = descriptor.toV3(tenant).toCreateRequest()
        val sameVersion = 0L
        assertEquals(expected.copy(version = sameVersion), actual.copy(version = sameVersion))
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - tenant: test_tenant
          descriptor: {"name": "gift", "desc": "Gift", "active": true}
          expected: {"active": true, "comment": "Gift"}
        """,
    )
    fun `produces V3 update request`(
        tenant: String,
        descriptor: V2ServiceDescriptor,
        expected: DatabaseUpdateRequest,
    ) {
        // version is generated from currentTimeMillis(); normalize before comparing
        val actual = descriptor.toV3(tenant).toUpdateRequest()
        val sameVersion = 0L
        assertEquals(expected.copy(version = sameVersion), actual.copy(version = sameVersion))
    }
}
