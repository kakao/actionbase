package com.kakao.actionbase.core.codec

import com.kakao.actionbase.core.metadata.common.Direction as KotlinDirection

import com.kakao.actionbase.core.edge.mapper.EdgeCountRecordMapper
import com.kakao.actionbase.core.edge.record.EdgeCountRecord
import com.kakao.actionbase.core.java.codec.StateCodec
import com.kakao.actionbase.core.java.codec.StateCodecFactory
import com.kakao.actionbase.core.java.metadata.v3.ImmutableEdgeTableDescriptor
import com.kakao.actionbase.core.java.metadata.v3.common.Direction
import com.kakao.actionbase.core.java.metadata.v3.common.DirectionType
import com.kakao.actionbase.core.java.metadata.v3.common.EdgeSchema
import com.kakao.actionbase.core.java.metadata.v3.common.MutationMode
import com.kakao.actionbase.core.java.types.DataType
import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest

import java.util.Base64

import kotlin.test.assertEquals

import org.junit.jupiter.api.Assertions.assertArrayEquals

class EdgeCountMapperTest {
    companion object {
        private val edgeCountMapper = EdgeCountRecordMapper.Companion.create()
        private val encoder = edgeCountMapper.encoder
        private val decoder = edgeCountMapper.decoder

        private val edgeStateCodec: StateCodec = StateCodecFactory().create()

        private val tableCode =
            ImmutableEdgeTableDescriptor
                .builder()
                .tenant("kc-tenant")
                .storage("mysql")
                .database("foo")
                .table("bars")
                .mode(MutationMode.SYNC)
                .schema(
                    EdgeSchema
                        .builder()
                        .apply {
                            source(DataType.LONG, "foo")
                            target(DataType.LONG, "bar")
                            direction(DirectionType.OUT)
                        }.build(),
                ).build()
                .code()
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - sourceField:
            type: string
            value: 1234
          direction: OUT
        - sourceField:
            type: string
            value: A
          direction: IN
        """,
    )
    fun encodeKeyTest(
        sourceField: Field,
        direction: Direction,
    ) {
        val source = sourceField.cast()

        // Java (expected)
        val expected: ByteArray = edgeStateCodec.encodeEdgeCountKey(source, direction, tableCode)

        // Kotlin (actual)
        val actual: ByteArray = encoder.encodeKey(EdgeCountRecord.Key.of(source, tableCode, direction = KotlinDirection.of(direction.code)))

        assertArrayEquals(expected, actual)
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - base64EncodedKey: 3NCIliuAAATSK7UfmNkpfimD
          expectedSourceField:
            type: int
            value: 1234
          direction: IN
        - base64EncodedKey: /PkMVzQxMjM0ACu1H5jZKX4pgg==
          expectedSourceField:
            type: string
            value: 1234
          direction: OUT
        - base64EncodedKey: P4+1DjRBACu1H5jZKX4pgw==
          expectedSourceField:
            type: string
            value: A
          direction: IN
        """,
    )
    fun decodeKeyTest(
        base64EncodedKey: String,
        expectedSourceField: Field,
        direction: KotlinDirection,
    ) {
        val key: ByteArray = Base64.getDecoder().decode(base64EncodedKey)
        val actual = decoder.decodeKey(key)

        val expectedSource = expectedSourceField.cast()
        assertEquals(expectedSource, actual.directedSource)
        assertEquals(direction, actual.direction)
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - encodedValue: AAAAAAAAAAI
          expectedCount: 2
        """,
    )
    fun decodeValueTest(
        encodedValue: String,
        expectedCount: Long,
    ) {
        val value: ByteArray = Base64.getDecoder().decode(encodedValue)

        val actual = decoder.decodeValue(value)

        assertEquals(expectedCount, actual)
    }
}
