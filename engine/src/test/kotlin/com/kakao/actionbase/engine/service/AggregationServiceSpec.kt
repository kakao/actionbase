package com.kakao.actionbase.engine.service

import com.kakao.actionbase.core.metadata.QualifiedAggregations
import com.kakao.actionbase.core.metadata.common.AggregationType
import com.kakao.actionbase.engine.AggregationEngine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.mockk.every
import io.mockk.mockk

class AggregationServiceSpec :
    StringSpec({
        val engine = mockk<AggregationEngine>()
        val service = AggregationService(engine)

        "getAggregations forwards results from the engine" {
            val entry = QualifiedAggregations(type = AggregationType.TOPK, database = "db", table = "with_topk")
            every { engine.getListWithAggregations(null) } returns listOf(entry)

            service.getAggregations() shouldContainExactlyInAnyOrder listOf(entry)
        }

        "getAggregations returns empty when the engine has nothing to report" {
            every { engine.getListWithAggregations(null) } returns emptyList()

            service.getAggregations().shouldBeEmpty()
        }

        "getAggregations forwards the requested type filter to the engine" {
            val entry = QualifiedAggregations(type = AggregationType.TOPK, database = "db", table = "with_topk")
            every { engine.getListWithAggregations(AggregationType.TOPK) } returns listOf(entry)

            service.getAggregations(AggregationType.TOPK) shouldContainExactlyInAnyOrder listOf(entry)
        }
    })
