package com.kakao.actionbase.core.edge.mutation

import com.kakao.actionbase.core.edge.mutation.EdgeMutationTestFixtures.edgeRecord
import com.kakao.actionbase.core.edge.mutation.EdgeMutationTestFixtures.multiEdgeRecord
import com.kakao.actionbase.core.edge.record.EdgeCountRecord
import com.kakao.actionbase.core.edge.record.EdgeStateRecord
import com.kakao.actionbase.core.metadata.common.Direction
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest
import com.kakao.actionbase.test.documentations.params.TableSource

import kotlin.test.assertEquals

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class EdgeMutationStrategyTest {
    private fun strategyOf(name: String): EdgeMutationStrategy =
        when (name) {
            "Edge" -> EdgeMutationStrategy.Edge
            "MultiEdge" -> EdgeMutationStrategy.MultiEdge
            else -> error("unknown strategy: $name")
        }

    private fun recordOf(
        strategy: String,
        source: Any = "userA",
        target: Any = "postX",
        active: Boolean = true,
        version: Long = 1L,
    ): EdgeStateRecord =
        when (strategy) {
            "Edge" -> edgeRecord(source = source, target = target, active = active, version = version)
            "MultiEdge" -> multiEdgeRecord(id = "edgeId1", source = source, target = target, active = active, version = version)
            else -> error("unknown strategy: $strategy")
        }

    @Nested
    inner class DirectedSource {
        @ObjectSourceParameterizedTest
        @TableSource(
            """
            # strategy   | direction | expected
            - Edge       | OUT       | userA   # Edge OUT  → key.source
            - Edge       | IN        | postX   # Edge IN   → key.target
            - MultiEdge  | OUT       | userA   # MultiEdge → properties._source
            - MultiEdge  | IN        | postX   # MultiEdge → properties._target
            """,
        )
        fun `returns the configured source for the direction`(
            strategy: String,
            direction: Direction,
            expected: String,
        ) {
            val record = recordOf(strategy)
            assertEquals(expected, strategyOf(strategy).directedSource(record, direction))
        }
    }

    @Nested
    inner class DirectedTarget {
        @ObjectSourceParameterizedTest
        @TableSource(
            """
            # strategy   | direction | expected
            - Edge       | OUT       | postX    # Edge swaps source/target
            - Edge       | IN        | userA
            - MultiEdge  | OUT       | edgeId1  # MultiEdge always uses key.source (id)
            - MultiEdge  | IN        | edgeId1
            """,
        )
        fun `returns the configured target for the direction`(
            strategy: String,
            direction: Direction,
            expected: String,
        ) {
            val record = recordOf(strategy)
            assertEquals(expected, strategyOf(strategy).directedTarget(record, direction))
        }
    }

    @Nested
    inner class CountRecordOnDelete {
        @ObjectSourceParameterizedTest
        @TableSource(
            """
            # Edge counts the after record (because source/target are stable across the
            # delete); MultiEdge counts the before record (after's properties are nulled).
            - Edge      | after
            - MultiEdge | before
            """,
        )
        fun `picks the count record by strategy`(
            strategy: String,
            which: String,
        ) {
            val before = recordOf(strategy, active = true, version = 1)
            val after = recordOf(strategy, active = false, version = 2)
            val expected = if (which == "before") before else after
            assertEquals(expected, strategyOf(strategy).countRecordOnDelete(before, after))
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

        @ObjectSourceParameterizedTest
        @TableSource(
            """
            # Edge never produces records on update; MultiEdge produces 4 (2 directions
            # × decrement+increment) iff source or target changed.
            # strategy    | beforeSrc | afterSrc | beforeTgt | afterTgt | expectedSize
            - Edge        | userA     | userB    | postX     | postY    | 0   # Edge ignores changes
            - MultiEdge   | userA     | userA    | postX     | postX    | 0   # unchanged
            - MultiEdge   | userA     | userB    | postX     | postX    | 4   # source changed
            - MultiEdge   | userA     | userA    | postX     | postY    | 4   # target changed
            """,
        )
        fun `produces records only when MultiEdge source or target changed`(
            strategy: String,
            beforeSrc: String,
            afterSrc: String,
            beforeTgt: String,
            afterTgt: String,
            expectedSize: Int,
        ) {
            val before = recordOf(strategy, source = beforeSrc, target = beforeTgt, version = 1)
            val after = recordOf(strategy, source = afterSrc, target = afterTgt, version = 2)
            val result =
                strategyOf(strategy).countRecordsOnUpdate(before, after, DirectionType.BOTH, stubBuildCountRecords)
            assertEquals(expectedSize, result.size)
        }

        @Test
        fun `MultiEdge change produces matching decrement and increment records`() {
            val before = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = true, version = 1)
            val after = multiEdgeRecord(id = "edgeId1", source = "userB", target = "postX", active = true, version = 2)
            val result =
                EdgeMutationStrategy.MultiEdge.countRecordsOnUpdate(before, after, DirectionType.BOTH, stubBuildCountRecords)
            val decrementRecords = result.filter { it.value == -1L }
            val incrementRecords = result.filter { it.value == 1L }
            assertEquals(2, decrementRecords.size)
            assertEquals(2, incrementRecords.size)
        }
    }
}
