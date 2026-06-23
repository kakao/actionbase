package com.kakao.actionbase.core.codec

import com.kakao.actionbase.core.metadata.common.Direction as KotlinDirection

import com.kakao.actionbase.core.edge.mapper.EdgeIndexRecordMapper
import com.kakao.actionbase.core.edge.record.EdgeIndexRecord
import com.kakao.actionbase.core.java.codec.ImmutableKeyValue
import com.kakao.actionbase.core.java.codec.KeyValue
import com.kakao.actionbase.core.java.codec.StateCodec
import com.kakao.actionbase.core.java.codec.StateCodecFactory
import com.kakao.actionbase.core.java.codec.common.hbase.Order
import com.kakao.actionbase.core.java.edge.index.ImmutableEncodableEdgeIndex
import com.kakao.actionbase.core.java.edge.index.ImmutableEncodableIndexValue
import com.kakao.actionbase.core.java.edge.index.StoredEdgeIndex
import com.kakao.actionbase.core.java.metadata.v3.ImmutableEdgeTableDescriptor
import com.kakao.actionbase.core.java.metadata.v3.common.Direction
import com.kakao.actionbase.core.java.metadata.v3.common.DirectionType
import com.kakao.actionbase.core.java.metadata.v3.common.EdgeSchema
import com.kakao.actionbase.core.java.metadata.v3.common.MutationMode
import com.kakao.actionbase.core.java.types.DataType
import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest
import com.kakao.actionbase.test.documentations.params.TableSource

import java.util.Base64

import kotlin.test.assertEquals

import org.junit.jupiter.api.Assertions.assertArrayEquals

class EdgeIndexMapperTest {
    companion object {
        private val xxHash32Wrapper = XXHash32Wrapper.default
        private val edgeIndexMapper = EdgeIndexRecordMapper.Companion.create()
        private val encoder = edgeIndexMapper.encoder
        private val decoder = edgeIndexMapper.decoder

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
            type: int
            value: 1234
          targetField:
            type: string
            value: Starbucks
          direction: IN
          properties: {"key":"value"}
          indexCode: 1
          indexValuesMap: [{"update_ts":"ASC"}]
          version: 1
        - sourceField:
            type: int
            value: 1234
          targetField:
            type: long
            value: 5678
          direction: OUT
          properties: {"A":"B"}
          indexCode: 1
          indexValuesMap: [{"created_at":"DESC"}]
          version: 1
        """,
    )
    fun encodeTest(
        sourceField: Field,
        targetField: Field,
        direction: Direction,
        properties: Map<String, String>,
        indexCode: Int,
        indexValuesMap: List<Map<String, Order>>,
        version: Long,
    ) {
        val source = sourceField.cast()
        val target = targetField.cast()

        // Java (expected)
        val expected: KeyValue<ByteArray> =
            edgeStateCodec.encodeEdgeIndex(
                ImmutableEncodableEdgeIndex
                    .builder()
                    .version(version)
                    .source(source)
                    .target(target)
                    .properties(properties)
                    .tableCode(tableCode)
                    .indexCode(indexCode)
                    .direction(direction)
                    .indexValues(
                        indexValuesMap.flatMap {
                            it.map { (key, value) ->
                                ImmutableEncodableIndexValue
                                    .builder()
                                    .value(key)
                                    .order(value)
                                    .build()
                            }
                        },
                    ).build(),
                tableCode,
            )

        // Kotlin (actual)
        val actualKey =
            encoder.encodeKey(
                EdgeIndexRecord.Key(
                    prefix =
                        EdgeIndexRecord.Key.Prefix.of(
                            tableCode = tableCode,
                            directedSource = if (direction == Direction.OUT) source else target,
                            direction = KotlinDirection.of(direction.code),
                            indexCode = indexCode,
                            indexValues = indexValuesMap.flatMap { it.map { (key, value) -> EdgeIndexRecord.Key.IndexValue(key, value) } },
                        ),
                    suffix =
                        EdgeIndexRecord.Key.Suffix(
                            restIndexValues = emptyList(),
                            directedTarget = if (direction == Direction.OUT) target else source,
                        ),
                ),
            )

        val actualValue =
            encoder.encodeValue(
                edge =
                    EdgeIndexRecord.Value(
                        version = version,
                        properties = properties.map { (key, value) -> xxHash32Wrapper.stringHash(key) to value }.toMap(),
                    ),
            )

        assertArrayEquals(expected.key(), actualKey)
        assertArrayEquals(expected.value(), actualValue)
    }

    @ObjectSourceParameterizedTest
    @TableSource(
        """
        - OYdPnzTsiqTtg4DrsoXsiqQAK7UfmNkpfCmDK4AAAAE0dXBkYXRlX3RzACuAAATS | LIAAAAAAAAABKzPZOuA0dmFsdWUA
        - 68VKEyuAAATSK7UfmNkpfCmCK4AAAAHLnI2anouam6Cei/8sgAAAAAAAFi4=   | LIAAAAAAAAABK5Blmk00QgA=
        """,
    )
    fun decodedKeyTest(
        encodedKey: String,
        encodedValue: String,
    ) {
        val key: ByteArray = Base64.getDecoder().decode(encodedKey)
        val value: ByteArray = Base64.getDecoder().decode(encodedValue)

        // Java (expected)
        val expected: StoredEdgeIndex =
            edgeStateCodec.decodeToStoredEdgeIndex(
                ImmutableKeyValue
                    .builder<ByteArray>()
                    .key(key)
                    .value(value)
                    .build(),
            )

        // Kotlin (actual)
        val actual: EdgeIndexRecord = decoder.decode(key, value)
        val actualKey: EdgeIndexRecord.Key = actual.key

        assertEquals(expected.directedSource(), actualKey.prefix.directedSource)
        assertEquals(expected.directedTarget(), actualKey.suffix.directedTarget)
        assertEquals(expected.direction().code, actualKey.prefix.direction.code)
        assertEquals(expected.indexCode(), actualKey.prefix.indexCode)

        assertEquals(expected.version(), actual.value.version)
        for (i in expected.indexValues().indices) {
            assertEquals(expected.indexValues()[i], actualKey.prefix.indexValues[i].value)
        }
        expected.properties().forEach { (key, value) ->
            assertEquals(value, actual.value.properties[key])
        }
    }
}
