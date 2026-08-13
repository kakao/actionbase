package com.kakao.actionbase.engine.service

import com.kakao.actionbase.core.metadata.QualifiedAggregations
import com.kakao.actionbase.core.metadata.common.AggregationType
import com.kakao.actionbase.engine.AggregationEngine

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.mockk.every
import io.mockk.mockk

/**
 * `AggregationService` is a thin dispatcher: it forwards metadata lookups to the engine and routes
 * each item to the handler registered for its type. Per-type behavior (top-K ranking) lives
 * in the handlers, and dispatch through a real handler is exercised end-to-end by
 * `MetadataAggQueryControllerE2ETest` — so here we only pin what the dispatcher itself owns.
 */
class AggregationServiceTest {
    private val engine = mockk<AggregationEngine>()

    @Nested
    inner class GetAggregations {
        private val service = AggregationService(engine, emptyList())
        private val entry = QualifiedAggregations(type = AggregationType.TOPK, database = "commerce", table = "orders")

        @Test
        fun `forwards results from the engine`() {
            every { engine.getListWithAggregations(null) } returns listOf(entry)

            service.getAggregations() shouldContainExactlyInAnyOrder listOf(entry)
        }

        @Test
        fun `forwards the type filter to the engine`() {
            every { engine.getListWithAggregations(AggregationType.TOPK) } returns listOf(entry)

            service.getAggregations(AggregationType.TOPK) shouldContainExactlyInAnyOrder listOf(entry)
        }
    }
}
