package com.kakao.actionbase.core.codec

import com.kakao.actionbase.core.edge.EdgeState as EdgeStateKt
import com.kakao.actionbase.core.state.StateValue as KotlinStateValue

import com.kakao.actionbase.core.edge.mapper.EdgeStateRecordMapper
import com.kakao.actionbase.core.edge.record.EdgeStateRecord
import com.kakao.actionbase.core.java.codec.KeyValue
import com.kakao.actionbase.core.java.codec.StateCodec
import com.kakao.actionbase.core.java.codec.StateCodecFactory
import com.kakao.actionbase.core.java.edge.EdgeKey
import com.kakao.actionbase.core.java.edge.EdgeState
import com.kakao.actionbase.core.java.edge.ImmutableEdgeKey
import com.kakao.actionbase.core.java.edge.ImmutableEdgeState
import com.kakao.actionbase.core.java.metadata.v3.ImmutableEdgeTableDescriptor
import com.kakao.actionbase.core.java.metadata.v3.common.DirectionType
import com.kakao.actionbase.core.java.metadata.v3.common.EdgeSchema
import com.kakao.actionbase.core.java.metadata.v3.common.MutationMode
import com.kakao.actionbase.core.java.state.StateValue
import com.kakao.actionbase.core.java.types.DataType
import com.kakao.actionbase.core.java.types.StructType
import com.kakao.actionbase.core.state.AbstractSchema
import com.kakao.actionbase.core.state.Schema
import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest

import java.util.Base64

import kotlin.test.Ignore

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull

class EdgeStateMapperTest {
    companion object {
        private val xxHash32Wrapper = XXHash32Wrapper.default
        private val edgeStateMapper = EdgeStateRecordMapper.create()
        private val encoder: EdgeStateRecordMapper.Encoder = edgeStateMapper.encoder
        private val decoder: EdgeStateRecordMapper.Decoder = edgeStateMapper.decoder

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

        fun schemaOf(structType: StructType): AbstractSchema =
            Schema(
                nullabilityMap =
                    structType.fields().associate { field ->
                        field.name() to field.nullable()
                    },
            )
    }

    @Ignore
    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - sourceField:
            type: int
            value: 1234
          targetField:
            type: int
            value: 1234
        - sourceField:
            type: int
            value: 1234
          targetField:
            type: string
            value: Starbucks
        - sourceField:
            type: long
            value: 1234
          targetField:
            type: string
            value: Starbucks
        - sourceField:
            type: float
            value: 0.2
          targetField:
            type: double
            value: -3.1
        """,
    )
    fun encodeKeyTest(
        sourceField: Field,
        targetField: Field,
    ) {
        val source = sourceField.cast()
        val target = targetField.cast()

        // Java (expected)
        val expected: ByteArray = edgeStateCodec.encodeEdgeStateKey(ImmutableEdgeKey.of(source, target), tableCode)

        // Kotlin (actual)
        val actual: ByteArray = encoder.encodeKey(key = EdgeStateRecord.Key.of(source = source, tableCode = tableCode, target = target))

        assertArrayEquals(expected, actual)
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - source: 1234
          target: 5678
          active: true
          version: 1
          insertTs: 1753192420430
          deleteTs: 1753192420430
          properties: {"key":{"value":"value","version":1}}
        - source: 4321
          target: 8765
          active: false
          version: 1
          insertTs: 1753192420430
          deleteTs: 1753192420430
          properties: {"created_at":{"value":"created_at","version":2}}
        - source: 4321
          target: 8765
          active: false
          version: 1
          insertTs: 1753192420430
          deleteTs: 1753192420430
          properties: {"created_at":{"value":"created_at","version":2},"modified_at":{"value":"modified_at","version":3}}
        - source: 56789
          target: 101112
          active: true
          version: 2
          insertTs: 1754567578724
          properties: {"A":{"value":"B","version":2}}
        """,
    )
    fun encodeValueTest(
        source: Int,
        target: Int,
        active: Boolean,
        version: Long,
        insertTs: Long?,
        deleteTs: Long?,
        properties: Map<String, StateValue>,
    ) {
        // Java (expected)
        val expected: ByteArray =
            edgeStateCodec.encodeEdgeStateValue(
                ImmutableEdgeState
                    .builder()
                    .source(source)
                    .target(target)
                    .active(active)
                    .version(version)
                    .properties(properties)
                    .createdAt(insertTs)
                    .deletedAt(deleteTs)
                    .build(),
            )

        // Kotlin (actual)
        val actual: ByteArray =
            encoder.encodeValue(
                EdgeStateRecord.Value(
                    active = active,
                    version = version,
                    createdAt = insertTs,
                    deletedAt = deleteTs,
                    properties = properties.map { (key, value) -> xxHash32Wrapper.stringHash(key) to KotlinStateValue(value = value.value(), version = value.version()) }.toMap(),
                ),
            )

        assertArrayEquals(expected, actual)
    }

    @Ignore
    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - base64EncodedKey: gyqAoiuAAATSK7UfmNkpfSuAAATS
          expectedSourceField:
            type: int
            value: 1234
          expectedTargetField:
            type: int
            value: 1234
        - base64EncodedKey: gyqAoiuAAATSK7UfmNkpfTTsiqTtg4DrsoXsiqQA
          expectedSourceField:
            type: int
            value: 1234
          expectedTargetField:
            type: string
            value: Starbucks
        - base64EncodedKey: sg4ACSyAAAAAAAAE0iu1H5jZKX007Iqk7YOA67KF7IqkAA==
          expectedSourceField:
            type: long
            value: 1234
          expectedTargetField:
            type: string
            value: Starbucks
        - base64EncodedKey: U1hgazC+TMzNK7UfmNkpfTE/9zMzMzMzMg==
          expectedSourceField:
            type: float
            value: 0.2
          expectedTargetField:
            type: double
            value: -3.1
        """,
    )
    fun decodeKeyTest(
        base64EncodedKey: String,
        expectedSourceField: Field,
        expectedTargetField: Field,
    ) {
        val byteArray: ByteArray = Base64.getDecoder().decode(base64EncodedKey)
        val expectedSource = expectedSourceField.cast()
        val expectedTarget = expectedTargetField.cast()

        // Java (expected)
        val expected: EdgeKey = edgeStateCodec.decodeEdgeStateKey(byteArray)

        // Kotlin (actual)
        val actual: EdgeStateRecord.Key = decoder.decodeKey(byteArray)

        assertEquals(expectedSource, actual.source)
        assertEquals(expectedTarget, actual.target)

        assertEquals(expected.source(), actual.source)
        assertEquals(expected.target(), actual.target)
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - base64EncodedKey: gyqAoiuAAATSK7UfmNkpfSuAAATS
          base64EncodedValue: KYEsgAAAAAAAAAErM9k64DR2YWx1ZQAsgAAAAAAAAAErkkHeMSyAAAGYMmlUTiyAAAGYMmlUTiu8Y5OCLIAAAZgyaVROLIAAAZgyaVRO
          schema: {"type":"struct","fields":[{"name":"key","type":{"type":"string"}}]}
        - base64EncodedKey: gyqAoiuAAATSK7UfmNkpfSuAAATS
          base64EncodedValue: KYAsgAAAAAAAAAIr1Wc4JTRjcmVhdGVkX2F0ACyAAAAAAAAAAivTdg4DNG1vZGlmaWVkX2F0ACyAAAAAAAAAAyuSQd4xLIAAAZgyaVROLIAAAZgyaVROK7xjk4IsgAABmDJpVE4sgAABmDJpVE4
          schema: {"type":"struct","fields":[{"name":"created_at","type":{"type":"string"}},{"name":"modified_at","type":{"type":"string"}}]}
        - base64EncodedKey: gyqAoiuAAATSK7UfmNkpfSuAAATS
          base64EncodedValue: KYAsgAAAAAAAAAEr1Wc4JTRjcmVhdGVkX2F0ACyAAAAAAAAAAiuSQd4xLIAAAZgyaVROLIAAAZgyaVROK7xjk4IsgAABmDJpVE4sgAABmDJpVE4=
          schema: {"type":"struct","fields":[{"name":"created_at","type":{"type":"string"}}]}
        """,
    )
    fun decodeValueTest(
        base64EncodedKey: String,
        base64EncodedValue: String,
        schema: StructType,
    ) {
        val key: ByteArray = Base64.getDecoder().decode(base64EncodedKey)
        val value: ByteArray = Base64.getDecoder().decode(base64EncodedValue)

        // Java (expected)
        val expected: EdgeState = edgeStateCodec.decodeToEdgeState(key, value, schema)

        // Kotlin (actual)
        val actual: EdgeStateKt = decoder.decode(key, value).toState(schemaOf(schema))

        assertEquals(expected.active(), actual.state.active)
        assertEquals(expected.version(), actual.state.version)

        expected.properties().forEach { (key, value) ->
            assertEquals(value.value(), actual.state.properties[key]?.value)
        }

        expected.createdAt()?.let { assertEquals(it, actual.state.createdAt) } ?: run { assertNull(actual.state.createdAt) }
        expected.deletedAt()?.let { assertEquals(it, actual.state.deletedAt) } ?: run { assertNull(actual.state.deletedAt) }
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - sourceField:
            type: int
            value: 1234
          targetField:
            type: string
            value: Starbucks
          deleteTs: 1754566445706
          active: false
          version: 2
          properties: {"key":{"value":"value","version":1}}
        - sourceField:
            type: int
            value: 1234
          targetField:
            type: string
            value: Starbucks
          insertTs: 1754566668702
          deleteTs:
          active:  true
          version: 3
          properties: {"key":{"value":"value","version":1}}
        - sourceField:
            type: int
            value: 1234
          targetField:
            type: long
            value: 5678
          insertTs: 1754566668702
          deleteTs:
          active: true
          version: 1
          properties: {"received_at":{"value":1754566668702,"version":1}}
        """,
    )
    fun encodeTest(
        sourceField: Field,
        targetField: Field,
        insertTs: Long?,
        deleteTs: Long?,
        active: Boolean,
        version: Long,
        properties: Map<String, StateValue>,
    ) {
        val source = sourceField.cast()
        val target = targetField.cast()

        // Java (expected)
        val expected: KeyValue<ByteArray> =
            edgeStateCodec.encodeEdgeState(
                ImmutableEdgeState
                    .builder()
                    .source(source)
                    .target(target)
                    .active(active)
                    .version(version)
                    .properties(properties)
                    .createdAt(insertTs)
                    .deletedAt(deleteTs)
                    .build(),
                tableCode,
            )

        // Kotlin (actual)
        val actual =
            encoder.encode(
                EdgeStateRecord(
                    key =
                        EdgeStateRecord.Key.of(
                            source,
                            tableCode,
                            target = target,
                        ),
                    value =
                        EdgeStateRecord.Value(
                            active = active,
                            version = version,
                            properties = properties.map { (key, value) -> xxHash32Wrapper.stringHash(key) to KotlinStateValue(value = value.value(), version = value.version()) }.toMap(),
                            createdAt = insertTs,
                            deletedAt = deleteTs,
                        ),
                ),
            )

        assertArrayEquals(expected.key(), actual.key)
        assertArrayEquals(expected.value(), actual.value)
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - encodedKey: gyqAoiuAAATSK7UfmNkpfTTsiqTtg4DrsoXsiqQA
          encodedValue: KYEsgAAAAAAAAAErM9k64DR2YWx1ZQAsgAAAAAAAAAErkkHeMSyAAAGYMmlUTiyAAAGYMmlUTiu8Y5OCLIAAAZgyaVROLIAAAZgyaVRO
          schema: {"type":"struct","fields":[{"name":"key","type":{"type":"string"}}]}

        - encodedKey: RnDE1CuAAN3VK7UfmNkpfSuAAWOD
          encodedValue: KYEsgAAAAAAAAAMrNGSIYyyAAAGYhFsDHyyAAAAAAAAAAivW96yyLIAAAZiEWwMfLIAAAAAAAAACK7xjk4IsgAABmIRbAx8sgAABmIRbAx8=
          schema: {"type":"struct","fields":[{"name":"received_at","type":{"type":"long"}},{"name":"paid_at","type":{"type":"long"}}]}

        - encodedKey: RnDE1CuAAN3VK7UfmNkpfSuAAWOD
          encodedValue: KYEsgAAAAAAAAAIrkGWaTTRCACyAAAAAAAAAAiuSQd4xLIAAAZiEYJRkLIAAAZiEYJRkK7xjk4IsgAAAAAAAAAAsgAAAAAAAAAA=
          schema: {"type":"struct","fields":[{"name":"A","type":{"type":"string"}}]}
        """,
    )
    fun decodeTest(
        encodedKey: String,
        encodedValue: String,
        schema: StructType,
    ) {
        val key: ByteArray = Base64.getDecoder().decode(encodedKey)
        val value: ByteArray = Base64.getDecoder().decode(encodedValue)

        // Java (expected)
        val expected: EdgeState = edgeStateCodec.decodeToEdgeState(key, value, schema)

        // Kotlin (actual)
        val actual: EdgeStateKt = decoder.decode(key, value).toState(schemaOf(schema))

        assertEquals(expected.key().source(), actual.source)
        assertEquals(expected.key().target(), actual.target)

        assertEquals(expected.active(), actual.state.active)
        assertEquals(expected.version(), actual.state.version)
        expected.properties().forEach { (key, value) ->
            assertEquals(value.value(), actual.state.properties[key]?.value)
            assertEquals(value.version(), actual.state.properties[key]?.version)
        }

        expected.createdAt()?.let { assertEquals(it, actual.state.createdAt) } ?: run { assertNull(actual.state.createdAt) }
        expected.deletedAt()?.let { assertEquals(it, actual.state.deletedAt) } ?: run { assertNull(actual.state.deletedAt) }
    }
}
