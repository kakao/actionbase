package com.kakao.actionbase.core.metadata.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * A rank key is built from data — an entity id, a dimension value — so it has to survive a value that
 * holds the separator itself.
 */
class AggregationConstantsTest {
    @Test
    fun `two rankings that differ only in where the separator falls get different keys`() {
        val entityHoldsIt = AggregationConstants.Topk.rankSource("shop", "purchased", "popular", "kakao|1", listOf("fruit"))
        val dimensionHoldsIt = AggregationConstants.Topk.rankSource("shop", "purchased", "popular", "kakao", listOf("1|fruit"))

        assertNotEquals(entityHoldsIt, dimensionHoldsIt)
    }

    @Test
    fun `a refresh key separates the same way`() {
        val entityHoldsIt = AggregationConstants.Topk.refreshKey("shop", "purchased", "popular", "kakao|1", "apple", listOf("fruit"))
        val dimensionHoldsIt = AggregationConstants.Topk.refreshKey("shop", "purchased", "popular", "kakao", "apple", listOf("1|fruit"))

        assertNotEquals(entityHoldsIt, dimensionHoldsIt)
    }

    /** Keys already written by the unescaped version have to keep reading back the same row. */
    @Test
    fun `a value without the separator is joined as it was before`() {
        assertEquals(
            "shop|purchased|popular|user1|fruit",
            AggregationConstants.Topk.rankSource("shop", "purchased", "popular", "user1", listOf("fruit")),
        )
    }

    @Test
    fun `a joined value reads back as it was written`() {
        val values = listOf("kakao|1", "fruit", "a\\b", "|", "\\", "", "plain")

        assertEquals(values, AggregationConstants.Topk.splitValues(AggregationConstants.Topk.joinValues(values)))
    }

    @Test
    fun `an escaped separator stays inside its value`() {
        assertEquals("kakao\\|1|fruit", AggregationConstants.Topk.joinValues(listOf("kakao|1", "fruit")))
        assertEquals(listOf("kakao|1", "fruit"), AggregationConstants.Topk.splitValues("kakao\\|1|fruit"))
    }

    @Test
    fun `no dimension values joins and reads back as none`() {
        assertEquals("", AggregationConstants.Topk.joinValues(emptyList()))
        assertEquals(emptyList<String>(), AggregationConstants.Topk.splitValues(""))
    }

    @Test
    fun `an empty value keeps its place next to another`() {
        assertEquals(listOf("", "fruit"), AggregationConstants.Topk.splitValues(AggregationConstants.Topk.joinValues(listOf("", "fruit"))))
        assertEquals(listOf("fruit", ""), AggregationConstants.Topk.splitValues(AggregationConstants.Topk.joinValues(listOf("fruit", ""))))
    }
}
