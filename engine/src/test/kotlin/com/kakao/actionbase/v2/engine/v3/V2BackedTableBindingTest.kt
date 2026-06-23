package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.v2.core.types.StructType as V2StructType
import com.kakao.actionbase.v2.engine.sql.DataFrame as V2DataFrame
import com.kakao.actionbase.v2.engine.sql.Row as V2Row

import com.kakao.actionbase.core.edge.mapper.EdgeCacheRecordMapper
import com.kakao.actionbase.core.edge.record.EdgeCacheRecord
import com.kakao.actionbase.core.edge.record.EdgeGroupRecord
import com.kakao.actionbase.core.java.codec.common.hbase.Order
import com.kakao.actionbase.core.metadata.common.Direction
import com.kakao.actionbase.core.state.SpecialStateValue
import com.kakao.actionbase.core.state.State
import com.kakao.actionbase.core.state.StateValue
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.v2.core.code.hbase.Constants
import com.kakao.actionbase.v2.core.types.DataType
import com.kakao.actionbase.v2.core.types.Field
import com.kakao.actionbase.v2.engine.v3.V2BackedTableBinding.Companion.mergeQualifiers
import com.kakao.actionbase.v2.engine.v3.V2BackedTableBinding.Companion.specialStateValueToNull

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

import org.apache.hadoop.hbase.client.Delete
import org.apache.hadoop.hbase.client.Put
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class V2BackedTableBindingTest {
    @Nested
    inner class MergeQualifiers {
        private fun groupRecord(
            qualifier: List<Any?>,
            value: Long,
        ): EdgeGroupRecord =
            EdgeGroupRecord(
                key =
                    EdgeGroupRecord.Key.of(
                        directedSource = "source",
                        tableCode = 100,
                        direction = Direction.OUT,
                        groupCode = 1,
                    ),
                qualifier = EdgeGroupRecord.Qualifier(qualifier),
                value = value,
            )

        @Test
        fun `empty list returns empty map`() {
            val result = emptyList<EdgeGroupRecord>().mergeQualifiers()
            assertTrue(result.isEmpty())
        }

        @Test
        fun `same qualifier records are summed`() {
            val records =
                listOf(
                    groupRecord(listOf("a"), 3L),
                    groupRecord(listOf("a"), 5L),
                )
            val result = records.mergeQualifiers()
            assertEquals(1, result.size)
            assertEquals(8L, result[EdgeGroupRecord.Qualifier(listOf("a"))])
        }

        @Test
        fun `qualifier summing to zero is filtered out`() {
            val records =
                listOf(
                    groupRecord(listOf("a"), 1L),
                    groupRecord(listOf("a"), -1L),
                )
            val result = records.mergeQualifiers()
            assertTrue(result.isEmpty())
        }

        @Test
        fun `different qualifiers are independent`() {
            val records =
                listOf(
                    groupRecord(listOf("a"), 3L),
                    groupRecord(listOf("b"), 7L),
                )
            val result = records.mergeQualifiers()
            assertEquals(2, result.size)
            assertEquals(3L, result[EdgeGroupRecord.Qualifier(listOf("a"))])
            assertEquals(7L, result[EdgeGroupRecord.Qualifier(listOf("b"))])
        }

        @Test
        fun `mixed qualifiers with some summing to zero`() {
            val records =
                listOf(
                    groupRecord(listOf("a"), 1L),
                    groupRecord(listOf("a"), -1L),
                    groupRecord(listOf("b"), 5L),
                    groupRecord(listOf("b"), 3L),
                )
            val result = records.mergeQualifiers()
            assertEquals(1, result.size)
            assertEquals(8L, result[EdgeGroupRecord.Qualifier(listOf("b"))])
        }
    }

    /**
     * Verifies that EdgeCacheRecord encodes into HBase Put/Delete correctly,
     * matching the pattern used in V2BackedTableBinding.buildHBaseMutations().
     */
    @Nested
    inner class CacheHBaseMutations {
        private val mapper = EdgeCacheRecordMapper.create()

        private fun cacheRecord(
            source: Any = "userA",
            target: Any = "postX",
            direction: Direction = Direction.OUT,
            cacheCode: Int = 42,
            version: Long = 1L,
            cacheValues: List<EdgeCacheRecord.Qualifier.CacheValue> =
                listOf(
                    EdgeCacheRecord.Qualifier.CacheValue(value = version, order = Order.DESC),
                ),
        ) = EdgeCacheRecord(
            key =
                EdgeCacheRecord.Key.of(
                    directedSource = source,
                    tableCode = 100,
                    direction = direction,
                    cacheCode = cacheCode,
                ),
            qualifier =
                EdgeCacheRecord.Qualifier(
                    cacheValues = cacheValues,
                    directedTarget = target,
                ),
            value =
                EdgeCacheRecord.Value(
                    version = version,
                    properties = emptyMap(),
                ),
        )

        /**
         * |          row key         |  qualifier |   value   |
         * |--------------------------|------------|-----------|
         * | hash|userA|100|-6|OUT|42 | ~1 | postX | version=1 |
         *
         * Verifies: Put contains row key + column family with qualifier.
         */
        @Test
        fun `cache record encodes to Put with qualifier`() {
            val record = cacheRecord()
            val encoded = mapper.encoder.encode(record)

            val put =
                Put(encoded.key)
                    .addColumn(Constants.DEFAULT_COLUMN_FAMILY, encoded.qualifier, encoded.value)

            // Put should have the row key
            assertTrue(put.row.isNotEmpty())
            // Put should contain our column family
            val familyCells = put.familyCellMap[Constants.DEFAULT_COLUMN_FAMILY]
            assertTrue(!familyCells.isNullOrEmpty())
        }

        /**
         * |  qualifier |
         * |------------|
         * | ~1 | postX |
         *
         * Verifies: encoded qualifier decodes back to directedTarget=postX.
         */
        @Test
        fun `cache Put qualifier matches encoded qualifier`() {
            val record = cacheRecord()
            val encoded = mapper.encoder.encode(record)

            // Qualifier should be non-empty and different from DEFAULT_QUALIFIER
            assertTrue(encoded.qualifier.isNotEmpty())

            // Decode qualifier back and verify target
            val decodedQualifier = mapper.decoder.decodeQualifier(encoded.qualifier)
            assertEquals("postX", decodedQualifier.directedTarget)
        }

        /**
         * Delete targets:
         * |          row key         |  qualifier |
         * |--------------------------|------------|
         * | hash|userA|100|-6|OUT|42 | ~1 | postX |
         *
         * Verifies: Delete has row key + family-level column operation (not whole-row).
         */
        @Test
        fun `cache Delete targets specific qualifier column`() {
            val record = cacheRecord()
            val encodedKey = mapper.encoder.encodeKey(record.key)
            val encodedQualifier = mapper.encoder.encodeQualifier(record.qualifier)

            val delete =
                Delete(encodedKey)
                    .addColumns(Constants.DEFAULT_COLUMN_FAMILY, encodedQualifier)

            // Delete should target specific row
            assertTrue(delete.row.isNotEmpty())
            // Delete should have family-level operations (not whole-row delete)
            val familyCells = delete.familyCellMap[Constants.DEFAULT_COLUMN_FAMILY]
            assertTrue(!familyCells.isNullOrEmpty())
        }

        /**
         * |          row key         |    qualifier    |   value   |
         * |--------------------------|-----------------|-----------|
         * | hash|user1|100|-6|OUT|42 | ~5 | product42  | version=5 |
         *
         * Verifies: encode → decode round-trip preserves all fields.
         */
        @Test
        fun `cache record round-trip through HBase encoding`() {
            val record = cacheRecord(source = "user1", target = "product42", version = 5L)
            val encoded = mapper.encoder.encode(record)

            // Simulate HBase read: decode from encoded key, qualifier, value
            val decoded = mapper.decoder.decode(encoded.key, encoded.qualifier, encoded.value)

            assertEquals(record.key.directedSource, decoded.key.directedSource)
            assertEquals(record.key.direction, decoded.key.direction)
            assertEquals(record.key.cacheCode, decoded.key.cacheCode)
            assertEquals(record.qualifier.directedTarget, decoded.qualifier.directedTarget)
            assertEquals(record.value.version, decoded.value.version)
        }

        /**
         * |         row key         | qualifier  |   value   |
         * |-------------------------|------------|-----------|
         * | hash|postX|100|-6|IN|42 | ~1 | userA | version=1 |
         *
         * IN direction: directedSource=postX (original target), directedTarget=userA (original source).
         */
        @Test
        fun `IN direction cache record encodes correctly`() {
            val record = cacheRecord(source = "postX", target = "userA", direction = Direction.IN)
            val encoded = mapper.encoder.encode(record)

            val decoded = mapper.decoder.decode(encoded.key, encoded.qualifier, encoded.value)
            assertEquals(Direction.IN, decoded.key.direction)
            assertEquals("postX", decoded.key.directedSource)
            assertEquals("userA", decoded.qualifier.directedTarget)
        }

        /**
         * userA
         * |         row key          |
         * |--------------------------|
         * | hash|userA|100|-6|OUT|42 |
         *
         * userB
         * |         row key          |
         * |--------------------------|
         * | hash|userB|100|-6|OUT|42 |
         *
         * Different sources → different row keys.
         */
        @Test
        fun `different cache records produce different row keys for different sources`() {
            val record1 = cacheRecord(source = "userA")
            val record2 = cacheRecord(source = "userB")

            val key1 = mapper.encoder.encodeKey(record1.key)
            val key2 = mapper.encoder.encodeKey(record2.key)

            assertTrue(!key1.contentEquals(key2))
        }

        /**
         * Wide row — same source, different targets:
         * |          row key          | qualifier (DESC) |   value   |
         * |---------------------------|------------------|-----------|
         * | hash|userA|100|-6|OUT|42  | ~1 | post1       | version=1 |
         * |                           | ~1 | post2       | version=1 |
         *
         * Same row key, different qualifiers.
         */
        @Test
        fun `same source different targets share row key but differ in qualifier`() {
            val record1 = cacheRecord(source = "userA", target = "post1")
            val record2 = cacheRecord(source = "userA", target = "post2")

            val key1 = mapper.encoder.encodeKey(record1.key)
            val key2 = mapper.encoder.encodeKey(record2.key)
            val qual1 = mapper.encoder.encodeQualifier(record1.qualifier)
            val qual2 = mapper.encoder.encodeQualifier(record2.qualifier)

            // Wide Row: same source → same row key
            assertTrue(key1.contentEquals(key2))
            // Different targets → different qualifiers
            assertTrue(!qual1.contentEquals(qual2))
        }
    }

    @Nested
    inner class SpecialStateValueToNull {
        @Test
        fun `normal properties are preserved`() {
            val state =
                State(
                    active = true,
                    version = 1L,
                    createdAt = 1L,
                    deletedAt = null,
                    properties = mapOf("name" to StateValue(1L, "hello")),
                )
            val result = state.specialStateValueToNull()
            assertEquals("hello", result.properties["name"]?.value)
        }

        @Test
        fun `DELETED special value is converted to null`() {
            val state =
                State(
                    active = true,
                    version = 1L,
                    createdAt = 1L,
                    deletedAt = null,
                    properties = mapOf("name" to StateValue(1L, SpecialStateValue.DELETED.code())),
                )
            val result = state.specialStateValueToNull()
            assertNull(result.properties["name"]?.value)
            // version is preserved
            assertEquals(1L, result.properties["name"]?.version)
        }

        @Test
        fun `UNSET special value is converted to null`() {
            val state =
                State(
                    active = true,
                    version = 1L,
                    createdAt = 1L,
                    deletedAt = null,
                    properties = mapOf("name" to StateValue(1L, SpecialStateValue.UNSET.code())),
                )
            val result = state.specialStateValueToNull()
            assertNull(result.properties["name"]?.value)
            assertEquals(1L, result.properties["name"]?.version)
        }

        @Test
        fun `empty properties remain empty`() {
            val state =
                State(
                    active = false,
                    version = 1L,
                    createdAt = null,
                    deletedAt = null,
                    properties = emptyMap(),
                )
            val result = state.specialStateValueToNull()
            assertTrue(result.properties.isEmpty())
        }

        @Test
        fun `non-property fields are preserved`() {
            val state =
                State(
                    active = true,
                    version = 5L,
                    createdAt = 3L,
                    deletedAt = 4L,
                    properties = mapOf("field" to StateValue(5L, SpecialStateValue.DELETED.code())),
                )
            val result = state.specialStateValueToNull()
            assertEquals(true, result.active)
            assertEquals(5L, result.version)
            assertEquals(3L, result.createdAt)
            assertEquals(4L, result.deletedAt)
        }

        @Test
        fun `mixed properties with normal and special values`() {
            val state =
                State(
                    active = true,
                    version = 1L,
                    createdAt = 1L,
                    deletedAt = null,
                    properties =
                        mapOf(
                            "normal" to StateValue(1L, "value"),
                            "deleted" to StateValue(1L, SpecialStateValue.DELETED.code()),
                            "unset" to StateValue(1L, SpecialStateValue.UNSET.code()),
                            "number" to StateValue(1L, 42),
                        ),
                )
            val result = state.specialStateValueToNull()
            assertEquals("value", result.properties["normal"]?.value)
            assertNull(result.properties["deleted"]?.value)
            assertNull(result.properties["unset"]?.value)
            assertEquals(42, result.properties["number"]?.value)
        }
    }

    @Nested
    inner class V2DataFrameToV3 {
        @Test
        fun `empty rows produce empty v3 DataFrame with total 0`() {
            val v2 = V2DataFrame(rows = emptyList(), schema = V2StructType())

            val v3 = v2.toV3()

            assertTrue(v3.rows.isEmpty())
            assertEquals(0L, v3.total)
            assertNull(v3.offset)
            assertEquals(false, v3.hasNext)
        }

        @Test
        fun `v2 edge field names are translated to v3 names in schema and rows`() {
            val v2Schema =
                V2StructType(
                    arrayOf(
                        Field("src", DataType.STRING, false, "source field"),
                        Field("tgt", DataType.STRING, false, "target field"),
                        Field("ts", DataType.LONG, false, "version field"),
                        Field("dir", DataType.STRING, false, "direction field"),
                    ),
                )
            val v2 =
                V2DataFrame(
                    rows = listOf(V2Row(arrayOf("user", "post", 100L, "OUT"))),
                    schema = v2Schema,
                )

            val v3 = v2.toV3()

            assertEquals(listOf("source", "target", "version", "direction"), v3.schema.fields.map { it.name })
            assertEquals("user", v3.rows[0].data["source"])
            assertEquals("post", v3.rows[0].data["target"])
            assertEquals(100L, v3.rows[0].data["version"])
            assertEquals("OUT", v3.rows[0].data["direction"])
        }

        @Test
        fun `non-edge field names are passed through unchanged`() {
            val v2Schema = V2StructType(arrayOf(Field("score", DataType.INT, true, "")))
            val v2 = V2DataFrame(rows = listOf(V2Row(arrayOf(42))), schema = v2Schema)

            val v3 = v2.toV3()

            assertEquals("score", v3.schema.fields[0].name)
            assertEquals(42, v3.rows[0].data["score"])
        }

        @Test
        fun `user property fields whose name collides with a v3 system field are escaped with backticks`() {
            val v2Schema =
                V2StructType(
                    arrayOf(
                        Field("ts", DataType.LONG, false, ""),
                        Field("src", DataType.STRING, false, ""),
                        Field("tgt", DataType.STRING, false, ""),
                        // User properties whose names collide with V3 system fields.
                        Field("version", DataType.LONG, true, ""),
                        Field("source", DataType.STRING, true, ""),
                        Field("target", DataType.STRING, true, ""),
                        Field("direction", DataType.STRING, true, ""),
                    ),
                )
            val v2 =
                V2DataFrame(
                    rows = listOf(V2Row(arrayOf(100L, "edgeSrc", "edgeTgt", 999L, "userSrc", "userTgt", "userDir"))),
                    schema = v2Schema,
                )

            val v3 = v2.toV3()

            // Edge fields keep their natural V3 names.
            assertEquals(
                listOf("version", "source", "target", "`version`", "`source`", "`target`", "`direction`"),
                v3.schema.fields.map { it.name },
            )
            // System values at natural keys.
            assertEquals(100L, v3.rows[0].data["version"])
            assertEquals("edgeSrc", v3.rows[0].data["source"])
            assertEquals("edgeTgt", v3.rows[0].data["target"])
            // User values at backticked keys.
            assertEquals(999L, v3.rows[0].data["`version`"])
            assertEquals("userSrc", v3.rows[0].data["`source`"])
            assertEquals("userTgt", v3.rows[0].data["`target`"])
            assertEquals("userDir", v3.rows[0].data["`direction`"])
        }

        @Test
        fun `non-colliding user property fields are not escaped`() {
            val v2Schema =
                V2StructType(
                    arrayOf(
                        Field("createdAt", DataType.LONG, false, ""),
                        Field("permission", DataType.STRING, true, ""),
                    ),
                )
            val v2 = V2DataFrame(rows = listOf(V2Row(arrayOf(50L, "na"))), schema = v2Schema)

            val v3 = v2.toV3()

            assertEquals(listOf("createdAt", "permission"), v3.schema.fields.map { it.name })
            assertEquals(50L, v3.rows[0].data["createdAt"])
            assertEquals("na", v3.rows[0].data["permission"])
        }

        @Test
        fun `each v2 DataType maps to the corresponding v3 PrimitiveType`() {
            val v2Schema =
                V2StructType(
                    arrayOf(
                        Field("b", DataType.BOOLEAN, false, ""),
                        Field("by", DataType.BYTE, false, ""),
                        Field("s", DataType.SHORT, false, ""),
                        Field("i", DataType.INT, false, ""),
                        Field("l", DataType.LONG, false, ""),
                        Field("f", DataType.FLOAT, false, ""),
                        Field("d", DataType.DOUBLE, false, ""),
                        Field("str", DataType.STRING, false, ""),
                    ),
                )
            val v2 = V2DataFrame(rows = emptyList(), schema = v2Schema)

            val types =
                v2
                    .toV3()
                    .schema.fields
                    .map { it.type }

            assertEquals(
                listOf(
                    PrimitiveType.BOOLEAN,
                    PrimitiveType.BYTE,
                    PrimitiveType.SHORT,
                    PrimitiveType.INT,
                    PrimitiveType.LONG,
                    PrimitiveType.FLOAT,
                    PrimitiveType.DOUBLE,
                    PrimitiveType.STRING,
                ),
                types,
            )
        }

        @Test
        fun `JSON and DECIMAL DataTypes both map to OBJECT PrimitiveType`() {
            val v2Schema =
                V2StructType(
                    arrayOf(
                        Field("payload", DataType.JSON, true, ""),
                        Field("amount", DataType.DECIMAL, true, ""),
                    ),
                )
            val v2 = V2DataFrame(rows = emptyList(), schema = v2Schema)

            val v3 = v2.toV3()

            assertEquals(PrimitiveType.OBJECT, v3.schema.fields[0].type)
            assertEquals(PrimitiveType.OBJECT, v3.schema.fields[1].type)
        }

        @Test
        fun `field comment and nullable flag are preserved in v3 schema`() {
            val v2Schema =
                V2StructType(
                    arrayOf(
                        Field("score", DataType.INT, true, "user score"),
                        Field("name", DataType.STRING, false, "display name"),
                    ),
                )
            val v2 = V2DataFrame(rows = emptyList(), schema = v2Schema)

            val v3 = v2.toV3()

            assertEquals("user score", v3.schema.fields[0].comment)
            assertEquals(true, v3.schema.fields[0].nullable)
            assertEquals("display name", v3.schema.fields[1].comment)
            assertEquals(false, v3.schema.fields[1].nullable)
        }

        @Test
        fun `total parameter overrides rows size`() {
            val v2Schema = V2StructType(arrayOf(Field("a", DataType.INT, false, "")))
            val v2 =
                V2DataFrame(
                    rows = listOf(V2Row(arrayOf(1)), V2Row(arrayOf(2))),
                    schema = v2Schema,
                )

            val v3 = v2.toV3(total = 999L)

            assertEquals(999L, v3.total)
            assertEquals(2, v3.rows.size)
        }

        @Test
        fun `total defaults to rows size when not provided`() {
            val v2Schema = V2StructType(arrayOf(Field("a", DataType.INT, false, "")))
            val v2 =
                V2DataFrame(
                    rows = listOf(V2Row(arrayOf(1)), V2Row(arrayOf(2)), V2Row(arrayOf(3))),
                    schema = v2Schema,
                )

            assertEquals(3L, v2.toV3().total)
        }

        @Test
        fun `offset is preserved when hasNext is true`() {
            val v2Schema = V2StructType(arrayOf(Field("a", DataType.INT, false, "")))
            val v2 =
                V2DataFrame(
                    rows = listOf(V2Row(arrayOf(1))),
                    schema = v2Schema,
                    offsets = listOf("offset-token"),
                    hasNext = listOf(true),
                )

            val v3 = v2.toV3()

            assertEquals("offset-token", v3.offset)
            assertEquals(true, v3.hasNext)
        }

        @Test
        fun `offset is dropped when hasNext is false`() {
            val v2Schema = V2StructType(arrayOf(Field("a", DataType.INT, false, "")))
            val v2 =
                V2DataFrame(
                    rows = listOf(V2Row(arrayOf(1))),
                    schema = v2Schema,
                    offsets = listOf("offset-token"),
                    hasNext = listOf(false),
                )

            val v3 = v2.toV3()

            assertNull(v3.offset)
            assertEquals(false, v3.hasNext)
        }

        @Test
        fun `offset is dropped when hasNext list is empty`() {
            val v2Schema = V2StructType(arrayOf(Field("a", DataType.INT, false, "")))
            val v2 =
                V2DataFrame(
                    rows = listOf(V2Row(arrayOf(1))),
                    schema = v2Schema,
                    offsets = listOf("offset-token"),
                    hasNext = emptyList(),
                )

            val v3 = v2.toV3()

            assertNull(v3.offset)
            assertEquals(false, v3.hasNext)
        }

        @Test
        fun `offset is null when offsets list has multiple entries`() {
            val v2Schema = V2StructType(arrayOf(Field("a", DataType.INT, false, "")))
            val v2 =
                V2DataFrame(
                    rows = listOf(V2Row(arrayOf(1))),
                    schema = v2Schema,
                    offsets = listOf("a", "b"),
                    hasNext = listOf(true),
                )

            val v3 = v2.toV3()

            assertNull(v3.offset)
            assertEquals(false, v3.hasNext)
        }
    }
}
