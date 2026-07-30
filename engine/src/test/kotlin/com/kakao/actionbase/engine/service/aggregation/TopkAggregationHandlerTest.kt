package com.kakao.actionbase.engine.service.aggregation

import com.kakao.actionbase.core.edge.MutationKey
import com.kakao.actionbase.core.edge.payload.AggregationItemPayload
import com.kakao.actionbase.core.edge.payload.DataFrameEdgeAggPayload
import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest.MutationItem
import com.kakao.actionbase.core.edge.payload.EdgePayload
import com.kakao.actionbase.core.edge.payload.MutationResult
import com.kakao.actionbase.core.edge.payload.TopkSweepItem
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
            stubTopkBinding(engine, topkConfig(name = "topk"))

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
            stubTopkBinding(engine, topkConfig(name = "topk"))

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
        fun `OUT ranks the source entity by the dimension value`() {
            stubTopkBinding(engine, topkConfig(name = "top_purchased"))

            every {
                queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Mono.just(aggPayload(count = 7))

            val mutations = slot<List<MutationItem>>()
            every {
                mutationService.mutate(any(), any(), capture(mutations), any(), any(), any(), any())
            } returns Mono.just(listOf(mutationResult(status = "CREATED")))

            StepVerifier
                .create(handler.aggregate(aggregationItemPayload(source = "user1", target = "item1")).collectList())
                .assertNext { results ->
                    results shouldHaveSize 1
                    results[0].status shouldBe "SUCCESS"
                }.verifyComplete()

            val edge = mutations.captured.single().edge
            edge.source shouldBe "commerce|orders|top_purchased|user1"
            edge.target shouldBe "item1"
        }

        @Test
        fun `IN ranks per target entity`() {
            stubTopkBinding(
                engine,
                topkConfig(name = "top_purchased_by", entity = "target", dimension = "source"),
                directionType = DirectionType.IN,
            )

            every {
                queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Mono.just(aggPayload(count = 3))

            val mutations = slot<List<MutationItem>>()
            every {
                mutationService.mutate(any(), any(), capture(mutations), any(), any(), any(), any())
            } returns Mono.just(listOf(mutationResult(status = "CREATED")))

            StepVerifier
                .create(handler.aggregate(aggregationItemPayload(source = "user1", target = "item1")).collectList())
                .assertNext { results ->
                    results shouldHaveSize 1
                    results[0].status shouldBe "SUCCESS"
                }.verifyComplete()

            val edge = mutations.captured.single().edge
            edge.source shouldBe "commerce|orders|top_purchased_by|item1"
            edge.target shouldBe "user1"
        }

        @Test
        fun `BOTH fans out into an OUT and an IN mutation`() {
            stubTopkBinding(engine, topkConfig(name = "top_both"), directionType = DirectionType.BOTH)

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
        fun `keeps bucket fields out of the rank source`() {
            stubTopkBinding(
                engine,
                topkConfig(name = "top_purchased_1y"),
                fields =
                    listOf(
                        Group.Field(name = "category"),
                        Group.Field(
                            name = "purchasedAt",
                            bucket = Bucket.Date(name = "day", unit = Bucket.ValueUnit.MILLISECOND, timezone = "UTC", format = "yyyy-MM-dd"),
                        ),
                    ),
            )

            every {
                queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Mono.just(aggPayload(count = 4))

            val mutations = slot<List<MutationItem>>()
            every {
                mutationService.mutate(any(), any(), capture(mutations), any(), any(), any(), any())
            } returns Mono.just(listOf(mutationResult(status = "CREATED")))

            StepVerifier
                .create(
                    handler
                        .aggregate(aggregationItemPayload(source = "user1", target = "item1", properties = mapOf("category" to "fruit", "purchasedAt" to 1_700_000_000_000L)))
                        .collectList(),
                ).assertNext { results ->
                    results shouldHaveSize 1
                    results[0].status shouldBe "SUCCESS"
                }.verifyComplete()

            val edge = mutations.captured.single().edge
            edge.source shouldBe "commerce|orders|top_purchased_1y|user1|fruit"
            edge.target shouldBe "item1"
        }

        @Test
        fun `resolves a property-backed dimension as the rank target`() {
            stubTopkBinding(
                engine,
                topkConfig(name = "top_category", dimension = "category"),
                fields =
                    listOf(
                        Group.Field(name = "category"),
                        Group.Field(
                            name = "purchasedAt",
                            bucket = Bucket.Date(name = "day", unit = Bucket.ValueUnit.MILLISECOND, timezone = "UTC", format = "yyyy-MM-dd"),
                        ),
                    ),
            )

            every {
                queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Mono.just(aggPayload(count = 3))

            val mutations = slot<List<MutationItem>>()
            every {
                mutationService.mutate(any(), any(), capture(mutations), any(), any(), any(), any())
            } returns Mono.just(listOf(mutationResult(status = "CREATED")))

            StepVerifier
                .create(
                    handler
                        .aggregate(
                            aggregationItemPayload(
                                source = "user1",
                                target = "item1",
                                properties = mapOf("category" to "fruit", "purchasedAt" to 1_700_000_000_000L),
                            ),
                        ).collectList(),
                ).assertNext { results ->
                    results shouldHaveSize 1
                    results[0].status shouldBe "SUCCESS"
                }.verifyComplete()

            val edge = mutations.captured.single().edge
            edge.source shouldBe "commerce|orders|top_category|user1"
            edge.target shouldBe "fruit"
        }

        @Test
        fun `joins multiple non-bucket dimension values into the rank source`() {
            stubTopkBinding(
                engine,
                topkConfig(name = "top_purchased_1y"),
                fields =
                    listOf(
                        Group.Field(name = "_target"),
                        Group.Field(name = "category"),
                        Group.Field(name = "region"),
                        Group.Field(
                            name = "purchasedAt",
                            bucket = Bucket.Date(name = "day", unit = Bucket.ValueUnit.MILLISECOND, timezone = "UTC", format = "yyyy-MM-dd"),
                        ),
                    ),
            )

            every {
                queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Mono.just(aggPayload(count = 2))

            val mutations = slot<List<MutationItem>>()
            every {
                mutationService.mutate(any(), any(), capture(mutations), any(), any(), any(), any())
            } returns Mono.just(listOf(mutationResult(status = "CREATED")))

            StepVerifier
                .create(
                    handler
                        .aggregate(
                            aggregationItemPayload(
                                source = "user1",
                                target = "item1",
                                properties = mapOf("category" to "fruit", "region" to "seoul", "purchasedAt" to 1_700_000_000_000L),
                            ),
                        ).collectList(),
                ).assertNext { results ->
                    results shouldHaveSize 1
                    results[0].status shouldBe "SUCCESS"
                }.verifyComplete()

            val edge = mutations.captured.single().edge
            edge.source shouldBe "commerce|orders|top_purchased_1y|user1|fruit|seoul"
            edge.target shouldBe "item1"
        }

        @Test
        fun `carries the declared properties onto the rank row`() {
            stubTopkBinding(
                engine,
                topkConfig(name = "top_purchased", additionalProperties = listOf("category", "target")),
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
            stubTopkBinding(engine, topkConfig(name = "top_purchased", refreshAfterMillis = refreshAfter))

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
                    AggregationConstants.Topk.REFRESH_TABLE,
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

        @Test
        fun `carries the declared properties into the refresh message`() {
            stubTopkBinding(
                engine,
                topkConfig(name = "top_purchased", refreshAfterMillis = 60_000L, additionalProperties = listOf("category")),
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
                    AggregationConstants.Topk.REFRESH_TABLE,
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
            stubTopkBinding(engine, topkConfig(name = "topk"))

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
                    AggregationConstants.Topk.REFRESH_TABLE,
                    any(),
                )
            }
        }

        @Test
        fun `skips the refresh when the rank write fails`() {
            stubTopkBinding(engine, topkConfig(name = "topk", refreshAfterMillis = 60_000L))

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
                    AggregationConstants.Topk.REFRESH_TABLE,
                    any(),
                )
            }
        }

        @Test
        fun `reports ERROR when the refresh enqueue fails`() {
            stubTopkBinding(engine, topkConfig(name = "topk", refreshAfterMillis = 60_000L))

            every {
                queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Mono.just(aggPayload(count = 1))

            every {
                mutationService.mutate("commerce", "orders__topk", any(), any(), any(), any(), any())
            } returns Mono.just(listOf(mutationResult(status = "CREATED")))

            every {
                queueService.enqueue(
                    AggregationConstants.Topk.DATABASE,
                    AggregationConstants.Topk.REFRESH_TABLE,
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
            stubTopkBinding(engine, topkConfig(name = "topk"))

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

    @Nested
    inner class Sweep {
        @Test
        fun `recomputes the ranking and rewrites the rank row`() {
            stubTopkBinding(engine, topkConfig(name = "top_purchased"))

            every {
                queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Mono.just(aggPayload(count = 9))

            val mutations = slot<List<MutationItem>>()
            every {
                mutationService.mutate("commerce", "orders__topk", capture(mutations), any(), any(), any(), any())
            } returns Mono.just(listOf(mutationResult(status = "CREATED")))

            StepVerifier
                .create(handler.sweep(sweepTarget(topk = "top_purchased")))
                .assertNext { result ->
                    result.status shouldBe "SUCCESS"
                    result.topk shouldBe "top_purchased"
                    result.entity shouldBe "user1"
                }.verifyComplete()

            val edge = mutations.captured.single().edge
            edge.source shouldBe "commerce|orders|top_purchased|user1"
            edge.target shouldBe "item1"
        }

        @Test
        fun `rewrites the carried properties onto the rank row`() {
            stubTopkBinding(engine, topkConfig(name = "top_purchased"))

            every {
                queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Mono.just(aggPayload(count = 9))

            val mutations = slot<List<MutationItem>>()
            every {
                mutationService.mutate("commerce", "orders__topk", capture(mutations), any(), any(), any(), any())
            } returns Mono.just(listOf(mutationResult(status = "CREATED")))

            StepVerifier
                .create(handler.sweep(sweepTarget(topk = "top_purchased", properties = mapOf("category" to "fruit"))))
                .assertNext { it.status shouldBe "SUCCESS" }
                .verifyComplete()

            // sweep has no source edge, so the property values ride in on the sweep item and are
            // re-written as one JSON string under `additionalProperties`.
            val edge = mutations.captured.single().edge
            edge.properties.containsKey("metric") shouldBe true
            edge.properties["additionalProperties"] shouldBe """{"category":"fruit"}"""
        }

        @Test
        fun `is skipped when the topk is not defined on the table`() {
            stubTopkBinding(engine, topkConfig(name = "top_purchased"))

            StepVerifier
                .create(handler.sweep(sweepTarget(topk = "unknown_topk")))
                .assertNext { it.status shouldBe "SKIPPED" }
                .verifyComplete()

            verify(exactly = 0) { mutationService.mutate(any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        fun `IN aggregates from the target endpoint`() {
            stubTopkBinding(
                engine,
                topkConfig(name = "top_purchased_by", entity = "target", dimension = "source"),
                directionType = DirectionType.IN,
            )

            val starts = slot<List<Any>>()
            every {
                queryService.agg(any(), any(), any(), capture(starts), any(), any(), any(), any())
            } returns Mono.just(aggPayload(count = 4))

            every {
                mutationService.mutate("commerce", "orders__topk", any(), any(), any(), any(), any())
            } returns Mono.just(listOf(mutationResult(status = "CREATED")))

            StepVerifier
                .create(
                    handler.sweep(
                        sweepTarget(topk = "top_purchased_by", direction = "IN", entity = "item1", topkDimensionValue = "user1"),
                    ),
                ).assertNext { it.status shouldBe "SUCCESS" }
                .verifyComplete()

            // IN ranks per target entity, so the aggregation starts from the target endpoint.
            starts.captured shouldBe listOf("item1")
        }
    }
}

// region test fixtures

// rank table naming rule: the source table name with the `__topk` suffix
private fun topkConfig(
    name: String,
    entity: String = "source",
    dimension: String = "target",
    rank: String = "commerce.orders__topk",
    refreshAfterMillis: Long = 0,
    additionalProperties: List<String> = emptyList(),
): Topk =
    Topk(
        topk = name,
        entity = entity,
        dimension = dimension,
        rank = rank,
        refreshAfterMillis = refreshAfterMillis,
        additionalProperties = additionalProperties,
    )

private fun sweepTarget(
    topk: String,
    database: String = "commerce",
    table: String = "orders",
    source: String = "user1",
    target: String = "item1",
    direction: String = "OUT",
    ranges: String = "_target:eq:item1",
    entity: String = "user1",
    topkDimensionValue: String = "item1",
    dimensionValues: String = "",
    properties: Map<String, String> = emptyMap(),
): TopkSweepItem =
    TopkSweepItem(
        database = database,
        table = table,
        topk = topk,
        source = source,
        target = target,
        direction = direction,
        ranges = ranges,
        entity = entity,
        topkDimensionValue = topkDimensionValue,
        dimensionValues = dimensionValues,
        properties = properties,
        refreshAt = 123L,
    )

/** Stubs the binding for a single group carrying a single topk — the shape every case here needs. */
private fun stubTopkBinding(
    engine: AggregationEngine,
    topk: Topk,
    database: String = "commerce",
    table: String = "orders",
    directionType: DirectionType = DirectionType.OUT,
    fields: List<Group.Field> = emptyList(),
) {
    val group =
        Group(
            group = "purchased_count",
            type = GroupType.SUM,
            fields = fields,
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
