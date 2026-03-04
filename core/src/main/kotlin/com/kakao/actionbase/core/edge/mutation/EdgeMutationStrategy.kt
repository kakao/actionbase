package com.kakao.actionbase.core.edge.mutation

import com.kakao.actionbase.core.edge.record.EdgeCountRecord
import com.kakao.actionbase.core.edge.record.EdgeStateRecord
import com.kakao.actionbase.core.metadata.common.Direction
import com.kakao.actionbase.core.metadata.common.DirectionType

/**
 * Strategy that encapsulates the behavioral differences between Edge and MultiEdge mutation building.
 *
 * The four axes of difference:
 * 1. Source resolution: Edge uses key.source/target, MultiEdge uses properties._source/_target
 * 2. Target resolution (index suffix): Edge swaps source/target, MultiEdge always uses key.source (id)
 * 3. DELETE count source: Edge uses after record, MultiEdge uses before record
 * 4. UPDATE count: Edge produces none, MultiEdge produces conditionally (when source/target changed)
 */
sealed interface EdgeMutationStrategy {
    fun directedSource(
        record: EdgeStateRecord,
        direction: Direction,
    ): Any

    fun directedTarget(
        record: EdgeStateRecord,
        direction: Direction,
    ): Any

    fun countRecordOnDelete(
        before: EdgeStateRecord,
        after: EdgeStateRecord,
    ): EdgeStateRecord

    fun countRecordsOnUpdate(
        before: EdgeStateRecord,
        after: EdgeStateRecord,
        directionType: DirectionType,
        buildCountRecords: (EdgeStateRecord, DirectionType, Long) -> List<EdgeCountRecord>,
    ): List<EdgeCountRecord>

    data object Edge : EdgeMutationStrategy {
        override fun directedSource(
            record: EdgeStateRecord,
            direction: Direction,
        ): Any =
            when (direction) {
                Direction.OUT -> record.key.source
                Direction.IN -> record.key.target
            }

        override fun directedTarget(
            record: EdgeStateRecord,
            direction: Direction,
        ): Any =
            when (direction) {
                Direction.OUT -> record.key.target
                Direction.IN -> record.key.source
            }

        override fun countRecordOnDelete(
            before: EdgeStateRecord,
            after: EdgeStateRecord,
        ): EdgeStateRecord = after

        override fun countRecordsOnUpdate(
            before: EdgeStateRecord,
            after: EdgeStateRecord,
            directionType: DirectionType,
            buildCountRecords: (EdgeStateRecord, DirectionType, Long) -> List<EdgeCountRecord>,
        ): List<EdgeCountRecord> = emptyList()
    }

    data object MultiEdge : EdgeMutationStrategy {
        override fun directedSource(
            record: EdgeStateRecord,
            direction: Direction,
        ): Any =
            when (direction) {
                Direction.OUT -> requireNotNull(record.value.properties[EdgeMutationBuilder.MULTI_EDGE_SOURCE_CODE]?.value) { "Missing _source property in MultiEdge record" }
                Direction.IN -> requireNotNull(record.value.properties[EdgeMutationBuilder.MULTI_EDGE_TARGET_CODE]?.value) { "Missing _target property in MultiEdge record" }
            }

        override fun directedTarget(
            record: EdgeStateRecord,
            direction: Direction,
        ): Any = record.key.source

        override fun countRecordOnDelete(
            before: EdgeStateRecord,
            after: EdgeStateRecord,
        ): EdgeStateRecord = before

        override fun countRecordsOnUpdate(
            before: EdgeStateRecord,
            after: EdgeStateRecord,
            directionType: DirectionType,
            buildCountRecords: (EdgeStateRecord, DirectionType, Long) -> List<EdgeCountRecord>,
        ): List<EdgeCountRecord> {
            val sourceChanged =
                directedSource(before, Direction.OUT) != directedSource(after, Direction.OUT)
            val targetChanged =
                directedSource(before, Direction.IN) != directedSource(after, Direction.IN)
            return if (sourceChanged || targetChanged) {
                buildCountRecords(before, directionType, -1L) +
                    buildCountRecords(after, directionType, 1L)
            } else {
                emptyList()
            }
        }
    }
}
