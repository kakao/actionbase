package com.kakao.actionbase.core.bulkload

import com.kakao.actionbase.core.codec.XXHash32Wrapper
import com.kakao.actionbase.core.edge.mapper.EdgeGroupRecordMapper
import com.kakao.actionbase.core.edge.mutation.EdgeMutationBuilder
import com.kakao.actionbase.core.edge.record.EdgeGroupRecord
import com.kakao.actionbase.core.edge.record.EdgeStateRecord
import com.kakao.actionbase.core.metadata.common.Direction
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.GroupType
import com.kakao.actionbase.core.state.AbstractSchema
import com.kakao.actionbase.core.state.StateValue
import com.kakao.actionbase.v2.core.code.BulkEdgeEncoder
import com.kakao.actionbase.v2.core.code.EdgeEncoder
import com.kakao.actionbase.v2.core.code.EdgeEncoderFactory
import com.kakao.actionbase.v2.core.edge.BulkLoadEdge
import com.kakao.actionbase.v2.core.metadata.EncodedEdgeType
import com.kakao.actionbase.v2.core.metadata.LabelDTO

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

import com.fasterxml.jackson.databind.ObjectMapper

/**
 * End-to-end verification of the bulk-load EdgeGroup path *without* the external pipeline: it
 * simulates the pipeline's aggregation step in-process and asserts it converges on the same
 * per-cell values the online write path already produces.
 *
 * The bulk-load pipeline never writes an EdgeGroup cell's absolute value directly. Each edge
 * encodes its own single contribution (`1` for COUNT, the value field for SUM); the pipeline then
 * groups those cells by (row key, qualifier) and sums the contributions into the final cell — the
 * batch analogue of the online path's per-edge HBase `Increment`. This test drives both paths over
 * the same set of edges and checks the two aggregations agree cell-for-cell:
 *
 *  - bulk path: [BulkEdgeEncoder] (V2) encodes each edge, the EdgeGroup rows are decoded through
 *    V3's [EdgeGroupRecordMapper], and their contributions are summed by (key, qualifier);
 *  - online path: [EdgeMutationBuilder] builds the group records for the same edges (each a `+1`
 *    weighted contribution on create), summed the same way.
 *
 * The single-edge byte-for-byte round-trip between the two codecs is covered by
 * [V2MultiEdgeBulkLoadTest]; this test adds the multi-edge group-by-and-sum semantics on top.
 */
class V2BulkLoadAggregationTest {
    private val xxHash32Wrapper = XXHash32Wrapper.default
    private val objectMapper = ObjectMapper()
    private val groupDecoder: EdgeGroupRecordMapper.Decoder = EdgeGroupRecordMapper.create().decoder

    private val tableName = "example.bulk_aggregation_multi_edge_v1"
    private val tableCode = xxHash32Wrapper.stringHash(tableName)

    private val countGroupName = "created_at_count"
    private val sumGroupName = "amount_sum"

    /** COUNT + SUM groups keyed by `created_at`; both default to `directionType=BOTH` (OUT + IN). */
    private val onlineGroups =
        listOf(
            Group(group = countGroupName, type = GroupType.COUNT, fields = listOf(Group.Field("created_at"))),
            Group(group = sumGroupName, type = GroupType.SUM, fields = listOf(Group.Field("created_at")), valueField = "amount"),
        )

    private val labelJson =
        """
        {
          "name": "$tableName",
          "desc": "bulk-load group aggregation fixture",
          "type": "MULTI_EDGE",
          "schema": {
            "src": {"type": "LONG"},
            "tgt": {"type": "STRING"},
            "fields": [
              {"name": "_id", "type": "LONG", "nullable": false},
              {"name": "created_at", "type": "LONG", "nullable": false},
              {"name": "amount", "type": "LONG", "nullable": false}
            ]
          },
          "dirType": "BOTH",
          "storage": "$tableName",
          "indices": [
            {"id": 0, "name": "created_at_desc", "fields": [{"name": "created_at", "order": "DESC"}]}
          ],
          "caches": [],
          "groups": [
            {"group": "$countGroupName", "type": "COUNT", "fields": [{"name": "created_at"}]},
            {"group": "$sumGroupName", "type": "SUM", "valueField": "amount", "fields": [{"name": "created_at"}]}
          ],
          "event": false,
          "readOnly": false
        }
        """.trimIndent()

    /**
     * Edges chosen so that several land in the same group bucket, exercising the sum rather than a
     * trivial one-contribution-per-cell case:
     *  - A & B share the OUT bucket (src=123, created_at=10);
     *  - A & D share the IN bucket (tgt="ItemA", created_at=10).
     */
    private val edges =
        listOf(
            BulkEdgeFixture(id = 1L, src = 123L, tgt = "ItemA", createdAt = 10L, amount = 5L),
            BulkEdgeFixture(id = 2L, src = 123L, tgt = "ItemB", createdAt = 10L, amount = 7L),
            BulkEdgeFixture(id = 3L, src = 123L, tgt = "ItemA", createdAt = 20L, amount = 3L),
            BulkEdgeFixture(id = 4L, src = 456L, tgt = "ItemA", createdAt = 10L, amount = 2L),
        )

    @Test
    fun `bulk-load group aggregation matches the online mutation path`() {
        val bulkAggregation = aggregate(encodeGroupRecordsViaBulkPath())
        val onlineAggregation = aggregate(buildGroupRecordsViaOnlinePath())

        assertEquals(onlineAggregation, bulkAggregation)

        // Guard against a degenerate all-ones result: at least one bucket must actually sum > 1
        // contribution, otherwise the group-by-and-sum step isn't being exercised.
        assertTrue(
            onlineAggregation.values.any { it > 1L },
            "expected at least one aggregated bucket to sum multiple contributions",
        )

        // A & B (src=123, created_at=10) collapse into one OUT COUNT cell = 2, and their amounts
        // sum to 12 in the SUM cell — spelled out so a regression in either path is legible here.
        assertEquals(2L, onlineAggregation[bucket(directedSource = 123L, direction = Direction.OUT, groupName = countGroupName, createdAt = 10L)])
        assertEquals(12L, onlineAggregation[bucket(directedSource = 123L, direction = Direction.OUT, groupName = sumGroupName, createdAt = 10L)])
    }

    private fun encodeGroupRecordsViaBulkPath(): List<EdgeGroupRecord> {
        val label = objectMapper.readValue(labelJson, LabelDTO::class.java)
        val factory = EdgeEncoderFactory(1)
        val encoder: EdgeEncoder<ByteArray> = factory.bytesKeyValueEncoder

        return edges.flatMap { edge ->
            val encoded = BulkEdgeEncoder.bulkEncodeAll(encoder, objectMapper.readValue(edge.toJson(), BulkLoadEdge::class.java), label)
            encoded
                .filter { it.encodedEdgeType == EncodedEdgeType.EDGE_GROUP_TYPE }
                .map { groupDecoder.decode(it.key, it.field, it.value) }
        }
    }

    private fun buildGroupRecordsViaOnlinePath(): List<EdgeGroupRecord> =
        edges.flatMap { edge ->
            val before = edge.toStateRecord(active = false, version = 0L)
            val after = edge.toStateRecord(active = true, version = 1L)
            EdgeMutationBuilder
                .buildForMultiEdge(before, after, DirectionType.BOTH, emptyList(), onlineGroups, emptyList())
                .groupRecords
        }

    private fun aggregate(records: List<EdgeGroupRecord>): Map<Pair<EdgeGroupRecord.Key, EdgeGroupRecord.Qualifier>, Long> =
        records
            .groupingBy { it.key to it.qualifier }
            .fold(0L) { acc, record -> acc + record.value }

    private fun bucket(
        directedSource: Any,
        direction: Direction,
        groupName: String,
        createdAt: Long,
    ): Pair<EdgeGroupRecord.Key, EdgeGroupRecord.Qualifier> =
        EdgeGroupRecord.Key.of(
            directedSource = directedSource,
            tableCode = tableCode,
            direction = direction,
            groupCode = xxHash32Wrapper.stringHash(groupName),
        ) to EdgeGroupRecord.Qualifier(groupValues = listOf(createdAt))

    private inner class BulkEdgeFixture(
        val id: Long,
        val src: Long,
        val tgt: String,
        val createdAt: Long,
        val amount: Long,
    ) {
        fun toJson(): String =
            """
            {"active": true, "ts": 1, "src": $src, "tgt": "$tgt",
             "props": {"_id": $id, "created_at": $createdAt, "amount": $amount}}
            """.trimIndent()

        fun toStateRecord(
            active: Boolean,
            version: Long,
        ): EdgeStateRecord =
            EdgeStateRecord(
                key = EdgeStateRecord.Key.of(source = id, tableCode = tableCode, target = id),
                value =
                    EdgeStateRecord.Value(
                        active = active,
                        version = version,
                        createdAt = if (active) version else null,
                        deletedAt = null,
                        properties =
                            mapOf(
                                EdgeMutationBuilder.MULTI_EDGE_SOURCE_CODE to StateValue(version, src),
                                EdgeMutationBuilder.MULTI_EDGE_TARGET_CODE to StateValue(version, tgt),
                                AbstractSchema.codeOf("created_at") to StateValue(version, createdAt),
                                AbstractSchema.codeOf("amount") to StateValue(version, amount),
                            ),
                    ),
            )
    }
}
