package com.kakao.actionbase.core.edge.mapper

import com.kakao.actionbase.core.edge.record.EdgeCacheRecord
import com.kakao.actionbase.core.java.codec.common.hbase.Order
import com.kakao.actionbase.core.metadata.common.Direction

import kotlin.test.assertEquals

import org.junit.jupiter.api.Test

private fun compareUnsigned(
    a: ByteArray,
    b: ByteArray,
): Int {
    val len = minOf(a.size, b.size)
    for (i in 0 until len) {
        val diff = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
        if (diff != 0) return diff
    }
    return a.size - b.size
}

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
     * Compound (permission ASC, createdAt DESC). Per-field [Order] is baked into
     * the qualifier bytes via OrderedBytes (DESC fields are bit-inverted), so an
     * unsigned-byte lex sort — the way HBase scans columns — yields the intended
     * compound order without any explicit scan-direction handling.
     *
     * | target | permission (ASC) | createdAt (DESC) |
     * |--------|------------------|------------------|
     * | t5     | "others"         | 1716388213000    |
     * | t2     | "me"             | 1744597404230    |
     * | t7     | "others"         | 1629251546000    |
     * | t3     | "others"         | 1766556337836    |
     * | t4     | "others"         | 1744692001289    |
     * | t6     | "others"         | 1682054196000    |
     *
     * | byte-sort | permission | createdAt     | reason                     |
     * |-----------|------------|---------------|----------------------------|
     * | t2        | "me"       | 1744597404230 | "me" < "others" (ASCII)    |
     * | t3        | "others"   | 1766556337836 | largest createdAt, DESC 1st|
     * | t4        | "others"   | 1744692001289 | ↓                          |
     * | t5        | "others"   | 1716388213000 | ↓                          |
     * | t6        | "others"   | 1682054196000 | ↓                          |
     * | t7        | "others"   | 1629251546000 | smallest, DESC last        |
     */
    @Test
    fun `compound mixed-direction qualifiers sort lex by (leading ASC, trailing DESC)`() {
        // schema: (permission ASC, createdAt DESC)
        val records =
            listOf(
                Triple("others", 1716388213000L, "t5"),
                Triple("me", 1744597404230L, "t2"),
                Triple("others", 1629251546000L, "t7"),
                Triple("others", 1766556337836L, "t3"),
                Triple("others", 1744692001289L, "t4"),
                Triple("others", 1682054196000L, "t6"),
            )
        val encoded =
            records.map { (perm, ts, target) ->
                val q =
                    EdgeCacheRecord.Qualifier(
                        cacheValues =
                            listOf(
                                EdgeCacheRecord.Qualifier.CacheValue(perm, Order.ASC),
                                EdgeCacheRecord.Qualifier.CacheValue(ts, Order.DESC),
                            ),
                        directedTarget = target,
                    )
                target to mapper.encoder.encodeQualifier(q)
            }

        val sorted = encoded.sortedWith { a, b -> compareUnsigned(a.second, b.second) }

        assertEquals(listOf("t2", "t3", "t4", "t5", "t6", "t7"), sorted.map { it.first })
    }

    /**
     * Predicate `WHERE permission='others'` on schema (permission ASC, createdAt DESC).
     *
     * |   bound   |           encoded bytes           |
     * |-----------|-----------------------------------|
     * | start (≤) | encode("others", ASC)             |
     * | stop  (<) | encode("others", ASC) + plusOne() |
     *
     * | qualifier (permission | createdAt(DESC) | target) | vs [start, stop) |
     * |---------------------------------------------------|------------------|
     * | "me"                  | 1000L          | "tx"     | < start          |
     * | "others"              | 1000L          | "tx"     | inside           |
     * | "public"              | 1000L          | "tx"     | ≥ stop           |
     *
     * `WHERE permission='others'` becomes a contiguous qualifier range
     * `[encode("others"), encode("others")+1)` — the byte-range translated
     * into a single ColumnRangeFilter by the seek API.
     */
    @Test
    fun `predicate prefix start-stop bracket matching qualifiers and exclude others`() {
        val comparator = Comparator<ByteArray> { a, b -> compareUnsigned(a, b) }
        // Eq("permission", "others") on an ASC field → same item on both start and stop sides.
        val eqItem = QualifierStartStopItem("others", Order.ASC, true)
        val startStops = listOf(QualifierStartStop(eqItem, eqItem))
        val start = mapper.encoder.encodeQualifierPrefixStart(startStops)
        val stop = mapper.encoder.encodeQualifierPrefixStop(startStops)

        fun encodeFor(perm: String): ByteArray {
            val q =
                EdgeCacheRecord.Qualifier(
                    cacheValues =
                        listOf(
                            EdgeCacheRecord.Qualifier.CacheValue(perm, Order.ASC),
                            EdgeCacheRecord.Qualifier.CacheValue(1_000L, Order.DESC),
                        ),
                    directedTarget = "tx",
                )
            return mapper.encoder.encodeQualifier(q)
        }

        val matching = encodeFor("others")
        val before = encodeFor("me")
        val after = encodeFor("public") // 'p' > 'o'

        // matching qualifier sits in [start, stop)
        assert(comparator.compare(start, matching) < 0) { "start should be < matching" }
        assert(comparator.compare(matching, stop) < 0) { "matching should be < stop" }
        // non-matching qualifiers fall outside
        assert(comparator.compare(before, start) < 0) { "permission='me' must precede start" }
        assert(comparator.compare(after, stop) >= 0) { "permission='public' must not precede stop" }
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
