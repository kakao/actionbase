package com.kakao.actionbase.core.edge.mutation

import com.kakao.actionbase.core.edge.mutation.EdgeMutationTestFixtures.edgeRecord
import com.kakao.actionbase.core.edge.mutation.EdgeMutationTestFixtures.multiEdgeRecord
import com.kakao.actionbase.core.edge.record.EdgeCountRecord
import com.kakao.actionbase.core.edge.record.EdgeStateRecord
import com.kakao.actionbase.core.metadata.common.Direction
import com.kakao.actionbase.core.metadata.common.DirectionType

import kotlin.test.assertEquals
import kotlin.test.assertTrue

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class EdgeMutationStrategyTest {
    @Nested
    inner class DirectedSource {
        @Test
        fun `Edge OUT returns key source`() {
            val record = edgeRecord(source = "userA", target = "postX", active = true)
            assertEquals("userA", EdgeMutationStrategy.Edge.directedSource(record, Direction.OUT))
        }

        @Test
        fun `Edge IN returns key target`() {
            val record = edgeRecord(source = "userA", target = "postX", active = true)
            assertEquals("postX", EdgeMutationStrategy.Edge.directedSource(record, Direction.IN))
        }

        @Test
        fun `MultiEdge OUT returns properties _source`() {
            val record = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = true)
            assertEquals("userA", EdgeMutationStrategy.MultiEdge.directedSource(record, Direction.OUT))
        }

        @Test
        fun `MultiEdge IN returns properties _target`() {
            val record = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = true)
            assertEquals("postX", EdgeMutationStrategy.MultiEdge.directedSource(record, Direction.IN))
        }
    }

    @Nested
    inner class DirectedTarget {
        @Test
        fun `Edge OUT returns key target`() {
            val record = edgeRecord(source = "userA", target = "postX", active = true)
            assertEquals("postX", EdgeMutationStrategy.Edge.directedTarget(record, Direction.OUT))
        }

        @Test
        fun `Edge IN returns key source`() {
            val record = edgeRecord(source = "userA", target = "postX", active = true)
            assertEquals("userA", EdgeMutationStrategy.Edge.directedTarget(record, Direction.IN))
        }

        @Test
        fun `MultiEdge OUT returns key source (id)`() {
            val record = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = true)
            assertEquals("edgeId1", EdgeMutationStrategy.MultiEdge.directedTarget(record, Direction.OUT))
        }

        @Test
        fun `MultiEdge IN returns key source (id)`() {
            val record = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = true)
            assertEquals("edgeId1", EdgeMutationStrategy.MultiEdge.directedTarget(record, Direction.IN))
        }
    }

    @Nested
    inner class CountRecordOnDelete {
        @Test
        fun `Edge returns after record`() {
            val before = edgeRecord(source = "userA", target = "postX", active = true, version = 1)
            val after = edgeRecord(source = "userA", target = "postX", active = false, version = 2)
            assertEquals(after, EdgeMutationStrategy.Edge.countRecordOnDelete(before, after))
        }

        @Test
        fun `MultiEdge returns before record`() {
            val before = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = true, version = 1)
            val after = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = false, version = 2)
            assertEquals(before, EdgeMutationStrategy.MultiEdge.countRecordOnDelete(before, after))
        }
    }

    @Nested
    inner class CountRecordsOnUpdate {
        private val stubBuildCountRecords: (EdgeStateRecord, DirectionType, Long) -> List<EdgeCountRecord> =
            { record, directionType, acc ->
                directionType.directions().map { direction ->
                    EdgeCountRecord(
                        key =
                            EdgeCountRecord.Key.of(
                                directedSource = record.key.source,
                                tableCode = record.key.tableCode,
                                direction = direction,
                            ),
                        value = acc,
                    )
                }
            }

        @Test
        fun `Edge always returns empty list`() {
            val before = edgeRecord(source = "userA", target = "postX", active = true, version = 1)
            val after = edgeRecord(source = "userB", target = "postY", active = true, version = 2)
            val result = EdgeMutationStrategy.Edge.countRecordsOnUpdate(before, after, DirectionType.BOTH, stubBuildCountRecords)
            assertTrue(result.isEmpty())
        }

        @Test
        fun `MultiEdge returns records when source changes`() {
            val before = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = true, version = 1)
            val after = multiEdgeRecord(id = "edgeId1", source = "userB", target = "postX", active = true, version = 2)
            val result = EdgeMutationStrategy.MultiEdge.countRecordsOnUpdate(before, after, DirectionType.BOTH, stubBuildCountRecords)
            assertEquals(4, result.size)
        }

        @Test
        fun `MultiEdge returns records when target changes`() {
            val before = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = true, version = 1)
            val after = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postY", active = true, version = 2)
            val result = EdgeMutationStrategy.MultiEdge.countRecordsOnUpdate(before, after, DirectionType.BOTH, stubBuildCountRecords)
            assertEquals(4, result.size)
        }

        @Test
        fun `MultiEdge returns empty when source and target unchanged`() {
            val before = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = true, version = 1)
            val after = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = true, version = 2)
            val result = EdgeMutationStrategy.MultiEdge.countRecordsOnUpdate(before, after, DirectionType.BOTH, stubBuildCountRecords)
            assertTrue(result.isEmpty())
        }

        @Test
        fun `MultiEdge decrement records use -1 and increment records use 1`() {
            val before = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = true, version = 1)
            val after = multiEdgeRecord(id = "edgeId1", source = "userB", target = "postX", active = true, version = 2)
            val result = EdgeMutationStrategy.MultiEdge.countRecordsOnUpdate(before, after, DirectionType.BOTH, stubBuildCountRecords)
            val decrementRecords = result.filter { it.value == -1L }
            val incrementRecords = result.filter { it.value == 1L }
            assertEquals(2, decrementRecords.size)
            assertEquals(2, incrementRecords.size)
        }
    }
}
