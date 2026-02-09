package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.core.edge.record.EdgeGroupRecord
import com.kakao.actionbase.core.metadata.common.Direction
import com.kakao.actionbase.core.state.SpecialStateValue
import com.kakao.actionbase.core.state.State
import com.kakao.actionbase.core.state.StateValue
import com.kakao.actionbase.v2.engine.v3.V3CompatibleTableBinding.Companion.mergeQualifiers
import com.kakao.actionbase.v2.engine.v3.V3CompatibleTableBinding.Companion.specialStateValueToNull

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class V3CompatibleTableBindingTest {
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
}
