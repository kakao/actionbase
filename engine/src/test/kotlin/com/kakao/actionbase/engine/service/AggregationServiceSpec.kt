package com.kakao.actionbase.engine.service

import com.kakao.actionbase.core.edge.MutationKey
import com.kakao.actionbase.core.edge.payload.AggregationItemPayload
import com.kakao.actionbase.core.edge.payload.DataFrameEdgeAggPayload
import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest.MutationItem
import com.kakao.actionbase.core.edge.payload.EdgePayload
import com.kakao.actionbase.core.edge.payload.MutationResult
import com.kakao.actionbase.core.metadata.QualifiedAggregations
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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class AggregationServiceSpec :
    StringSpec(
        {

            val queryService = mockk<QueryService>()
            val mutationService = mockk<MutationService>()
            val engine = mockk<AggregationEngine>()
            val service = AggregationService(queryService, mutationService, engine)

            // --- getAggregations ---

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

            // --- aggregate ---

            "aggregate returns SUCCESS when mutate succeeds" {
                val topk = topkConfig(name = "t1", rank = "db.rank_tbl")
                val group =
                    groupWithTopks(
                        name = "g1",
                        topks = listOf(topk),
                        directionType = DirectionType.OUT,
                    )
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 42))

                every {
                    mutationService.mutate(any(), any(), any(), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                StepVerifier
                    .create(service.aggregate(AggregationType.TOPK, listOf(item("db", "src", source = "s1", target = "t1"))))
                    .assertNext { results ->
                        results shouldHaveSize 1
                        results[0].status shouldBe "SUCCESS"
                        results[0].error shouldBe null
                    }.verifyComplete()
            }

            "aggregate returns ERROR when mutate reports ERROR status" {
                val topk = topkConfig(name = "t1", rank = "db.rank_tbl")
                val group =
                    groupWithTopks(
                        name = "g1",
                        topks = listOf(topk),
                        directionType = DirectionType.OUT,
                    )
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 1))

                every {
                    mutationService.mutate(any(), any(), any(), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "ERROR")))

                StepVerifier
                    .create(service.aggregate(AggregationType.TOPK, listOf(item("db", "src"))))
                    .assertNext { results ->
                        results shouldHaveSize 1
                        results[0].status shouldBe "ERROR"
                        results[0].error shouldBe null
                    }.verifyComplete()
            }

            "aggregate for OUT direction uses source as entity and the dimension value as rank target" {
                val topk = topkConfig(name = "top_purchased", entity = "source", dimension = "target", rank = "db.rank_tbl")
                val group =
                    groupWithTopks(
                        name = "g_out",
                        topks = listOf(topk),
                        directionType = DirectionType.OUT,
                    )
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 7))

                val mutations = slot<List<MutationItem>>()
                every {
                    mutationService.mutate(any(), any(), capture(mutations), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                StepVerifier
                    .create(service.aggregate(AggregationType.TOPK, listOf(item("db", "src", source = "user1", target = "item1"))))
                    .assertNext { results ->
                        results shouldHaveSize 1
                        results[0].status shouldBe "SUCCESS"
                    }.verifyComplete()

                val edge = mutations.captured.single().edge
                edge.source shouldBe "top_purchased|user1"
                edge.target shouldBe "item1"
            }

            "aggregate for IN direction ranks per target entity" {
                val topk = topkConfig(name = "top_purchased_by", entity = "target", dimension = "source", rank = "db.rank_tbl")
                val group =
                    groupWithTopks(
                        name = "g_in",
                        topks = listOf(topk),
                        directionType = DirectionType.IN,
                    )
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 3))

                val mutations = slot<List<MutationItem>>()
                every {
                    mutationService.mutate(any(), any(), capture(mutations), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                StepVerifier
                    .create(service.aggregate(AggregationType.TOPK, listOf(item("db", "src", source = "user1", target = "item1"))))
                    .assertNext { results ->
                        results shouldHaveSize 1
                        results[0].status shouldBe "SUCCESS"
                    }.verifyComplete()

                val edge = mutations.captured.single().edge
                edge.source shouldBe "top_purchased_by|item1"
                edge.target shouldBe "user1"
            }

            "aggregate for BOTH direction fans out into one OUT and one IN mutation" {
                val topk = topkConfig(name = "top_both", entity = "source", dimension = "target", rank = "db.rank_tbl")
                val group =
                    groupWithTopks(
                        name = "g_both",
                        topks = listOf(topk),
                        directionType = DirectionType.BOTH,
                    )
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 5))

                val mutations = mutableListOf<List<MutationItem>>()
                every {
                    mutationService.mutate(any(), any(), capture(mutations), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                StepVerifier
                    .create(service.aggregate(AggregationType.TOPK, listOf(item("db", "src", source = "user1", target = "item1"))))
                    .assertNext { results ->
                        results shouldHaveSize 2
                    }.verifyComplete()

                val edges = mutations.map { it.single().edge }
                edges.map { it.source to it.target } shouldContainExactlyInAnyOrder
                    listOf(
                        "top_both|user1" to "item1",
                        "top_both|item1" to "item1",
                    )
            }

            "aggregate keeps bucket fields out of the rank source dimensionValues" {
                val topk = topkConfig(name = "top_purchased_1y", entity = "source", dimension = "target", rank = "db.rank_tbl")
                val group =
                    groupWithTopks(
                        name = "g_bucketed",
                        topks = listOf(topk),
                        directionType = DirectionType.OUT,
                        fields =
                            listOf(
                                Group.Field(name = "category"),
                                Group.Field(
                                    name = "purchasedAt",
                                    bucket = Bucket.Date(name = "day", unit = Bucket.ValueUnit.MILLISECOND, timezone = "UTC", format = "yyyy-MM-dd"),
                                ),
                            ),
                    )
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 4))

                val mutations = slot<List<MutationItem>>()
                every {
                    mutationService.mutate(any(), any(), capture(mutations), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                StepVerifier
                    .create(
                        service.aggregate(
                            AggregationType.TOPK,
                            listOf(item("db", "src", source = "user1", target = "item1", properties = mapOf("category" to "fruit", "purchasedAt" to 1_700_000_000_000L))),
                        ),
                    ).assertNext { results ->
                        results shouldHaveSize 1
                        results[0].status shouldBe "SUCCESS"
                    }.verifyComplete()

                val edge = mutations.captured.single().edge
                // category joins the source; the bucketed field is consumed by ranges, not dimensionValues
                edge.source shouldBe "top_purchased_1y|user1|fruit"
                edge.target shouldBe "item1"
            }

            "aggregate resolves a property-backed dimension as the rank target" {
                val topk = topkConfig(name = "top_category", entity = "source", dimension = "category", rank = "db.rank_tbl")
                val group =
                    groupWithTopks(
                        name = "g_props_only",
                        topks = listOf(topk),
                        directionType = DirectionType.OUT,
                        fields =
                            listOf(
                                Group.Field(name = "category"),
                                Group.Field(
                                    name = "purchasedAt",
                                    bucket = Bucket.Date(name = "day", unit = Bucket.ValueUnit.MILLISECOND, timezone = "UTC", format = "yyyy-MM-dd"),
                                ),
                            ),
                    )
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 3))

                val mutations = slot<List<MutationItem>>()
                every {
                    mutationService.mutate(any(), any(), capture(mutations), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                StepVerifier
                    .create(
                        service.aggregate(
                            AggregationType.TOPK,
                            listOf(
                                item(
                                    "db",
                                    "src",
                                    source = "user1",
                                    target = "item1",
                                    properties = mapOf("category" to "fruit", "purchasedAt" to 1_700_000_000_000L),
                                ),
                            ),
                        ),
                    ).assertNext { results ->
                        results shouldHaveSize 1
                        results[0].status shouldBe "SUCCESS"
                    }.verifyComplete()

                val edge = mutations.captured.single().edge
                // dimension = category, so its value becomes the rank target and drops out of dimensionValues
                edge.source shouldBe "top_category|user1"
                edge.target shouldBe "fruit"
            }

            "aggregate joins multiple non-bucket dimension values into the rank source" {
                val topk = topkConfig(name = "top_purchased_1y", entity = "source", dimension = "target", rank = "db.rank_tbl")
                val group =
                    groupWithTopks(
                        name = "g_multi",
                        topks = listOf(topk),
                        directionType = DirectionType.OUT,
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
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 2))

                val mutations = slot<List<MutationItem>>()
                every {
                    mutationService.mutate(any(), any(), capture(mutations), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                StepVerifier
                    .create(
                        service.aggregate(
                            AggregationType.TOPK,
                            listOf(
                                item(
                                    "db",
                                    "src",
                                    source = "user1",
                                    target = "item1",
                                    properties = mapOf("category" to "fruit", "region" to "seoul", "purchasedAt" to 1_700_000_000_000L),
                                ),
                            ),
                        ),
                    ).assertNext { results ->
                        results shouldHaveSize 1
                        results[0].status shouldBe "SUCCESS"
                    }.verifyComplete()

                val edge = mutations.captured.single().edge
                // _target is the dimension (rank target); category + region join the source
                edge.source shouldBe "top_purchased_1y|user1|fruit|seoul"
                edge.target shouldBe "item1"
            }

            // --- aggregate (refresh write) ---

            "aggregate writes a refresh row after the rank row when refreshAfterMillis is positive" {
                val refreshAfter = 60_000L
                val topk =
                    Topk(
                        topk = "top_purchased",
                        entity = "source",
                        dimension = "target",
                        refreshAfterMillis = refreshAfter,
                        rank = "commerce.rank_tbl",
                    )
                val group = groupWithTopks(name = "g_out", topks = listOf(topk), directionType = DirectionType.OUT)
                stubBindingWith(engine, database = "commerce", table = "purchases", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 4))

                val rankMutations = slot<List<MutationItem>>()
                every {
                    mutationService.mutate("commerce", "rank_tbl", capture(rankMutations), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                val refreshMutations = slot<List<MutationItem>>()
                every {
                    mutationService.mutate(
                        AggregationConstants.TOPK_DATABASE,
                        AggregationConstants.TOPK_REFRESH_TABLE,
                        capture(refreshMutations),
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                val before = System.currentTimeMillis()
                StepVerifier
                    .create(service.aggregate(AggregationType.TOPK, listOf(item("commerce", "purchases", source = "user1", target = "item1"))))
                    .assertNext { results ->
                        results shouldHaveSize 1
                        results[0].status shouldBe "SUCCESS"
                    }.verifyComplete()
                val after = System.currentTimeMillis()

                rankMutations.captured
                    .single()
                    .edge.source shouldBe "top_purchased|user1"
                rankMutations.captured
                    .single()
                    .edge.target shouldBe "item1"

                val refreshEdge = refreshMutations.captured.single().edge
                refreshEdge.source shouldBe
                    AggregationConstants.refreshSource(
                        database = "commerce",
                        table = "purchases",
                        topk = "top_purchased",
                        entity = "user1",
                        topkDimensionValue = "item1",
                        dimensionValues = emptyList(),
                    )
                val refreshAt = refreshEdge.properties["refreshAt"] as Long
                refreshAt shouldBeGreaterThanOrEqual (before + refreshAfter)
                refreshAt shouldBeLessThanOrEqual (after + refreshAfter)
                refreshEdge.target shouldBe refreshAt.toString()
                refreshEdge.properties["database"] shouldBe "commerce"
                refreshEdge.properties["table"] shouldBe "purchases"
                refreshEdge.properties["topk"] shouldBe "top_purchased"
                refreshEdge.properties["source"] shouldBe "user1"
                refreshEdge.properties["target"] shouldBe "item1"
                refreshEdge.properties["direction"] shouldBe "OUT"
                refreshEdge.properties["entity"] shouldBe "user1"
                refreshEdge.properties["topkDimensionValue"] shouldBe "item1"
                refreshEdge.properties["dimensionValues"] shouldBe ""
            }

            "aggregate skips the refresh write when refreshAfterMillis is not positive" {
                clearMocks(mutationService)
                val topk = topkConfig(name = "t1", rank = "db.rank_tbl")
                val group = groupWithTopks(name = "g1", topks = listOf(topk), directionType = DirectionType.OUT)
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 1))

                every {
                    mutationService.mutate("db", "rank_tbl", any(), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                StepVerifier
                    .create(service.aggregate(AggregationType.TOPK, listOf(item("db", "src"))))
                    .assertNext { it.single().status shouldBe "SUCCESS" }
                    .verifyComplete()

                verify(exactly = 0) {
                    mutationService.mutate(
                        AggregationConstants.TOPK_DATABASE,
                        AggregationConstants.TOPK_REFRESH_TABLE,
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                }
            }

            "aggregate skips the refresh write when the rank mutate reports ERROR" {
                clearMocks(mutationService)
                val topk =
                    Topk(
                        topk = "t1",
                        entity = "source",
                        dimension = "target",
                        refreshAfterMillis = 60_000L,
                        rank = "db.rank_tbl",
                    )
                val group = groupWithTopks(name = "g1", topks = listOf(topk), directionType = DirectionType.OUT)
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 1))

                every {
                    mutationService.mutate("db", "rank_tbl", any(), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "ERROR")))

                StepVerifier
                    .create(service.aggregate(AggregationType.TOPK, listOf(item("db", "src"))))
                    .assertNext { it.single().status shouldBe "ERROR" }
                    .verifyComplete()

                verify(exactly = 0) {
                    mutationService.mutate(
                        AggregationConstants.TOPK_DATABASE,
                        AggregationConstants.TOPK_REFRESH_TABLE,
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                }
            }

            "aggregate reports ERROR when the refresh mutate reports ERROR" {
                val topk =
                    Topk(
                        topk = "t1",
                        entity = "source",
                        dimension = "target",
                        refreshAfterMillis = 60_000L,
                        rank = "db.rank_tbl",
                    )
                val group = groupWithTopks(name = "g1", topks = listOf(topk), directionType = DirectionType.OUT)
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 1))

                every {
                    mutationService.mutate("db", "rank_tbl", any(), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                every {
                    mutationService.mutate(
                        AggregationConstants.TOPK_DATABASE,
                        AggregationConstants.TOPK_REFRESH_TABLE,
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                } returns Mono.just(listOf(mutationResult(status = "ERROR")))

                StepVerifier
                    .create(service.aggregate(AggregationType.TOPK, listOf(item("db", "src"))))
                    .assertNext { it.single().status shouldBe "ERROR" }
                    .verifyComplete()
            }

            "aggregate maps thrown errors into ERROR status with the error message" {
                val topk = topkConfig(name = "t1", rank = "db.rank_tbl")
                val group =
                    groupWithTopks(
                        name = "g1",
                        topks = listOf(topk),
                        directionType = DirectionType.OUT,
                    )
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
                } returns Mono.error(RuntimeException("agg boom"))

                StepVerifier
                    .create(service.aggregate(AggregationType.TOPK, listOf(item("db", "src"))))
                    .assertNext { results ->
                        results shouldHaveSize 1
                        results[0].status shouldBe "ERROR"
                        results[0].error shouldBe "agg boom"
                    }.verifyComplete()
            }
        },
    )

// region test fixtures

private fun topkConfig(
    name: String,
    entity: String = "source",
    dimension: String = "target",
    rank: String = "${name}__rank",
): Topk = Topk(topk = name, entity = entity, dimension = dimension, rank = rank)

private fun groupWithTopks(
    name: String,
    topks: List<Topk>,
    directionType: DirectionType = DirectionType.BOTH,
    fields: List<Group.Field> = emptyList(),
): Group =
    Group(
        group = name,
        type = GroupType.SUM,
        fields = fields,
        directionType = directionType,
        aggregations = Aggregations(topk = topks),
    )

private fun stringField(): Field = Field(type = PrimitiveType.STRING, comment = "")

private fun stubBindingWith(
    engine: AggregationEngine,
    database: String,
    table: String,
    groups: List<Group>,
) {
    val schema =
        ModelSchema.Edge(
            source = stringField(),
            target = stringField(),
            direction = DirectionType.BOTH,
            groups = groups,
        )
    val binding = mockk<TableBinding>()
    every { binding.schema } returns schema
    every { engine.getTableBinding(database = database, alias = table) } returns binding
}

private fun item(
    database: String,
    table: String,
    source: String = "s",
    target: String = "t",
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
