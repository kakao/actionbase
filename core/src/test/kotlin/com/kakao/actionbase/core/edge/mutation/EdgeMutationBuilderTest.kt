package com.kakao.actionbase.core.edge.mutation

import com.kakao.actionbase.core.edge.mutation.EdgeMutationTestFixtures.edgeRecord
import com.kakao.actionbase.core.edge.mutation.EdgeMutationTestFixtures.multiEdgeRecord
import com.kakao.actionbase.core.edge.record.EdgeCacheRecord
import com.kakao.actionbase.core.java.codec.common.hbase.Order
import com.kakao.actionbase.core.metadata.common.Cache
import com.kakao.actionbase.core.metadata.common.Direction
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.GroupType
import com.kakao.actionbase.core.metadata.common.Index
import com.kakao.actionbase.core.metadata.common.IndexField

import kotlin.test.assertEquals
import kotlin.test.assertTrue

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class EdgeMutationBuilderTest {
    private val versionIndex =
        Index(
            index = "version_idx",
            fields = listOf(IndexField(field = "version", order = Order.DESC)),
        )

    @Nested
    inner class EdgeStrategy {
        @Test
        fun `IDLE when before equals after`() {
            val record = edgeRecord(source = "userA", target = "postX", active = true)
            val result = EdgeMutationBuilder.buildForUniqueEdge(record, record, DirectionType.BOTH, emptyList(), emptyList(), emptyList())

            assertEquals("IDLE", result.status)
            assertEquals(0L, result.acc)
            assertTrue(result.createIndexRecords.isEmpty())
            assertTrue(result.deleteIndexRecordKeys.isEmpty())
            assertTrue(result.countRecords.isEmpty())
            assertTrue(result.groupRecords.isEmpty())
        }

        @Test
        fun `CREATED when inactive to active`() {
            val before = edgeRecord(source = "userA", target = "postX", active = false, version = 0)
            val after = edgeRecord(source = "userA", target = "postX", active = true, version = 1)
            val result = EdgeMutationBuilder.buildForUniqueEdge(before, after, DirectionType.BOTH, listOf(versionIndex), emptyList(), emptyList())

            assertEquals("CREATED", result.status)
            assertEquals(1L, result.acc)
            assertEquals(2, result.createIndexRecords.size)
            assertEquals(2, result.countRecords.size)
            assertTrue(result.countRecords.all { it.value == 1L })
        }

        @Test
        fun `DELETED uses after record for count and has acc -1`() {
            val before = edgeRecord(source = "userA", target = "postX", active = true, version = 1)
            val after = edgeRecord(source = "userA", target = "postX", active = false, version = 2)
            val result = EdgeMutationBuilder.buildForUniqueEdge(before, after, DirectionType.BOTH, listOf(versionIndex), emptyList(), emptyList())

            assertEquals("DELETED", result.status)
            assertEquals(-1L, result.acc)
            assertEquals(2, result.deleteIndexRecordKeys.size)
            assertEquals(2, result.countRecords.size)
            assertTrue(result.countRecords.all { it.value == -1L })

            // Edge strategy: count uses "after" record -> directedSource from after.key
            val outCount = result.countRecords.first { it.key.direction == Direction.OUT }
            assertEquals("userA", outCount.key.directedSource)
            val inCount = result.countRecords.first { it.key.direction == Direction.IN }
            assertEquals("postX", inCount.key.directedSource)
        }

        @Test
        fun `UPDATED produces no count records for Edge`() {
            val before = edgeRecord(source = "userA", target = "postX", active = true, version = 1)
            val after = edgeRecord(source = "userA", target = "postX", active = true, version = 2)
            val result = EdgeMutationBuilder.buildForUniqueEdge(before, after, DirectionType.BOTH, listOf(versionIndex), emptyList(), emptyList())

            assertEquals("UPDATED", result.status)
            assertEquals(0L, result.acc)
            assertTrue(result.countRecords.isEmpty())
        }

        @Test
        fun `CREATED index directedSource and directedTarget follow key source-target swap`() {
            val before = edgeRecord(source = "userA", target = "postX", active = false, version = 0)
            val after = edgeRecord(source = "userA", target = "postX", active = true, version = 1)
            val result = EdgeMutationBuilder.buildForUniqueEdge(before, after, DirectionType.BOTH, listOf(versionIndex), emptyList(), emptyList())

            val outIndex = result.createIndexRecords.first { it.key.prefix.direction == Direction.OUT }
            assertEquals("userA", outIndex.key.prefix.directedSource)
            assertEquals("postX", outIndex.key.suffix.directedTarget)

            val inIndex = result.createIndexRecords.first { it.key.prefix.direction == Direction.IN }
            assertEquals("postX", inIndex.key.prefix.directedSource)
            assertEquals("userA", inIndex.key.suffix.directedTarget)
        }

        @Test
        fun `CREATED with COUNT group produces groupRecords with value 1`() {
            val countGroup = Group(group = "count_group", type = GroupType.COUNT, fields = listOf(Group.Field("version")))
            val before = edgeRecord(source = "userA", target = "postX", active = false, version = 0)
            val after = edgeRecord(source = "userA", target = "postX", active = true, version = 1)
            val result = EdgeMutationBuilder.buildForUniqueEdge(before, after, DirectionType.BOTH, emptyList(), listOf(countGroup), emptyList())

            assertEquals("CREATED", result.status)
            assertEquals(2, result.groupRecords.size)
            assertTrue(result.groupRecords.all { it.value == 1L })

            val outGroup = result.groupRecords.first { it.key.direction == Direction.OUT }
            assertEquals("userA", outGroup.key.directedSource)
            val inGroup = result.groupRecords.first { it.key.direction == Direction.IN }
            assertEquals("postX", inGroup.key.directedSource)
        }

        @Test
        fun `DELETED with COUNT group produces groupRecords with value -1`() {
            val countGroup = Group(group = "count_group", type = GroupType.COUNT, fields = listOf(Group.Field("version")))
            val before = edgeRecord(source = "userA", target = "postX", active = true, version = 1)
            val after = edgeRecord(source = "userA", target = "postX", active = false, version = 2)
            val result = EdgeMutationBuilder.buildForUniqueEdge(before, after, DirectionType.BOTH, emptyList(), listOf(countGroup), emptyList())

            assertEquals("DELETED", result.status)
            assertEquals(2, result.groupRecords.size)
            assertTrue(result.groupRecords.all { it.value == -1L })
        }

        @Test
        fun `UPDATED with COUNT group produces decrement and increment groupRecords`() {
            val countGroup = Group(group = "count_group", type = GroupType.COUNT, fields = listOf(Group.Field("version")))
            val before = edgeRecord(source = "userA", target = "postX", active = true, version = 1)
            val after = edgeRecord(source = "userA", target = "postX", active = true, version = 2)
            val result = EdgeMutationBuilder.buildForUniqueEdge(before, after, DirectionType.BOTH, emptyList(), listOf(countGroup), emptyList())

            assertEquals("UPDATED", result.status)
            // 2 directions x 2 (decrement + increment) = 4 group records
            assertEquals(4, result.groupRecords.size)
            val decrementRecords = result.groupRecords.filter { it.value == -1L }
            val incrementRecords = result.groupRecords.filter { it.value == 1L }
            assertEquals(2, decrementRecords.size)
            assertEquals(2, incrementRecords.size)
        }

        @Test
        fun `CREATED with SUM group produces groupRecords with weight times field value`() {
            val sumGroup = Group(group = "sum_group", type = GroupType.SUM, fields = listOf(Group.Field("version")), valueField = "version")
            val before = edgeRecord(source = "userA", target = "postX", active = false, version = 0)
            val after = edgeRecord(source = "userA", target = "postX", active = true, version = 5)
            val result = EdgeMutationBuilder.buildForUniqueEdge(before, after, DirectionType.BOTH, emptyList(), listOf(sumGroup), emptyList())

            assertEquals("CREATED", result.status)
            assertEquals(2, result.groupRecords.size)
            // weight=1 * version=5 = 5
            assertTrue(result.groupRecords.all { it.value == 5L })
        }
    }

    @Nested
    inner class MultiEdgeStrategy {
        @Test
        fun `IDLE when before equals after`() {
            val record = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = true)
            val result = EdgeMutationBuilder.buildForMultiEdge(record, record, DirectionType.BOTH, emptyList(), emptyList(), emptyList())

            assertEquals("IDLE", result.status)
            assertEquals(0L, result.acc)
        }

        @Test
        fun `CREATED when inactive to active`() {
            val before = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = false, version = 0)
            val after = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = true, version = 1)
            val result = EdgeMutationBuilder.buildForMultiEdge(before, after, DirectionType.BOTH, listOf(versionIndex), emptyList(), emptyList())

            assertEquals("CREATED", result.status)
            assertEquals(1L, result.acc)
            assertEquals(2, result.createIndexRecords.size)
            assertEquals(2, result.countRecords.size)
        }

        @Test
        fun `DELETED uses before record for count`() {
            val before = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = true, version = 1)
            val after = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = false, version = 2)
            val result = EdgeMutationBuilder.buildForMultiEdge(before, after, DirectionType.BOTH, listOf(versionIndex), emptyList(), emptyList())

            assertEquals("DELETED", result.status)
            assertEquals(-1L, result.acc)
            assertEquals(2, result.countRecords.size)

            // MultiEdge strategy: count uses "before" record -> directedSource from before.value.properties
            val outCount = result.countRecords.first { it.key.direction == Direction.OUT }
            assertEquals("userA", outCount.key.directedSource)
            val inCount = result.countRecords.first { it.key.direction == Direction.IN }
            assertEquals("postX", inCount.key.directedSource)
        }

        @Test
        fun `UPDATED produces count records when source changes`() {
            val before = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = true, version = 1)
            val after = multiEdgeRecord(id = "edgeId1", source = "userB", target = "postX", active = true, version = 2)
            val result = EdgeMutationBuilder.buildForMultiEdge(before, after, DirectionType.BOTH, listOf(versionIndex), emptyList(), emptyList())

            assertEquals("UPDATED", result.status)
            assertTrue(result.countRecords.isNotEmpty())
            // decrement for old + increment for new = 4 records (2 directions x 2)
            assertEquals(4, result.countRecords.size)
        }

        @Test
        fun `UPDATED produces no count records when source and target unchanged`() {
            val before = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = true, version = 1)
            val after = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = true, version = 2)
            val result = EdgeMutationBuilder.buildForMultiEdge(before, after, DirectionType.BOTH, listOf(versionIndex), emptyList(), emptyList())

            assertEquals("UPDATED", result.status)
            assertTrue(result.countRecords.isEmpty())
        }

        @Test
        fun `UPDATED produces count records when target changes`() {
            val before = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = true, version = 1)
            val after = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postY", active = true, version = 2)
            val result = EdgeMutationBuilder.buildForMultiEdge(before, after, DirectionType.BOTH, listOf(versionIndex), emptyList(), emptyList())

            assertEquals("UPDATED", result.status)
            assertEquals(4, result.countRecords.size)
        }

        @Test
        fun `CREATED index directedSource from properties and directedTarget is always id`() {
            val before = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = false, version = 0)
            val after = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = true, version = 1)
            val result = EdgeMutationBuilder.buildForMultiEdge(before, after, DirectionType.BOTH, listOf(versionIndex), emptyList(), emptyList())

            val outIndex = result.createIndexRecords.first { it.key.prefix.direction == Direction.OUT }
            assertEquals("userA", outIndex.key.prefix.directedSource)
            assertEquals("edgeId1", outIndex.key.suffix.directedTarget)

            val inIndex = result.createIndexRecords.first { it.key.prefix.direction == Direction.IN }
            assertEquals("postX", inIndex.key.prefix.directedSource)
            assertEquals("edgeId1", inIndex.key.suffix.directedTarget)
        }

        @Test
        fun `CREATED with COUNT group uses properties for directedSource`() {
            val countGroup = Group(group = "count_group", type = GroupType.COUNT, fields = listOf(Group.Field("version")))
            val before = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = false, version = 0)
            val after = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = true, version = 1)
            val result = EdgeMutationBuilder.buildForMultiEdge(before, after, DirectionType.BOTH, emptyList(), listOf(countGroup), emptyList())

            assertEquals("CREATED", result.status)
            assertEquals(2, result.groupRecords.size)
            assertTrue(result.groupRecords.all { it.value == 1L })

            // MultiEdge: directedSource comes from properties (_source/_target), not key
            val outGroup = result.groupRecords.first { it.key.direction == Direction.OUT }
            assertEquals("userA", outGroup.key.directedSource)
            val inGroup = result.groupRecords.first { it.key.direction == Direction.IN }
            assertEquals("postX", inGroup.key.directedSource)
        }

        @Test
        fun `DELETED with COUNT group produces groupRecords with value -1`() {
            val countGroup = Group(group = "count_group", type = GroupType.COUNT, fields = listOf(Group.Field("version")))
            val before = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = true, version = 1)
            val after = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = false, version = 2)
            val result = EdgeMutationBuilder.buildForMultiEdge(before, after, DirectionType.BOTH, emptyList(), listOf(countGroup), emptyList())

            assertEquals("DELETED", result.status)
            assertEquals(2, result.groupRecords.size)
            assertTrue(result.groupRecords.all { it.value == -1L })
        }

        @Test
        fun `UPDATED with COUNT group produces decrement and increment groupRecords`() {
            val countGroup = Group(group = "count_group", type = GroupType.COUNT, fields = listOf(Group.Field("version")))
            val before = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = true, version = 1)
            val after = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = true, version = 2)
            val result = EdgeMutationBuilder.buildForMultiEdge(before, after, DirectionType.BOTH, emptyList(), listOf(countGroup), emptyList())

            assertEquals("UPDATED", result.status)
            assertEquals(4, result.groupRecords.size)
            val decrementRecords = result.groupRecords.filter { it.value == -1L }
            val incrementRecords = result.groupRecords.filter { it.value == 1L }
            assertEquals(2, decrementRecords.size)
            assertEquals(2, incrementRecords.size)
        }
    }

    @Nested
    inner class CacheRecords {
        private val cache =
            Cache(
                cache = "created_at_desc",
                fields = listOf(IndexField(field = "version", order = Order.DESC)),
                limit = 1,
            )

        @Test
        fun `CREATED produces cache records with correct key and qualifier`() {
            val before = edgeRecord(source = "userA", target = "postX", active = false, version = 0)
            val after = edgeRecord(source = "userA", target = "postX", active = true, version = 1)
            val result = EdgeMutationBuilder.buildForUniqueEdge(before, after, DirectionType.BOTH, emptyList(), emptyList(), listOf(cache))

            assertEquals("CREATED", result.status)
            assertEquals(2, result.createCacheRecords.size)
            assertTrue(result.deleteCacheRecordQualifiers.isEmpty())

            val outCache = result.createCacheRecords.first { it.key.direction == Direction.OUT }
            assertEquals("userA", outCache.key.directedSource)
            assertEquals(cache.code, outCache.key.cacheCode)
            assertEquals("postX", outCache.qualifier.directedTarget)
            assertEquals(1L, outCache.value.version)

            val inCache = result.createCacheRecords.first { it.key.direction == Direction.IN }
            assertEquals("postX", inCache.key.directedSource)
            assertEquals(cache.code, inCache.key.cacheCode)
            assertEquals("userA", inCache.qualifier.directedTarget)
            assertEquals(1L, inCache.value.version)
        }

        @Test
        fun `CREATED with empty caches produces no cache records`() {
            val before = edgeRecord(source = "userA", target = "postX", active = false, version = 0)
            val after = edgeRecord(source = "userA", target = "postX", active = true, version = 1)
            val result = EdgeMutationBuilder.buildForUniqueEdge(before, after, DirectionType.BOTH, emptyList(), emptyList(), emptyList())

            assertEquals("CREATED", result.status)
            assertTrue(result.createCacheRecords.isEmpty())
            assertTrue(result.deleteCacheRecordQualifiers.isEmpty())
        }

        @Test
        fun `DELETED produces delete qualifiers with correct direction and target`() {
            val before = edgeRecord(source = "userA", target = "postX", active = true, version = 1)
            val after = edgeRecord(source = "userA", target = "postX", active = false, version = 2)
            val result = EdgeMutationBuilder.buildForUniqueEdge(before, after, DirectionType.BOTH, emptyList(), emptyList(), listOf(cache))

            assertEquals("DELETED", result.status)
            assertTrue(result.createCacheRecords.isEmpty())
            assertEquals(2, result.deleteCacheRecordQualifiers.size)

            val outQualifier = result.deleteCacheRecordQualifiers.first { it.first.direction == Direction.OUT }
            assertEquals("userA", outQualifier.first.directedSource)
            assertEquals("postX", outQualifier.second.directedTarget)

            val inQualifier = result.deleteCacheRecordQualifiers.first { it.first.direction == Direction.IN }
            assertEquals("postX", inQualifier.first.directedSource)
            assertEquals("userA", inQualifier.second.directedTarget)
        }

        @Test
        fun `UPDATED produces create records for new version and delete qualifiers for old version`() {
            val before = edgeRecord(source = "userA", target = "postX", active = true, version = 1)
            val after = edgeRecord(source = "userA", target = "postX", active = true, version = 2)
            val result = EdgeMutationBuilder.buildForUniqueEdge(before, after, DirectionType.BOTH, emptyList(), emptyList(), listOf(cache))

            assertEquals("UPDATED", result.status)
            assertEquals(2, result.createCacheRecords.size)
            assertEquals(2, result.deleteCacheRecordQualifiers.size)

            // new cache records have after's version
            assertTrue(result.createCacheRecords.all { it.value.version == 2L })

            // delete qualifiers have before's cache values (version=1)
            assertTrue(
                result.deleteCacheRecordQualifiers.all { (_, qualifier) ->
                    qualifier.cacheValues ==
                        listOf(
                            EdgeCacheRecord.Qualifier.CacheValue(value = 1L, order = Order.DESC),
                        )
                },
            )
        }

        @Test
        fun `IDLE produces no cache records`() {
            val record = edgeRecord(source = "userA", target = "postX", active = true)
            val result = EdgeMutationBuilder.buildForUniqueEdge(record, record, DirectionType.BOTH, emptyList(), emptyList(), listOf(cache))

            assertEquals("IDLE", result.status)
            assertTrue(result.createCacheRecords.isEmpty())
            assertTrue(result.deleteCacheRecordQualifiers.isEmpty())
        }

        @Test
        fun `OUT direction produces single cache record`() {
            val before = edgeRecord(source = "userA", target = "postX", active = false, version = 0)
            val after = edgeRecord(source = "userA", target = "postX", active = true, version = 1)
            val result = EdgeMutationBuilder.buildForUniqueEdge(before, after, DirectionType.OUT, emptyList(), emptyList(), listOf(cache))

            assertEquals(1, result.createCacheRecords.size)
            assertEquals(
                Direction.OUT,
                result.createCacheRecords
                    .first()
                    .key.direction,
            )
        }

        @Test
        fun `cache record has correct cache values from fields`() {
            val before = edgeRecord(source = "userA", target = "postX", active = false, version = 0)
            val after = edgeRecord(source = "userA", target = "postX", active = true, version = 5)
            val result = EdgeMutationBuilder.buildForUniqueEdge(before, after, DirectionType.OUT, emptyList(), emptyList(), listOf(cache))

            val cacheRecord = result.createCacheRecords.first()
            assertEquals(
                listOf(EdgeCacheRecord.Qualifier.CacheValue(value = 5L, order = Order.DESC)),
                cacheRecord.qualifier.cacheValues,
            )
        }
    }

    @Nested
    inner class MultiEdgeCacheRecords {
        private val cache =
            Cache(
                cache = "created_at_desc",
                fields = listOf(IndexField(field = "version", order = Order.DESC)),
                limit = 1,
            )

        @Test
        fun `CREATED with cache index produces cache records with id as target`() {
            val before = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = false, version = 0)
            val after = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = true, version = 1)
            val result = EdgeMutationBuilder.buildForMultiEdge(before, after, DirectionType.BOTH, emptyList(), emptyList(), listOf(cache))

            assertEquals("CREATED", result.status)
            assertEquals(2, result.createCacheRecords.size)

            val outCache = result.createCacheRecords.first { it.key.direction == Direction.OUT }
            assertEquals("userA", outCache.key.directedSource)
            assertEquals("edgeId1", outCache.qualifier.directedTarget)

            val inCache = result.createCacheRecords.first { it.key.direction == Direction.IN }
            assertEquals("postX", inCache.key.directedSource)
            assertEquals("edgeId1", inCache.qualifier.directedTarget)
        }

        @Test
        fun `DELETED with cache index produces delete qualifiers`() {
            val before = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = true, version = 1)
            val after = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = false, version = 2)
            val result = EdgeMutationBuilder.buildForMultiEdge(before, after, DirectionType.BOTH, emptyList(), emptyList(), listOf(cache))

            assertEquals("DELETED", result.status)
            assertTrue(result.createCacheRecords.isEmpty())
            assertEquals(2, result.deleteCacheRecordQualifiers.size)
        }

        @Test
        fun `UPDATED with cache index produces create and delete cache records`() {
            val before = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = true, version = 1)
            val after = multiEdgeRecord(id = "edgeId1", source = "userA", target = "postX", active = true, version = 2)
            val result = EdgeMutationBuilder.buildForMultiEdge(before, after, DirectionType.BOTH, emptyList(), emptyList(), listOf(cache))

            assertEquals("UPDATED", result.status)
            assertEquals(2, result.createCacheRecords.size)
        }
    }

    @Nested
    inner class StrategyBehaviorConsistency {
        @Test
        fun `Edge and MultiEdge produce same status for same state transitions`() {
            // IDLE
            val edgeRecord = edgeRecord(source = "a", target = "b", active = true)
            val multiRecord = multiEdgeRecord(id = "id1", source = "a", target = "b", active = true)
            assertEquals(
                EdgeMutationBuilder.buildForUniqueEdge(edgeRecord, edgeRecord, DirectionType.BOTH, emptyList(), emptyList(), emptyList()).status,
                EdgeMutationBuilder.buildForMultiEdge(multiRecord, multiRecord, DirectionType.BOTH, emptyList(), emptyList(), emptyList()).status,
            )

            // CREATED
            val inactiveBefore = edgeRecord(source = "a", target = "b", active = false, version = 0)
            val activeAfter = edgeRecord(source = "a", target = "b", active = true, version = 1)
            val multiInactiveBefore = multiEdgeRecord(id = "id1", source = "a", target = "b", active = false, version = 0)
            val multiActiveAfter = multiEdgeRecord(id = "id1", source = "a", target = "b", active = true, version = 1)
            assertEquals(
                EdgeMutationBuilder.buildForUniqueEdge(inactiveBefore, activeAfter, DirectionType.BOTH, emptyList(), emptyList(), emptyList()).status,
                EdgeMutationBuilder.buildForMultiEdge(multiInactiveBefore, multiActiveAfter, DirectionType.BOTH, emptyList(), emptyList(), emptyList()).status,
            )
        }

        @Test
        fun `OUT direction produces single record per type`() {
            val before = edgeRecord(source = "userA", target = "postX", active = false, version = 0)
            val after = edgeRecord(source = "userA", target = "postX", active = true, version = 1)
            val result = EdgeMutationBuilder.buildForUniqueEdge(before, after, DirectionType.OUT, listOf(versionIndex), emptyList(), emptyList())

            assertEquals(1, result.createIndexRecords.size)
            assertEquals(1, result.countRecords.size)
            assertEquals(
                Direction.OUT,
                result.createIndexRecords
                    .first()
                    .key.prefix.direction,
            )
        }
    }
}
