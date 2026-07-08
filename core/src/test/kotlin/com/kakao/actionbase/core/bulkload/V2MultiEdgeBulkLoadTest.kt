package com.kakao.actionbase.core.bulkload

import com.kakao.actionbase.core.codec.XXHash32Wrapper
import com.kakao.actionbase.core.edge.mapper.EdgeCacheRecordMapper
import com.kakao.actionbase.core.edge.mapper.EdgeCountRecordMapper
import com.kakao.actionbase.core.edge.mapper.EdgeGroupRecordMapper
import com.kakao.actionbase.core.edge.mapper.EdgeIndexRecordMapper
import com.kakao.actionbase.core.edge.mapper.EdgeStateRecordMapper
import com.kakao.actionbase.core.edge.record.EdgeCacheRecord
import com.kakao.actionbase.core.edge.record.EdgeCountRecord
import com.kakao.actionbase.core.edge.record.EdgeGroupRecord
import com.kakao.actionbase.core.edge.record.EdgeIndexRecord
import com.kakao.actionbase.core.edge.record.EdgeStateRecord
import com.kakao.actionbase.core.java.codec.common.hbase.Order
import com.kakao.actionbase.core.metadata.common.Direction
import com.kakao.actionbase.core.state.StateValue

import java.util.Base64

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * see [com.kakao.actionbase.v2.core.code.MultiEdgeBulkEdgeEncoderTests]
 *
 * {
 *   "active": true,
 *   "ts": 1,
 *   "src": 123,
 *   "tgt": "Coffee10",
 *   "props": {
 *     "_id": 1,
 *     "created_at": 1,
 *     "permission": "public",
 *     "memo": "for good morning"
 *   }
 * }
 *
 * jb3NsSyAAAAAAAAAASuiY3G2KX0sgAAAAAAAAAE=, KYEsgAAAAAAAAAEr1Wc4JSyAAAAAAAAAASyAAAAAAAAAASsLXozXNGZvciBnb29kIG1vcm5pbmcALIAAAAAAAAABKyqUN440cHVibGljACyAAAAAAAAAASvEPlKJLIAAAAAAAAB7LIAAAAAAAAABK0noVpM0Q29mZmVlMTAALIAAAAAAAAABK5JB3jEsgAAAAAAAAAEsgAAAAAAAAAE=
 * XskydSyAAAAAAAAAeyuiY3G2KXwpgitptzPH03/////////+LIAAAAAAAAAB, LIAAAAAAAAABK9VnOCUsgAAAAAAAAAErC16M1zRmb3IgZ29vZCBtb3JuaW5nACsqlDeONHB1YmxpYwArxD5SiSyAAAAAAAAAeyssyE5cLIAAAAAAAAABK0noVpM0Q29mZmVlMTAA
 * 4IU4UDRDb2ZmZWUxMAAromNxtil8KYMrabczx9N//////////iyAAAAAAAAAAQ==, LIAAAAAAAAABK9VnOCUsgAAAAAAAAAErC16M1zRmb3IgZ29vZCBtb3JuaW5nACsqlDeONHB1YmxpYwArxD5SiSyAAAAAAAAAeyssyE5cLIAAAAAAAAABK0noVpM0Q29mZmVlMTAA
 * , dc/qIyyAAAAAAAAAeyuiY3G2KX4pgg==
 * , 7s9/xDRDb2ZmZWUxMAAromNxtil+KYM=
 */
class V2MultiEdgeBulkLoadTest {
    private val xxHash32Wrapper = XXHash32Wrapper.default
    private val stateDecoder: EdgeStateRecordMapper.Decoder = EdgeStateRecordMapper.create().decoder
    private val indexDecoder: EdgeIndexRecordMapper.Decoder = EdgeIndexRecordMapper.create().decoder
    private val countDecoder: EdgeCountRecordMapper.Decoder = EdgeCountRecordMapper.create().decoder
    private val cacheDecoder: EdgeCacheRecordMapper.Decoder = EdgeCacheRecordMapper.create().decoder
    private val groupDecoder: EdgeGroupRecordMapper.Decoder = EdgeGroupRecordMapper.create().decoder

    @Test
    fun testEdgeState() {
        val key0 = "jb3NsSyAAAAAAAAAASuiY3G2KX0sgAAAAAAAAAE="
        val value0 = "KYEsgAAAAAAAAAEr1Wc4JSyAAAAAAAAAASyAAAAAAAAAASsLXozXNGZvciBnb29kIG1vcm5pbmcALIAAAAAAAAABKyqUN440cHVibGljACyAAAAAAAAAASvEPlKJLIAAAAAAAAB7LIAAAAAAAAABK0noVpM0Q29mZmVlMTAALIAAAAAAAAABK5JB3jEsgAAAAAAAAAEsgAAAAAAAAAE="
        val key = Base64.getDecoder().decode(key0)
        val value = Base64.getDecoder().decode(value0)

        val edgeStateRecord = stateDecoder.decode(key, value)

        val expected =
            EdgeStateRecord(
                key =
                    EdgeStateRecord.Key.of(
                        source = 1L, // id
                        tableCode = xxHash32Wrapper.stringHash("gift.like_product_v1_20240402_132500"),
                        target = 1L, // id
                    ),
                value =
                    EdgeStateRecord.Value(
                        active = true,
                        version = 1L,
                        createdAt = 1L,
                        deletedAt = null,
                        properties =
                            mapOf(
                                xxHash32Wrapper.stringHash("_source") to StateValue(1L, 123L),
                                xxHash32Wrapper.stringHash("_target") to StateValue(1L, "Coffee10"),
                                xxHash32Wrapper.stringHash("created_at") to StateValue(1L, 1L),
                                xxHash32Wrapper.stringHash("permission") to StateValue(1L, "public"),
                                xxHash32Wrapper.stringHash("memo") to StateValue(1L, "for good morning"),
                            ),
                    ),
            )

        assertEquals(expected, edgeStateRecord)
    }

    @Test
    fun testEdgeIndexOut() {
        val key0 = "XskydSyAAAAAAAAAeyuiY3G2KXwpgitptzPH03/////////+LIAAAAAAAAAB"
        val value0 = "LIAAAAAAAAABK9VnOCUsgAAAAAAAAAErC16M1zRmb3IgZ29vZCBtb3JuaW5nACsqlDeONHB1YmxpYwArxD5SiSyAAAAAAAAAeytJ6FaTNENvZmZlZTEwAA=="
        val key = Base64.getDecoder().decode(key0)
        val value = Base64.getDecoder().decode(value0)

        val edgeIndexRecord = indexDecoder.decode(key, value)

        val expected =
            EdgeIndexRecord(
                key =
                    EdgeIndexRecord.Key(
                        prefix =
                            EdgeIndexRecord.Key.Prefix.of(
                                tableCode = xxHash32Wrapper.stringHash("gift.like_product_v1_20240402_132500"),
                                directedSource = 123L,
                                direction = Direction.OUT,
                                indexCode = xxHash32Wrapper.stringHash("created_at_desc"),
                                indexValues = listOf(EdgeIndexRecord.Key.IndexValue(value = 1L, order = Order.DESC)),
                            ),
                        suffix =
                            EdgeIndexRecord.Key.Suffix(
                                restIndexValues = emptyList(),
                                directedTarget = 1L, // id
                            ),
                    ),
                value =
                    EdgeIndexRecord.Value(
                        version = 1L,
                        properties =
                            mapOf(
                                xxHash32Wrapper.stringHash("_source") to 123L,
                                xxHash32Wrapper.stringHash("_target") to "Coffee10",
                                xxHash32Wrapper.stringHash("created_at") to 1L,
                                xxHash32Wrapper.stringHash("permission") to "public",
                                xxHash32Wrapper.stringHash("memo") to "for good morning",
                            ),
                    ),
            )

        assertEquals(expected, edgeIndexRecord)
    }

    @Test
    fun testEdgeIndexIn() {
        val key0 = "4IU4UDRDb2ZmZWUxMAAromNxtil8KYMrabczx9N//////////iyAAAAAAAAAAQ=="
        val value0 = "LIAAAAAAAAABK9VnOCUsgAAAAAAAAAErC16M1zRmb3IgZ29vZCBtb3JuaW5nACsqlDeONHB1YmxpYwArxD5SiSyAAAAAAAAAeytJ6FaTNENvZmZlZTEwAA=="
        val key = Base64.getDecoder().decode(key0)
        val value = Base64.getDecoder().decode(value0)

        val edgeIndexRecord = indexDecoder.decode(key, value)

        val expected =
            EdgeIndexRecord(
                key =
                    EdgeIndexRecord.Key(
                        prefix =
                            EdgeIndexRecord.Key.Prefix.of(
                                tableCode = xxHash32Wrapper.stringHash("gift.like_product_v1_20240402_132500"),
                                directedSource = "Coffee10",
                                direction = Direction.IN,
                                indexCode = xxHash32Wrapper.stringHash("created_at_desc"),
                                indexValues = listOf(EdgeIndexRecord.Key.IndexValue(value = 1L, order = Order.DESC)),
                            ),
                        suffix =
                            EdgeIndexRecord.Key.Suffix(
                                restIndexValues = emptyList(),
                                directedTarget = 1L, // id
                            ),
                    ),
                value =
                    EdgeIndexRecord.Value(
                        version = 1L,
                        properties =
                            mapOf(
                                xxHash32Wrapper.stringHash("_source") to 123L,
                                xxHash32Wrapper.stringHash("_target") to "Coffee10",
                                xxHash32Wrapper.stringHash("created_at") to 1L,
                                xxHash32Wrapper.stringHash("permission") to "public",
                                xxHash32Wrapper.stringHash("memo") to "for good morning",
                            ),
                    ),
            )

        assertEquals(expected, edgeIndexRecord)
    }

    @Test
    fun testEdgeCountOut() {
        val key0 = "dc/qIyyAAAAAAAAAeyuiY3G2KX4pgg=="
        val key = Base64.getDecoder().decode(key0)

        val edgeCountRecord = countDecoder.decode(key, 1L)

        val expected =
            EdgeCountRecord(
                key =
                    EdgeCountRecord.Key.of(
                        directedSource = 123L,
                        tableCode = xxHash32Wrapper.stringHash("gift.like_product_v1_20240402_132500"),
                        direction = Direction.OUT,
                    ),
                value = 1L,
            )

        assertEquals(expected, edgeCountRecord)
    }

    @Test
    fun testEdgeCountIn() {
        val key0 = "7s9/xDRDb2ZmZWUxMAAromNxtil+KYM="
        val key = Base64.getDecoder().decode(key0)

        val edgeCountRecord = countDecoder.decode(key, 1L)

        val expected =
            EdgeCountRecord(
                key =
                    EdgeCountRecord.Key.of(
                        directedSource = "Coffee10",
                        tableCode = xxHash32Wrapper.stringHash("gift.like_product_v1_20240402_132500"),
                        direction = Direction.IN,
                    ),
                value = 1L,
            )

        assertEquals(expected, edgeCountRecord)
    }

    /**
     * Round-trip verification of bulk-encoded MULTI_EDGE cache row (OUT direction) via V3 decoder.
     *
     * Bytes captured from `MultiEdgeBulkEdgeEncoderTests.testMultiEdgeWithCaches` with
     * `caches: [{"cache":"top_created_at","fields":[{"name":"created_at","order":"DESC"}],"limit":100}]`.
     *
     * For MULTI_EDGE OUT: directedSource = properties._source (original src = 123L),
     * directedTarget = record.key.source (edge id = 1L).
     */
    @Test
    fun testEdgeCacheOut() {
        val key0 = "GrcIXiyAAAAAAAAAeyuiY3G2KXopgivvyJjC"
        val qualifier0 = "03/////////+LIAAAAAAAAAB"
        val value0 =
            "LIAAAAAAAAABK9VnOCUsgAAAAAAAAAErC16M1zRmb3IgZ29vZCBtb3JuaW5nACsqlDeONHB1YmxpYwArxD5SiSyAAAAAAAAAeytJ6FaTNENvZmZlZTEwAA=="
        val key = Base64.getDecoder().decode(key0)
        val qualifier = Base64.getDecoder().decode(qualifier0)
        val value = Base64.getDecoder().decode(value0)

        val edgeCacheRecord = cacheDecoder.decode(key, qualifier, value)

        val expected =
            EdgeCacheRecord(
                key =
                    EdgeCacheRecord.Key.of(
                        directedSource = 123L,
                        tableCode = xxHash32Wrapper.stringHash("gift.like_product_v1_20240402_132500"),
                        direction = Direction.OUT,
                        cacheCode = xxHash32Wrapper.stringHash("top_created_at"),
                    ),
                qualifier =
                    EdgeCacheRecord.Qualifier(
                        cacheValues = listOf(EdgeCacheRecord.Qualifier.CacheValue(value = 1L, order = Order.DESC)),
                        directedTarget = 1L, // edge id
                    ),
                value =
                    EdgeCacheRecord.Value(
                        version = 1L,
                        properties =
                            mapOf(
                                xxHash32Wrapper.stringHash("_source") to 123L,
                                xxHash32Wrapper.stringHash("_target") to "Coffee10",
                                xxHash32Wrapper.stringHash("created_at") to 1L,
                                xxHash32Wrapper.stringHash("permission") to "public",
                                xxHash32Wrapper.stringHash("memo") to "for good morning",
                            ),
                    ),
            )

        assertEquals(expected, edgeCacheRecord)
    }

    /**
     * For MULTI_EDGE IN: directedSource = properties._target (original tgt = "Coffee10"),
     * directedTarget = record.key.source (edge id = 1L).
     */
    @Test
    fun testEdgeCacheIn() {
        val key0 = "FK89BDRDb2ZmZWUxMAAromNxtil6KYMr78iYwg=="
        val qualifier0 = "03/////////+LIAAAAAAAAAB"
        val value0 =
            "LIAAAAAAAAABK9VnOCUsgAAAAAAAAAErC16M1zRmb3IgZ29vZCBtb3JuaW5nACsqlDeONHB1YmxpYwArxD5SiSyAAAAAAAAAeytJ6FaTNENvZmZlZTEwAA=="
        val key = Base64.getDecoder().decode(key0)
        val qualifier = Base64.getDecoder().decode(qualifier0)
        val value = Base64.getDecoder().decode(value0)

        val edgeCacheRecord = cacheDecoder.decode(key, qualifier, value)

        val expected =
            EdgeCacheRecord(
                key =
                    EdgeCacheRecord.Key.of(
                        directedSource = "Coffee10",
                        tableCode = xxHash32Wrapper.stringHash("gift.like_product_v1_20240402_132500"),
                        direction = Direction.IN,
                        cacheCode = xxHash32Wrapper.stringHash("top_created_at"),
                    ),
                qualifier =
                    EdgeCacheRecord.Qualifier(
                        cacheValues = listOf(EdgeCacheRecord.Qualifier.CacheValue(value = 1L, order = Order.DESC)),
                        directedTarget = 1L, // edge id
                    ),
                value =
                    EdgeCacheRecord.Value(
                        version = 1L,
                        properties =
                            mapOf(
                                xxHash32Wrapper.stringHash("_source") to 123L,
                                xxHash32Wrapper.stringHash("_target") to "Coffee10",
                                xxHash32Wrapper.stringHash("created_at") to 1L,
                                xxHash32Wrapper.stringHash("permission") to "public",
                                xxHash32Wrapper.stringHash("memo") to "for good morning",
                            ),
                    ),
            )

        assertEquals(expected, edgeCacheRecord)
    }

    /**
     * Round-trip verification of bulk-encoded MULTI_EDGE group row (OUT direction) via V3 decoder.
     *
     * Bytes captured from a MULTI_EDGE label with
     * `groups: [{"group":"top_created_at","type":"COUNT","fields":[{"name":"created_at"}]}]`,
     * same edge fixture as [testEdgeCacheOut]/[testEdgeCacheIn].
     *
     * For MULTI_EDGE OUT: directedSource = properties._source (original src = 123L). Unlike
     * EdgeCache, EdgeGroup has no `directedTarget` in its qualifier — only bucketed group field
     * values — and the cell value is this single edge's raw contribution (COUNT=1), matching V3's
     * plain `buffer.long` decode (no OrderedBytes header).
     */
    @Test
    fun testEdgeGroupOut() {
        val key0 = "9a9SBiyAAAAAAAAAeyuiY3G2KXspgivvyJjC"
        val qualifier0 = "03/////////+"
        val value0 = "AAAAAAAAAAE="
        val key = Base64.getDecoder().decode(key0)
        val qualifier = Base64.getDecoder().decode(qualifier0)
        val value = Base64.getDecoder().decode(value0)

        val edgeGroupRecord = groupDecoder.decode(key, qualifier, value)

        val expected =
            EdgeGroupRecord(
                key =
                    EdgeGroupRecord.Key.of(
                        directedSource = 123L,
                        tableCode = xxHash32Wrapper.stringHash("gift.like_product_v1_20240402_132500"),
                        direction = Direction.OUT,
                        groupCode = xxHash32Wrapper.stringHash("top_created_at"),
                    ),
                qualifier = EdgeGroupRecord.Qualifier(groupValues = listOf(1L)),
                value = 1L,
            )

        assertEquals(expected, edgeGroupRecord)
    }

    /**
     * For MULTI_EDGE IN: directedSource = properties._target (original tgt = "Coffee10").
     */
    @Test
    fun testEdgeGroupIn() {
        val key0 = "xaGKMjRDb2ZmZWUxMAAromNxtil7KYMr78iYwg=="
        val qualifier0 = "03/////////+"
        val value0 = "AAAAAAAAAAE="
        val key = Base64.getDecoder().decode(key0)
        val qualifier = Base64.getDecoder().decode(qualifier0)
        val value = Base64.getDecoder().decode(value0)

        val edgeGroupRecord = groupDecoder.decode(key, qualifier, value)

        val expected =
            EdgeGroupRecord(
                key =
                    EdgeGroupRecord.Key.of(
                        directedSource = "Coffee10",
                        tableCode = xxHash32Wrapper.stringHash("gift.like_product_v1_20240402_132500"),
                        direction = Direction.IN,
                        groupCode = xxHash32Wrapper.stringHash("top_created_at"),
                    ),
                qualifier = EdgeGroupRecord.Qualifier(groupValues = listOf(1L)),
                value = 1L,
            )

        assertEquals(expected, edgeGroupRecord)
    }

    /**
     * Round-trip verification of bulk-encoded INDEXED group row (OUT direction) via V3 decoder.
     *
     * Bytes captured from an INDEXED label (same edge fixture) with
     * `groups: [{"group":"top_created_at","type":"COUNT","fields":[{"name":"created_at"}]}]`
     * via `BulkEdgeEncoder`'s `encodeAllGroupEdges` call — a different Java code path than
     * MULTI_EDGE's manual per-direction loop in [testEdgeGroupOut]/[testEdgeGroupIn]. The bytes
     * happen to be byte-identical here (both resolve to src=123L/tgt="Coffee10" with no
     * dimensioning), since both paths ultimately delegate to the same `encodeGroupEdge`
     * implementation — this test guards the INDEXED path specifically against a future
     * divergence in either one.
     */
    @Test
    fun testEdgeGroupOutFromIndexedLabel() {
        val key0 = "9a9SBiyAAAAAAAAAeyuiY3G2KXspgivvyJjC"
        val qualifier0 = "03/////////+"
        val value0 = "AAAAAAAAAAE="
        val key = Base64.getDecoder().decode(key0)
        val qualifier = Base64.getDecoder().decode(qualifier0)
        val value = Base64.getDecoder().decode(value0)

        val edgeGroupRecord = groupDecoder.decode(key, qualifier, value)

        val expected =
            EdgeGroupRecord(
                key =
                    EdgeGroupRecord.Key.of(
                        directedSource = 123L,
                        tableCode = xxHash32Wrapper.stringHash("gift.like_product_v1_20240402_132500"),
                        direction = Direction.OUT,
                        groupCode = xxHash32Wrapper.stringHash("top_created_at"),
                    ),
                qualifier = EdgeGroupRecord.Qualifier(groupValues = listOf(1L)),
                value = 1L,
            )

        assertEquals(expected, edgeGroupRecord)
    }

    /**
     * For INDEXED IN: directedSource = record.key.target (original tgt = "Coffee10").
     */
    @Test
    fun testEdgeGroupInFromIndexedLabel() {
        val key0 = "xaGKMjRDb2ZmZWUxMAAromNxtil7KYMr78iYwg=="
        val qualifier0 = "03/////////+"
        val value0 = "AAAAAAAAAAE="
        val key = Base64.getDecoder().decode(key0)
        val qualifier = Base64.getDecoder().decode(qualifier0)
        val value = Base64.getDecoder().decode(value0)

        val edgeGroupRecord = groupDecoder.decode(key, qualifier, value)

        val expected =
            EdgeGroupRecord(
                key =
                    EdgeGroupRecord.Key.of(
                        directedSource = "Coffee10",
                        tableCode = xxHash32Wrapper.stringHash("gift.like_product_v1_20240402_132500"),
                        direction = Direction.IN,
                        groupCode = xxHash32Wrapper.stringHash("top_created_at"),
                    ),
                qualifier = EdgeGroupRecord.Qualifier(groupValues = listOf(1L)),
                value = 1L,
            )

        assertEquals(expected, edgeGroupRecord)
    }
}
