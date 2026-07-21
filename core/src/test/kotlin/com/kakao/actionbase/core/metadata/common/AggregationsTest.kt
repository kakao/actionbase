package com.kakao.actionbase.core.metadata.common

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AggregationsTest {
    @Test
    fun `supports TOPK when topk is present`() {
        assertTrue(Aggregations(topk = listOf(topk)).supports(AggregationType.TOPK))
    }

    @Test
    fun `does not support TOPK when topk is empty`() {
        assertFalse(Aggregations().supports(AggregationType.TOPK))
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
        private val topk =
            Topk(topk = "topk", entity = "__GLOBAL__", topkDimension = "target", rank = "db.table__topk")
    }
}
