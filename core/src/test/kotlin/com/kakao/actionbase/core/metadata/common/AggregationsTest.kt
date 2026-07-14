package com.kakao.actionbase.core.metadata.common

import com.kakao.actionbase.core.metadata.common.AggregationConstants.TOPK_DATABASE
import com.kakao.actionbase.core.metadata.common.AggregationConstants.TOPK_EXPIRE_TABLE

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AggregationsTest {
    @Test
    fun `supportedTypes contains TOPK when topk is present`() {
        val aggregations = Aggregations(topk = listOf(topk))

        assertEquals(setOf(AggregationType.TOPK), aggregations.supportedTypes)
    }

    @Test
    fun `supportedTypes is empty when no aggregation is defined`() {
        val aggregations = Aggregations()

        assertTrue(aggregations.supportedTypes.isEmpty())
    }

    @Test
    fun `isEmpty is true when no aggregation is defined`() {
        assertTrue(Aggregations().isEmpty())
    }

    @Test
    fun `isEmpty is false when topk is present`() {
        assertFalse(Aggregations(topk = listOf(topk)).isEmpty())
    }

    companion object {
        private val topk = Topk(topk = "topk", table = TopkTable(score = "db.table__topk", expire = "${TOPK_DATABASE}.${TOPK_EXPIRE_TABLE}"))
    }
}
