package com.kakao.actionbase.core.metadata

import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest
import com.kakao.actionbase.test.json.PrettyObjectWriter

import kotlin.test.assertEquals

import com.fasterxml.jackson.module.kotlin.readValue

class DatastoreSerializationTest {
    val prettyWriter = PrettyObjectWriter.DEFAULT

    val objectMapper = prettyWriter.objectMapper

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - descriptor: {
              "type": "HBASE",
              "configuration": {"hbase.zookeeper.quorum": "localhost"}
            }
          expected: '{"type": "HBASE", "configuration": {"hbase.zookeeper.quorum": "localhost"}}'
        """,
    )
    fun `serializes to JSON`(
        descriptor: DatastoreDescriptor,
        expected: String,
    ) {
        assertEquals(expected, prettyWriter.writeValueAsString(descriptor))
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - input: '{"type": "HBASE", "configuration": {"hbase.zookeeper.quorum": "localhost"}}'
          expected: {
              "type": "HBASE",
              "configuration": {"hbase.zookeeper.quorum": "localhost"}
            }
        """,
    )
    fun `deserializes from JSON`(
        input: String,
        expected: DatastoreDescriptor,
    ) {
        assertEquals(expected, objectMapper.readValue<DatastoreDescriptor>(input))
    }
}
