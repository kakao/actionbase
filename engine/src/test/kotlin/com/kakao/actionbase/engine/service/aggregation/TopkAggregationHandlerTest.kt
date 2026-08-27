package com.kakao.actionbase.engine.service.aggregation

import com.kakao.actionbase.core.edge.MutationKey
import com.kakao.actionbase.core.edge.payload.AggregationItemPayload
import com.kakao.actionbase.core.edge.payload.DataFrameEdgeAggPayload
import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest.MutationItem
import com.kakao.actionbase.core.edge.payload.EdgePayload
import com.kakao.actionbase.core.edge.payload.MutationResult
import com.kakao.actionbase.core.metadata.common.AggregationConstants
import com.kakao.actionbase.core.metadata.common.AggregationType
import com.kakao.actionbase.core.metadata.common.Aggregations
import com.kakao.actionbase.core.metadata.common.Bucket
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Field
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.GroupType
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.metadata.common.Topk
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.engine.AggregationEngine
import com.kakao.actionbase.engine.binding.TableBinding
import com.kakao.actionbase.engine.queue.EnqueueRequest
import com.kakao.actionbase.engine.queue.EnqueueResponse
import com.kakao.actionbase.engine.queue.EnqueueResult
import com.kakao.actionbase.engine.queue.QueueService
import com.kakao.actionbase.engine.service.MutationService
import com.kakao.actionbase.engine.service.QueryService

import java.time.Instant

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class TopkAggregationHandlerTest {
    private val queryService = mockk<QueryService>()
    private val mutationService = mockk<MutationService>()
    private val queueService = mockk<QueueService>()
    private val engine = mockk<AggregationEngine>()
    private val handler = TopkAggregationHandler(queryService, mutationService, queueService, engine)

    @Nested
    inner class Aggregate {
        @Test
        fun `returns SUCCESS when mutate succeeds`() {
            stubTopkBinding(engine = engine, topk = topkConfig(name = "topk"))

            every {
                queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Mono.just(aggPayload(count = 42))

            every {
                mutationService.mutate(any(), any(), any(), any(), any(), any(), any())
            } returns Mono.just(listOf(mutationResult(status = "CREATED")))

            StepVerifier
                .create(handler.aggregate(aggregationItemPayload(source = "user1", target = "item1")).collectList())
                .assertNext { results ->
                    results shouldHaveSize 1
                    results[0].status shouldBe "SUCCESS"
                    results[0].error shouldBe null
                }.verifyComplete()
        }

        @Test
        fun `returns ERROR when the mutate fails`() {
            stubTopkBinding(engine = engine, topk = topkConfig(name = "topk"))

            every {
                queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Mono.just(aggPayload(count = 1))

            every {
                mutationService.mutate(any(), any(), any(), any(), any(), any(), any())
            } returns Mono.just(listOf(mutationResult(status = "ERROR")))

            StepVerifier
                .create(handler.aggregate(aggregationItemPayload()).collectList())
                .assertNext { results ->
                    results shouldHaveSize 1
                    results[0].status shouldBe "ERROR"
                    results[0].error shouldBe null
                }.verifyComplete()
        }

        @Test
        fun `BOTH fans out into an OUT and an IN mutation`() {
            stubTopkBinding(engine = engine, topk = topkConfig(name = "top_both"), directionType = DirectionType.BOTH)

            every {
                queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Mono.just(aggPayload(count = 5))

            val mutations = mutableListOf<List<MutationItem>>()
            every {
                mutationService.mutate(any(), any(), capture(mutations), any(), any(), any(), any())
            } returns Mono.just(listOf(mutationResult(status = "CREATED")))

            StepVerifier
                .create(handler.aggregate(aggregationItemPayload(source = "user1", target = "item1")).collectList())
                .assertNext { results ->
                    results shouldHaveSize 2
                }.verifyComplete()

            val edges = mutations.map { it.single().edge }
            edges.map { it.source to it.target } shouldContainExactlyInAnyOrder
                listOf(
                    "commerce|orders|top_both|user1" to "item1",
                    "commerce|orders|top_both|item1" to "item1",
                )
        }

        @Test
        fun `carries the declared properties onto the rank row`() {
            stubTopkBinding(
                engine = engine,
                topk = topkConfig(name = "top_purchased", additionalProperties = listOf("category", "target")),
            )

            every {
                queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Mono.just(aggPayload(count = 4))

            val mutations = slot<List<MutationItem>>()
            every {
                mutationService.mutate("commerce", "orders__topk", capture(mutations), any(), any(), any(), any())
            } returns Mono.just(listOf(mutationResult(status = "CREATED")))

            StepVerifier
                .create(
                    handler
                        .aggregate(aggregationItemPayload(source = "user1", target = "item1", properties = mapOf("category" to "fruit")))
                        .collectList(),
                ).assertNext { it.single().status shouldBe "SUCCESS" }
                .verifyComplete()

            val edge = mutations.captured.single().edge
            edge.properties.containsKey("metric") shouldBe true
            edge.properties["additionalProperties"] shouldBe """{"category":"fruit","target":"item1"}"""
        }

        @Test
        fun `enqueues a refresh after the rank write`() {
            val refreshAfter = 60_000L
            stubTopkBinding(engine = engine, topk = topkConfig(name = "top_purchased", refreshAfterMillis = refreshAfter, ranges = SLIDING_WINDOW), bucketed = true)

            every {
                queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Mono.just(aggPayload(count = 4))

            val rankMutations = slot<List<MutationItem>>()
            every {
                mutationService.mutate("commerce", "orders__topk", capture(rankMutations), any(), any(), any(), any())
            } returns Mono.just(listOf(mutationResult(status = "CREATED")))

            val refreshRequest = slot<EnqueueRequest>()
            every {
                queueService.enqueue(
                    AggregationConstants.Topk.DATABASE,
                    AggregationConstants.Topk.REFRESH_QUEUE,
                    capture(refreshRequest),
                )
            } returns Mono.just(enqueueResponse(status = "CREATED"))

            val before = System.currentTimeMillis()
            StepVerifier
                .create(handler.aggregate(aggregationItemPayload(source = "user1", target = "item1")).collectList())
                .assertNext { results ->
                    results shouldHaveSize 1
                    results[0].status shouldBe "SUCCESS"
                }.verifyComplete()
            val after = System.currentTimeMillis()

            rankMutations.captured
                .single()
                .edge.source shouldBe "commerce|orders|top_purchased|user1"
            rankMutations.captured
                .single()
                .edge.target shouldBe "item1"

            val message = refreshRequest.captured.messages.single()
            message.key shouldBe
                AggregationConstants.Topk.refreshKey(
                    database = "commerce",
                    table = "orders",
                    topk = "top_purchased",
                    entity = "user1",
                    topkDimensionValue = "item1",
                    dimensionValues = emptyList(),
                )
            val refreshAt = message.seq
            refreshAt shouldBeGreaterThanOrEqual (before + refreshAfter)
            refreshAt shouldBeLessThanOrEqual (after + refreshAfter)

            val refresh = message.value as TopkRefreshMessage
            refresh.type shouldBe AggregationType.TOPK
            refresh.item.database shouldBe "commerce"
            refresh.item.table shouldBe "orders"
            refresh.item.topk shouldBe "top_purchased"
            refresh.item.source shouldBe "user1"
            refresh.item.target shouldBe "item1"
            refresh.item.direction shouldBe "OUT"
            refresh.item.entity shouldBe "user1"
            refresh.item.topkDimensionValue shouldBe "item1"
            refresh.item.dimensionValues shouldBe ""
            refresh.item.refreshAt shouldBe refreshAt
        }

        /**
         * The queue decides which sweeper picks the refresh up, so a ranking that names one has to be
         * enqueued there and nowhere else. Every other test here names none and asserts the shared queue,
         * which is the other half of the contract.
         */
        @Test
        fun `a refresh goes onto the queue the declaration names`() {
            stubTopkBinding(
                engine = engine,
                topk =
                    topkConfig(
                        name = "top_purchased",
                        refreshAfterMillis = 60_000L,
                        refreshQueue = "purchase_refresh",
                        ranges = SLIDING_WINDOW,
                    ),
                bucketed = true,
            )

            every {
                queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Mono.just(aggPayload(count = 4))

            every {
                mutationService.mutate("commerce", "orders__topk", any(), any(), any(), any(), any())
            } returns Mono.just(listOf(mutationResult(status = "CREATED")))

            val refreshRequest = slot<EnqueueRequest>()
            every {
                queueService.enqueue(AggregationConstants.Topk.DATABASE, "purchase_refresh", capture(refreshRequest))
            } returns Mono.just(enqueueResponse(status = "CREATED"))

            StepVerifier
                .create(handler.aggregate(aggregationItemPayload(source = "user1", target = "item1")).collectList())
                .assertNext { results -> results.single().status shouldBe "SUCCESS" }
                .verifyComplete()

            refreshRequest.captured.messages
                .single()
                .key shouldBe
                AggregationConstants.Topk.refreshKey(
                    database = "commerce",
                    table = "orders",
                    topk = "top_purchased",
                    entity = "user1",
                    topkDimensionValue = "item1",
                    dimensionValues = emptyList(),
                )

            verify(exactly = 0) {
                queueService.enqueue(any(), AggregationConstants.Topk.REFRESH_QUEUE, any())
            }
        }

        @Test
        fun `carries the declared properties into the refresh message`() {
            stubTopkBinding(
                engine = engine,
                topk = topkConfig(name = "top_purchased", refreshAfterMillis = 60_000L, additionalProperties = listOf("category"), ranges = SLIDING_WINDOW),
                bucketed = true,
            )

            every {
                queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Mono.just(aggPayload(count = 4))
            every {
                mutationService.mutate("commerce", "orders__topk", any(), any(), any(), any(), any())
            } returns Mono.just(listOf(mutationResult(status = "CREATED")))

            val refreshRequest = slot<EnqueueRequest>()
            every {
                queueService.enqueue(
                    AggregationConstants.Topk.DATABASE,
                    AggregationConstants.Topk.REFRESH_QUEUE,
                    capture(refreshRequest),
                )
            } returns Mono.just(enqueueResponse(status = "CREATED"))

            StepVerifier
                .create(
                    handler
                        .aggregate(aggregationItemPayload(source = "user1", target = "item1", properties = mapOf("category" to "fruit")))
                        .collectList(),
                ).assertNext { it.single().status shouldBe "SUCCESS" }
                .verifyComplete()

            val refresh =
                refreshRequest.captured.messages
                    .single()
                    .value as TopkRefreshMessage
            refresh.item.properties shouldBe mapOf("category" to "fruit")
        }

        @Test
        fun `skips the refresh when refreshAfterMillis is not positive`() {
            stubTopkBinding(engine = engine, topk = topkConfig(name = "topk"))

            every {
                queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Mono.just(aggPayload(count = 1))

            every {
                mutationService.mutate("commerce", "orders__topk", any(), any(), any(), any(), any())
            } returns Mono.just(listOf(mutationResult(status = "CREATED")))

            StepVerifier
                .create(handler.aggregate(aggregationItemPayload()).collectList())
                .assertNext { it.single().status shouldBe "SUCCESS" }
                .verifyComplete()

            verify(exactly = 0) {
                queueService.enqueue(
                    AggregationConstants.Topk.DATABASE,
                    AggregationConstants.Topk.REFRESH_QUEUE,
                    any(),
                )
            }
        }

        @Test
        fun `skips the refresh when the rank write fails`() {
            stubTopkBinding(engine = engine, topk = topkConfig(name = "topk", refreshAfterMillis = 60_000L, ranges = SLIDING_WINDOW), bucketed = true)

            every {
                queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Mono.just(aggPayload(count = 1))

            every {
                mutationService.mutate("commerce", "orders__topk", any(), any(), any(), any(), any())
            } returns Mono.just(listOf(mutationResult(status = "ERROR")))

            StepVerifier
                .create(handler.aggregate(aggregationItemPayload()).collectList())
                .assertNext { it.single().status shouldBe "ERROR" }
                .verifyComplete()

            verify(exactly = 0) {
                queueService.enqueue(
                    AggregationConstants.Topk.DATABASE,
                    AggregationConstants.Topk.REFRESH_QUEUE,
                    any(),
                )
            }
        }

        @Test
        fun `schedules the refresh for the instant the event leaves the window`() {
            val refreshAfter = 365L * 24 * 60 * 60 * 1000
            // 14:32 on a day bucket is stored as that day, so the remainder must not carry into the due time.
            val purchasedAt = Instant.parse("2026-01-01T14:32:00Z").toEpochMilli()

            stubTopkBinding(
                engine = engine,
                topk = topkConfig(name = "top_purchased", refreshAfterMillis = refreshAfter, ranges = SLIDING_WINDOW),
                bucketed = true,
            )

            every {
                queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Mono.just(aggPayload(count = 1))
            every {
                mutationService.mutate("commerce", "orders__topk", any(), any(), any(), any(), any())
            } returns Mono.just(listOf(mutationResult(status = "CREATED")))

            val refreshRequest = slot<EnqueueRequest>()
            every {
                queueService.enqueue(AggregationConstants.Topk.DATABASE, AggregationConstants.Topk.REFRESH_QUEUE, capture(refreshRequest))
            } returns Mono.just(enqueueResponse(status = "CREATED"))

            StepVerifier
                .create(handler.aggregate(aggregationItemPayload(properties = mapOf(PURCHASED_AT to purchasedAt))).collectList())
                .assertNext { results -> results.single().status shouldBe "SUCCESS" }
                .verifyComplete()

            // The purchase's day stays inside a `now-365d` window until the day after it turns 365 days old.
            refreshRequest.captured.messages
                .single()
                .seq shouldBe Instant.parse("2027-01-02T00:00:00Z").toEpochMilli()
        }

        @Test
        fun `schedules from the field's value when the bucket carries a name of its own`() {
            val refreshAfter = 365L * 24 * 60 * 60 * 1000
            val purchasedAt = Instant.parse("2026-01-01T14:32:00Z").toEpochMilli()

            stubTopkBinding(
                engine = engine,
                topk = topkConfig(name = "top_purchased", refreshAfterMillis = refreshAfter, ranges = ALIASED_SLIDING_WINDOW),
                bucketed = true,
                bucket = ALIASED_DAY_BUCKET,
            )

            every {
                queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Mono.just(aggPayload(count = 1))
            every {
                mutationService.mutate("commerce", "orders__topk", any(), any(), any(), any(), any())
            } returns Mono.just(listOf(mutationResult(status = "CREATED")))

            val refreshRequest = slot<EnqueueRequest>()
            every {
                queueService.enqueue(AggregationConstants.Topk.DATABASE, AggregationConstants.Topk.REFRESH_QUEUE, capture(refreshRequest))
            } returns Mono.just(enqueueResponse(status = "CREATED"))

            StepVerifier
                .create(handler.aggregate(aggregationItemPayload(properties = mapOf(PURCHASED_AT to purchasedAt))).collectList())
                .assertNext { results -> results.single().status shouldBe "SUCCESS" }
                .verifyComplete()

            refreshRequest.captured.messages
                .single()
                .seq shouldBe Instant.parse("2027-01-02T00:00:00Z").toEpochMilli()
        }

        @Test
        fun `reports ERROR when the refresh enqueue fails`() {
            stubTopkBinding(engine = engine, topk = topkConfig(name = "topk", refreshAfterMillis = 60_000L, ranges = SLIDING_WINDOW), bucketed = true)

            every {
                queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Mono.just(aggPayload(count = 1))

            every {
                mutationService.mutate("commerce", "orders__topk", any(), any(), any(), any(), any())
            } returns Mono.just(listOf(mutationResult(status = "CREATED")))

            every {
                queueService.enqueue(
                    AggregationConstants.Topk.DATABASE,
                    AggregationConstants.Topk.REFRESH_QUEUE,
                    any(),
                )
            } returns Mono.just(enqueueResponse(status = "ERROR"))

            StepVerifier
                .create(handler.aggregate(aggregationItemPayload()).collectList())
                .assertNext { it.single().status shouldBe "ERROR" }
                .verifyComplete()
        }

        @Test
        fun `maps a thrown error to ERROR with its message`() {
            stubTopkBinding(engine = engine, topk = topkConfig(name = "topk"))

            every {
                queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Mono.error(RuntimeException("agg boom"))

            StepVerifier
                .create(handler.aggregate(aggregationItemPayload()).collectList())
                .assertNext { results ->
                    results shouldHaveSize 1
                    results[0].status shouldBe "ERROR"
                    results[0].error shouldBe "agg boom"
                }.verifyComplete()
        }
    }
}

// region test fixtures

private const val PURCHASED_AT = "purchasedAt"

private val DAY_BUCKET = Bucket.Date(name = PURCHASED_AT, unit = Bucket.ValueUnit.MILLISECOND, timezone = "UTC", format = "yyyy-MM-dd")

private const val SLIDING_WINDOW = "$PURCHASED_AT:bt:now-365d,now"

/** The same bucket, named for the value it yields rather than for the field it reads. */
private val ALIASED_DAY_BUCKET = Bucket.Date(name = "time", unit = Bucket.ValueUnit.MILLISECOND, timezone = "UTC", format = "yyyy-MM-dd")

private const val ALIASED_SLIDING_WINDOW = "time:bt:now-365d,now"

// rank table naming rule: the source table name with the `__topk` suffix
private fun topkConfig(
    name: String,
    entity: String = "source",
    dimension: String = "target",
    rank: String = "commerce.orders__topk",
    refreshAfterMillis: Long = 0,
    refreshQueue: String = AggregationConstants.Topk.REFRESH_QUEUE,
    additionalProperties: List<String> = emptyList(),
    ranges: String = "",
): Topk =
    Topk(
        topk = name,
        entity = entity,
        dimension = dimension,
        rank = rank,
        refreshAfterMillis = refreshAfterMillis,
        refreshQueue = refreshQueue,
        additionalProperties = additionalProperties,
        ranges = ranges,
    )

private fun stubTopkBinding(
    engine: AggregationEngine,
    topk: Topk,
    database: String = "commerce",
    table: String = "orders",
    directionType: DirectionType = DirectionType.OUT,
    bucketed: Boolean = false,
    bucket: Bucket.Date = DAY_BUCKET,
) {
    val group =
        Group(
            group = "purchased_count",
            type = GroupType.SUM,
            fields = if (bucketed) listOf(Group.Field(PURCHASED_AT, bucket = bucket)) else emptyList(),
            directionType = directionType,
            aggregations = Aggregations(topk = listOf(topk)),
        )
    val binding = mockk<TableBinding>()
    every { binding.schema } returns ModelSchema.Edge(source = Field(type = PrimitiveType.STRING, comment = ""), target = Field(type = PrimitiveType.STRING, comment = ""), direction = DirectionType.BOTH, groups = listOf(group))
    every { binding.table } returns table
    every { engine.getTableBinding(database = database, alias = table) } returns binding
}

private fun aggregationItemPayload(
    database: String = "commerce",
    table: String = "orders",
    source: String = "user1",
    target: String = "item1",
    properties: Map<String, Any?> = emptyMap(),
): AggregationItemPayload =
    AggregationItemPayload(
        database = database,
        table = table,
        edge =
            EdgePayload(
                version = 1L,
                source = source,
                target = target,
                properties = properties,
                context = emptyMap(),
            ),
    )

private fun aggPayload(count: Int): DataFrameEdgeAggPayload = DataFrameEdgeAggPayload(groups = emptyList(), count = count, context = emptyMap())

private fun mutationResult(status: String): MutationResult = MutationResult.of(key = MutationKey.SourceTarget("s", "t"), count = 1, status = status)

private fun enqueueResponse(status: String): EnqueueResponse =
    EnqueueResponse(
        accepted = if (status == "CREATED") 1 else 0,
        results = listOf(EnqueueResult(partition = 0, id = "01", status = status)),
    )

// endregion
