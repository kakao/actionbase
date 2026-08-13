package com.kakao.actionbase.engine.service

import com.kakao.actionbase.core.edge.payload.AggregationItemPayload
import com.kakao.actionbase.core.edge.payload.AggregationResult
import com.kakao.actionbase.core.edge.payload.AggregationSweepResult
import com.kakao.actionbase.core.edge.payload.EdgePayload
import com.kakao.actionbase.core.edge.payload.SweepItem
import com.kakao.actionbase.core.edge.payload.SweepItemPayload
import com.kakao.actionbase.core.edge.payload.TopkSweepItem
import com.kakao.actionbase.core.metadata.QualifiedAggregations
import com.kakao.actionbase.core.metadata.common.AggregationType
import com.kakao.actionbase.engine.AggregationEngine
import com.kakao.actionbase.engine.service.aggregation.AggregationHandler

import java.time.Duration

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.mockk.every
import io.mockk.mockk
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

/**
 * `AggregationService` is a thin dispatcher: it forwards metadata lookups to the engine and routes
 * each item to the handler registered for its type. Per-type behavior (top-K ranking, refresh) lives
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

    @Nested
    inner class Aggregate {
        @Test
        fun `completes with an empty list when given no items`() {
            val service = AggregationService(engine, listOf(DelayedHandler()))

            StepVerifier
                .create(service.aggregate(items = emptyList()))
                .assertNext { results -> results shouldContainExactly emptyList() }
                .verifyComplete()
        }

        /**
         * `AggregationResult` carries no field naming the item it came from, so a caller can only pair a
         * result back to its request by position. That holds only if the dispatcher emits in request order —
         * otherwise the same batch returns differently shaped responses depending on how fast each handler
         * happened to answer. The delays here stand in for that variance: the slowest item is requested first.
         */
        @Test
        fun `returns results in the order the items were given`() {
            val service =
                AggregationService(
                    engine,
                    listOf(
                        DelayedHandler(
                            delays =
                                mapOf(
                                    "user1" to Duration.ofMillis(150),
                                    "user2" to Duration.ofMillis(80),
                                    "user3" to Duration.ofMillis(10),
                                ),
                        ),
                    ),
                )

            StepVerifier
                .withVirtualTime { service.aggregate(items = listOf(item("user1"), item("user2"), item("user3"))) }
                .expectSubscription()
                .thenAwait(Duration.ofMinutes(1))
                .assertNext { results -> results.map { it.source } shouldContainExactly listOf("user1", "user2", "user3") }
                .verifyComplete()
        }

        /**
         * Handlers arrive as an injected `List<AggregationHandler>` (`GraphConfiguration`), and the dispatcher
         * keys them by type. Two beans claiming one type is a wiring mistake, and the map resolves it by
         * silently keeping the last — pinned here so that changing how it resolves stays a deliberate choice.
         */
        @Test
        fun `keeps only the last handler registered for a type`() {
            val service = AggregationService(engine, listOf(DelayedHandler(tag = "first"), DelayedHandler(tag = "second")))

            StepVerifier
                .create(service.aggregate(items = listOf(item("user1"))))
                .assertNext { results -> results.map { it.table } shouldContainExactly listOf("second") }
                .verifyComplete()
        }
    }

    @Nested
    inner class Sweep {
        @Test
        fun `errors when no handler is registered for the type`() {
            val service = AggregationService(engine, emptyList())

            StepVerifier
                .create(service.sweep(listOf(sweepItem())))
                .verifyError(IllegalStateException::class.java)
        }
    }
}

// region test fixtures

private fun item(source: String): AggregationItemPayload =
    AggregationItemPayload(
        database = "commerce",
        table = "orders",
        edge =
            EdgePayload(
                version = 1L,
                source = source,
                target = "item1",
                properties = emptyMap(),
                context = emptyMap(),
            ),
    )

private fun sweepItem(): SweepItem =
    SweepItem(
        type = AggregationType.TOPK,
        item =
            TopkSweepItem(
                database = "commerce",
                table = "orders",
                topk = "top_purchased",
                source = "user1",
                target = "item1",
                direction = "OUT",
                entity = "user1",
                topkDimensionValue = "item1",
            ),
    )

/**
 * Answers after a delay the test fixes per item, standing in for a real handler whose storage calls come
 * back out of order. Without it every inner publisher completes on the subscribing thread and the
 * dispatcher's interleaving never becomes observable. The ordering tests drive this under
 * `withVirtualTime`, so the delays cost no wall-clock time.
 */
private class DelayedHandler(
    private val tag: String = "orders",
    private val delays: Map<String, Duration> = emptyMap(),
) : AggregationHandler {
    override val type: AggregationType = AggregationType.TOPK

    override fun aggregate(item: AggregationItemPayload): Flux<AggregationResult> {
        val source = item.edge.source.toString()

        return Mono
            .just(
                AggregationResult(
                    database = item.database,
                    table = tag,
                    source = source,
                    target = item.edge.target.toString(),
                    status = "SUCCESS",
                    error = null,
                ),
            ).delayElement(delays[source] ?: Duration.ZERO)
            .flux()
    }

    override fun sweep(item: SweepItemPayload): Mono<AggregationSweepResult> = error("this fake stands in on the aggregate path only")
}

// endregion
