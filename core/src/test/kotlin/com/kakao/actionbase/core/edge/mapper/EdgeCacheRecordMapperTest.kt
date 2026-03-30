package com.kakao.actionbase.core.edge.mapper

import com.kakao.actionbase.core.edge.record.EdgeCacheRecord
import com.kakao.actionbase.core.java.codec.common.hbase.Order
import com.kakao.actionbase.core.metadata.common.Direction

import kotlin.test.assertEquals

import org.junit.jupiter.api.Test

/**
 * EdgeCache (Wide Row) layout:
 *
 * |                                  row key                                |            qualifier           |        value         |
 * |-------------------------------------------------------------------------|--------------------------------|----------------------|
 * | xxhash32 | directed_source | table_code | EDGE_CACHE | direction | code | cache_values | directed target | version | properties |
 *
 * - Row Key: one row per (hash, source, table, direction, code)
 * - Qualifier: cache values + directed target packed into column qualifier
 * - Value: version + properties
 */
class EdgeCacheRecordMapperTest {
    private val mapper = EdgeCacheRecordMapper.create()

    /**
     * |                       row key                         |
     * |-------------------------------------------------------|
     * | xxhash32 | "user1" | 100 | -6 (EDGE_CACHE) | OUT | 42 |
     */
    @Test
    fun `encode and decode key round-trip`() {
        val key =
            EdgeCacheRecord.Key.of(
                directedSource = "user1",
                tableCode = 100,
                direction = Direction.OUT,
                cacheCode = 42,
            )

        val encoded = mapper.encoder.encodeKey(key)
        val decoded = mapper.decoder.decodeKey(encoded)

        assertEquals("user1", decoded.directedSource)
        assertEquals(100, decoded.tableCode)
        assertEquals(key.recordTypeCode, decoded.recordTypeCode)
        assertEquals(Direction.OUT, decoded.direction)
        assertEquals(42, decoded.cacheCode)
    }

    /**
     * |                    qualifier                   |
     * |------------------------------------------------|
     * | 1000L (DESC) | "category_a" (ASC) | "product1" |
     */
    @Test
    fun `encode and decode qualifier round-trip`() {
        val qualifier =
            EdgeCacheRecord.Qualifier(
                cacheValues =
                    listOf(
                        EdgeCacheRecord.Qualifier.CacheValue(value = 1000L, order = Order.DESC),
                        EdgeCacheRecord.Qualifier.CacheValue(value = "category_a", order = Order.ASC),
                    ),
                directedTarget = "product1",
            )

        val encoded = mapper.encoder.encodeQualifier(qualifier)
        val decoded = mapper.decoder.decodeQualifier(encoded)

        assertEquals(qualifier.cacheValues.size, decoded.cacheValues.size)
        qualifier.cacheValues.zip(decoded.cacheValues).forEach { (expected, actual) ->
            assertEquals(expected.value, actual.value)
            assertEquals(expected.order, actual.order)
        }
        assertEquals(qualifier.directedTarget, decoded.directedTarget)
    }

    /**
     * | qualifier |
     * |-----------|
     * | "target1" |
     *
     * No cache values — qualifier contains only the directed target.
     */
    @Test
    fun `encode and decode qualifier with no index values`() {
        val qualifier =
            EdgeCacheRecord.Qualifier(
                cacheValues = emptyList(),
                directedTarget = "target1",
            )

        val encoded = mapper.encoder.encodeQualifier(qualifier)
        val decoded = mapper.decoder.decodeQualifier(encoded)

        assertEquals(0, decoded.cacheValues.size)
        assertEquals("target1", decoded.directedTarget)
    }

    /**
     * |                 row key                |         qualifier         |              value               |
     * |----------------------------------------|---------------------------|----------------------------------|
     * | xxhash32 | "user1" | 200 | -6 | IN | 7 | 999L (DESC) | "product42" | version=5 | {101:"hello",202:42} |
     */
    @Test
    fun `encode and decode full record round-trip`() {
        val record =
            EdgeCacheRecord(
                key =
                    EdgeCacheRecord.Key.of(
                        directedSource = "user1",
                        tableCode = 200,
                        direction = Direction.IN,
                        cacheCode = 7,
                    ),
                qualifier =
                    EdgeCacheRecord.Qualifier(
                        cacheValues =
                            listOf(
                                EdgeCacheRecord.Qualifier.CacheValue(value = 999L, order = Order.DESC),
                            ),
                        directedTarget = "product42",
                    ),
                value =
                    EdgeCacheRecord.Value(
                        version = 5L,
                        properties = mapOf(101 to "hello", 202 to 42L),
                    ),
            )

        val hbaseRecord = mapper.encoder.encode(record)
        val decoded = mapper.decoder.decode(hbaseRecord.key, hbaseRecord.qualifier, hbaseRecord.value)

        // key
        assertEquals(record.key.directedSource, decoded.key.directedSource)
        assertEquals(record.key.tableCode, decoded.key.tableCode)
        assertEquals(record.key.direction, decoded.key.direction)
        assertEquals(record.key.cacheCode, decoded.key.cacheCode)

        // qualifier
        assertEquals(record.qualifier.cacheValues.size, decoded.qualifier.cacheValues.size)
        assertEquals(999L, decoded.qualifier.cacheValues[0].value)
        assertEquals(Order.DESC, decoded.qualifier.cacheValues[0].order)
        assertEquals("product42", decoded.qualifier.directedTarget)

        // value
        assertEquals(5L, decoded.value.version)
        assertEquals("hello", decoded.value.properties[101])
        assertEquals(42L, decoded.value.properties[202])
    }

    /**
     * |                 row key                |       qualifier        |   value   |
     * |----------------------------------------|------------------------|-----------|
     * | xxhash32 | 12345L | 100 | -6 | OUT | 1 | 1000L (DESC) | 67890L  | version=1 |
     *
     * Long type source/target instead of String.
     */
    @Test
    fun `encode and decode with long source and target`() {
        val record =
            EdgeCacheRecord(
                key =
                    EdgeCacheRecord.Key.of(
                        directedSource = 12345L,
                        tableCode = 100,
                        direction = Direction.OUT,
                        cacheCode = 1,
                    ),
                qualifier =
                    EdgeCacheRecord.Qualifier(
                        cacheValues =
                            listOf(
                                EdgeCacheRecord.Qualifier.CacheValue(value = 1000L, order = Order.DESC),
                            ),
                        directedTarget = 67890L,
                    ),
                value =
                    EdgeCacheRecord.Value(
                        version = 1L,
                        properties = emptyMap(),
                    ),
            )

        val hbaseRecord = mapper.encoder.encode(record)
        val decoded = mapper.decoder.decode(hbaseRecord.key, hbaseRecord.qualifier, hbaseRecord.value)

        assertEquals(12345L, decoded.key.directedSource)
        assertEquals(100, decoded.key.tableCode)
        assertEquals(Direction.OUT, decoded.key.direction)
        assertEquals(1, decoded.key.cacheCode)

        assertEquals(1, decoded.qualifier.cacheValues.size)
        assertEquals(1000L, decoded.qualifier.cacheValues[0].value)
        assertEquals(Order.DESC, decoded.qualifier.cacheValues[0].order)
        assertEquals(67890L, decoded.qualifier.directedTarget)

        assertEquals(1L, decoded.value.version)
        assertEquals(0, decoded.value.properties.size)
    }

    /**
     * |                   row key                 |  qualifier |
     * |-------------------------------------------|------------|
     * | xxhash32 | "product1" | 100 | -6 | IN | 1 | "user1"    |
     *
     * IN direction: directedSource is the original target, directedTarget is the original source.
     */
    @Test
    fun `IN direction swaps source and target in toEdge`() {
        val record =
            EdgeCacheRecord(
                key =
                    EdgeCacheRecord.Key.of(
                        directedSource = "product1",
                        tableCode = 100,
                        direction = Direction.IN,
                        cacheCode = 1,
                    ),
                qualifier =
                    EdgeCacheRecord.Qualifier(
                        cacheValues = emptyList(),
                        directedTarget = "user1",
                    ),
                value =
                    EdgeCacheRecord.Value(
                        version = 1L,
                        properties = emptyMap(),
                    ),
            )

        // IN direction: directedSource is actually target, directedTarget is actually source
        assertEquals(Direction.IN, record.key.direction)
        assertEquals("product1", record.key.directedSource)
        assertEquals("user1", record.qualifier.directedTarget)
    }

    /**
     * |                  row key                |                 qualifier               |   value   |
     * |-----------------------------------------|-----------------------------------------|-----------|
     * | xxhash32 | "user1" | 100 | -6 | OUT | 1 | null (DESC) | "hello" (ASC) | "target1" | version=1 |
     *
     * Nullable cache value in qualifier — encoder writes null marker, decoder reads it back.
     */
    @Test
    fun `encode and decode qualifier with null cache value`() {
        val qualifier =
            EdgeCacheRecord.Qualifier(
                cacheValues =
                    listOf(
                        EdgeCacheRecord.Qualifier.CacheValue(value = null, order = Order.DESC),
                        EdgeCacheRecord.Qualifier.CacheValue(value = "hello", order = Order.ASC),
                    ),
                directedTarget = "target1",
            )

        val encoded = mapper.encoder.encodeQualifier(qualifier)
        val decoded = mapper.decoder.decodeQualifier(encoded)

        assertEquals(2, decoded.cacheValues.size)
        assertEquals(null, decoded.cacheValues[0].value)
        assertEquals(Order.DESC, decoded.cacheValues[0].order)
        assertEquals("hello", decoded.cacheValues[1].value)
        assertEquals(Order.ASC, decoded.cacheValues[1].order)
        assertEquals("target1", decoded.directedTarget)
    }
}
