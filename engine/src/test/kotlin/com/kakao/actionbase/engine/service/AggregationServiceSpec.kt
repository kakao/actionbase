package com.kakao.actionbase.engine.service

import com.kakao.actionbase.core.edge.MutationKey
import com.kakao.actionbase.core.edge.payload.AggregationItemPayload
import com.kakao.actionbase.core.edge.payload.DataFrameEdgeAggPayload
import com.kakao.actionbase.core.edge.payload.DataFrameEdgePayload
import com.kakao.actionbase.core.edge.payload.EdgeAggPayload
import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest.MutationItem
import com.kakao.actionbase.core.edge.payload.EdgePayload
import com.kakao.actionbase.core.edge.payload.MutationResult
import com.kakao.actionbase.core.edge.payload.RefreshEntryPayload
import com.kakao.actionbase.core.metadata.QualifiedAggregations
import com.kakao.actionbase.core.metadata.common.AggregationConstants
import com.kakao.actionbase.core.metadata.common.AggregationType
import com.kakao.actionbase.core.metadata.common.Aggregations
import com.kakao.actionbase.core.metadata.common.Bucket
import com.kakao.actionbase.core.metadata.common.Direction
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Field
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.GroupType
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.metadata.common.Topk
import com.kakao.actionbase.core.metadata.common.TopkTable
import com.kakao.actionbase.core.state.EventType
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.engine.AggregationEngine
import com.kakao.actionbase.engine.binding.TableBinding

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
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
                val topk = topkConfig(name = "t1", table = TopkTable(score = "db.score_tbl"))
                val group =
                    groupWithTopks(
                        name = "g1",
                        topks = listOf(topk),
                        directionType = DirectionType.OUT,
                    )
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any<Direction>(), any(), any(), any())
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
                val topk = topkConfig(name = "t1", table = TopkTable(score = "db.score_tbl"))
                val group =
                    groupWithTopks(
                        name = "g1",
                        topks = listOf(topk),
                        directionType = DirectionType.OUT,
                    )
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any<Direction>(), any(), any(), any())
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

            "aggregate stores a GLOBAL topk segment in the source key and keeps only score as a property" {
                val topk =
                    topkConfig(name = "top_seg", table = TopkTable(score = "db.score_tbl"))
                        .copy(entity = AggregationConstants.GLOBAL_ENTITY, ranges = "gender:eq:{gender}")
                val group =
                    groupWithTopks(
                        name = "g_seg",
                        topks = listOf(topk),
                        directionType = DirectionType.OUT,
                    )
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any<Direction>(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 3))

                val mutations = slot<List<MutationItem>>()
                every {
                    mutationService.mutate(any(), any(), capture(mutations), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                val baseItem = item("db", "src", source = "user1", target = "item1")
                val item = baseItem.copy(edge = baseItem.edge.copy(properties = mapOf("gender" to "F")))

                StepVerifier
                    .create(service.aggregate(AggregationType.TOPK, listOf(item)))
                    .assertNext { results -> results shouldHaveSize 1 }
                    .verifyComplete()

                val edge = mutations.captured.single().edge
                edge.source shouldBe "db.src:top_seg:OUT:${AggregationConstants.GLOBAL_ENTITY}:gender:eq:F"
                edge.properties["score"] shouldBe 3.0
                edge.properties.containsKey("segment") shouldBe false
            }

            "aggregate for OUT direction uses source as entity and keeps target as ranked value" {
                val topk = topkConfig(name = "top_purchased", table = TopkTable(score = "db.score_tbl"))
                val group =
                    groupWithTopks(
                        name = "g_out",
                        topks = listOf(topk),
                        directionType = DirectionType.OUT,
                    )
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any<Direction>(), any(), any(), any())
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
                edge.source shouldBe "db.src:top_purchased:OUT:user1:__all__"
                edge.target shouldBe "item1"
            }

            "aggregate for IN direction with default rankTarget still ranks target scoped by source" {
                // direction only picks which physical Group row the AGG query scores (here,
                // directedSource=target under IN); it no longer swaps entity/rankedValue — those
                // come from rankTarget alone (default TARGET), same as the OUT case.
                val topk = topkConfig(name = "top_purchased_by", table = TopkTable(score = "db.score_tbl"))
                val group =
                    groupWithTopks(
                        name = "g_in",
                        topks = listOf(topk),
                        directionType = DirectionType.IN,
                    )
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any<Direction>(), any(), any(), any())
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
                edge.source shouldBe "db.src:top_purchased_by:IN:user1:__all__"
                edge.target shouldBe "item1"
            }

            "aggregate for a GLOBAL entity ranks the item itself, scored from its own IN row" {
                // __global__ pairs with IN here because the AGG score must come from the item's
                // own row (directedSource=item under IN); rankedField=_target (default) then ranks
                // that same item. A GLOBAL topk declared OUT would score per-user rows instead,
                // which isn't what "global popularity" means for this table's user→item edges.
                val topk =
                    topkConfig(name = "top_global", table = TopkTable(score = "db.score_tbl"))
                        .copy(entity = AggregationConstants.GLOBAL_ENTITY)
                val group =
                    groupWithTopks(
                        name = "g_global",
                        topks = listOf(topk),
                        directionType = DirectionType.IN,
                    )
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any<Direction>(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 9))

                val mutations = mutableListOf<List<MutationItem>>()
                every {
                    mutationService.mutate(any(), any(), capture(mutations), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                StepVerifier
                    .create(
                        service.aggregate(
                            AggregationType.TOPK,
                            listOf(
                                item("db", "src", source = "user1", target = "item1"),
                                item("db", "src", source = "user2", target = "item2"),
                            ),
                        ),
                    ).assertNext { results ->
                        results shouldHaveSize 2
                    }.verifyComplete()

                val edges = mutations.map { it.single().edge }
                edges.map { it.source } shouldContainExactlyInAnyOrder
                    listOf(
                        "db.src:top_global:IN:${AggregationConstants.GLOBAL_ENTITY}:${AggregationConstants.ALL_SEGMENT}",
                        "db.src:top_global:IN:${AggregationConstants.GLOBAL_ENTITY}:${AggregationConstants.ALL_SEGMENT}",
                    )
                edges.map { it.target } shouldContainExactlyInAnyOrder listOf("item1", "item2")
            }

            "aggregate for rankedField _source ranks the source endpoint and scopes by target" {
                val topk =
                    topkConfig(name = "top_by_target", table = TopkTable(score = "db.score_tbl"))
                        .copy(entity = AggregationConstants.TARGET_FIELD, rankedField = AggregationConstants.SOURCE_FIELD)
                val group =
                    groupWithTopks(
                        name = "g_flip",
                        topks = listOf(topk),
                        directionType = DirectionType.IN,
                    )
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any<Direction>(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 6))

                val mutations = slot<List<MutationItem>>()
                every {
                    mutationService.mutate(any(), any(), capture(mutations), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                StepVerifier
                    .create(service.aggregate(AggregationType.TOPK, listOf(item("db", "src", source = "user1", target = "item1"))))
                    .assertNext { results -> results shouldHaveSize 1 }
                    .verifyComplete()

                val edge = mutations.captured.single().edge
                edge.source shouldBe "db.src:top_by_target:IN:item1:__all__"
                edge.target shouldBe "user1"
            }

            "aggregate ranks a property value when rankedField references one" {
                val topk =
                    topkConfig(name = "top_brand", table = TopkTable(score = "db.score_tbl"))
                        .copy(rankedField = "brandId")
                val group =
                    groupWithTopks(
                        name = "g_brand",
                        topks = listOf(topk),
                        directionType = DirectionType.OUT,
                    )
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any<Direction>(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 7))

                val mutations = slot<List<MutationItem>>()
                every {
                    mutationService.mutate(any(), any(), capture(mutations), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                val baseItem = item("db", "src", source = "user1", target = "item1")
                val item = baseItem.copy(edge = baseItem.edge.copy(properties = mapOf("brandId" to "brand42")))

                StepVerifier
                    .create(service.aggregate(AggregationType.TOPK, listOf(item)))
                    .assertNext { results -> results shouldHaveSize 1 }
                    .verifyComplete()

                val edge = mutations.captured.single().edge
                edge.source shouldBe "db.src:top_brand:OUT:user1:__all__"
                edge.target shouldBe "brand42"
            }

            "aggregate skips the event when the declared rankedField value is missing" {
                val topk =
                    topkConfig(name = "top_brand", table = TopkTable(score = "db.score_tbl"))
                        .copy(rankedField = "brandId")
                val group =
                    groupWithTopks(
                        name = "g_brand",
                        topks = listOf(topk),
                        directionType = DirectionType.OUT,
                    )
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                // SKIPPED (not SUCCESS) proves the short-circuit: the shared mocks would happily
                // serve the agg/mutate path if it were taken.
                StepVerifier
                    .create(service.aggregate(AggregationType.TOPK, listOf(item("db", "src", source = "user1", target = "item1"))))
                    .assertNext { results ->
                        results shouldHaveSize 1
                        results[0].status shouldBe "SKIPPED"
                    }.verifyComplete()
            }

            "aggregate for BOTH direction fans out into one OUT and one IN mutation, both ranking the same target" {
                // rankedField (default _target) fixes entity=user1/rankedValue=item1 regardless of
                // direction, so both fan-out mutations rank the same item1 — only the score key's
                // embedded direction (and whichever physical Group row backs each score) differs.
                val topk = topkConfig(name = "top_both", table = TopkTable(score = "db.score_tbl"))
                val group =
                    groupWithTopks(
                        name = "g_both",
                        topks = listOf(topk),
                        directionType = DirectionType.BOTH,
                    )
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any<Direction>(), any(), any(), any())
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
                        "db.src:top_both:OUT:user1:__all__" to "item1",
                        "db.src:top_both:IN:user1:__all__" to "item1",
                    )
            }

            "aggregate maps thrown errors into ERROR status with the error message" {
                val topk = topkConfig(name = "t1", table = TopkTable(score = "db.score_tbl"))
                val group =
                    groupWithTopks(
                        name = "g1",
                        topks = listOf(topk),
                        directionType = DirectionType.OUT,
                    )
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any<Direction>(), any(), any(), any())
                } returns Mono.error(RuntimeException("agg boom"))

                StepVerifier
                    .create(service.aggregate(AggregationType.TOPK, listOf(item("db", "src"))))
                    .assertNext { results ->
                        results shouldHaveSize 1
                        results[0].status shouldBe "ERROR"
                        results[0].error shouldBe "agg boom"
                    }.verifyComplete()
            }

            // --- aggregate (refresh row bookkeeping) ---

            "aggregate does not write a refresh entry when the topk has no refresh configured" {
                val topk = topkConfig(name = "t1", table = TopkTable(score = "db.score_tbl"))
                val group = groupWithTopks(name = "g1", topks = listOf(topk), directionType = DirectionType.OUT)
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any<Direction>(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 1))
                every {
                    mutationService.mutate("db", "score_tbl", any(), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                StepVerifier
                    .create(service.aggregate(AggregationType.TOPK, listOf(item("db", "src"))))
                    .assertNext { results -> results shouldHaveSize 1 }
                    .verifyComplete()

                verify(exactly = 0) { mutationService.mutate("topk", "refresh", any(), any(), any(), any(), any()) }
            }

            "aggregate writes one event-scoped refresh entry keyed by refreshAt when the topk configures refresh" {
                val topk =
                    topkConfig(name = "top_purchased", table = TopkTable(score = "db.score_tbl"))
                        .copy(refreshAfterMillis = 60_000L)
                val group = groupWithTopks(name = "g1", topks = listOf(topk), directionType = DirectionType.OUT)
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any<Direction>(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 1))
                every {
                    mutationService.mutate("db", "score_tbl", any(), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                val refreshMutations = slot<List<MutationItem>>()
                every {
                    mutationService.mutate("topk", "refresh", capture(refreshMutations), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                val eventItem = item("db", "src", source = "user1", target = "item1", version = 1_000L)

                StepVerifier
                    .create(service.aggregate(AggregationType.TOPK, listOf(eventItem)))
                    .assertNext { results -> results shouldHaveSize 1 }
                    .verifyComplete()

                val refreshEdge = refreshMutations.captured.single().edge
                refreshEdge.target shouldBe "db.src:top_purchased:OUT:user1:__all__:item1:61000"
                refreshEdge.properties["refreshAt"] shouldBe 61_000L
            }

            "aggregate stores refreshAt as epoch millis even when edge versions are nanos" {
                val topk =
                    topkConfig(name = "top_purchased", table = TopkTable(score = "db.score_tbl"))
                        .copy(refreshAfterMillis = 60_000L)
                val group =
                    groupWithTopks(
                        name = "g1",
                        topks = listOf(topk),
                        directionType = DirectionType.OUT,
                        fields =
                            listOf(
                                Group.Field(
                                    name = "version",
                                    bucket =
                                        Bucket.Date(
                                            name = "time",
                                            unit = Bucket.ValueUnit.NANOSECOND,
                                            timezone = "+00:00",
                                            format = "yyyyMMdd'T'HH",
                                        ),
                                ),
                            ),
                    )
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any<Direction>(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 1))
                every {
                    mutationService.mutate("db", "score_tbl", any(), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                val refreshMutations = slot<List<MutationItem>>()
                every {
                    mutationService.mutate("topk", "refresh", capture(refreshMutations), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                val eventItem = item("db", "src", source = "user1", target = "item1", version = 1_000_000_000L)

                StepVerifier
                    .create(service.aggregate(AggregationType.TOPK, listOf(eventItem)))
                    .assertNext { results -> results shouldHaveSize 1 }
                    .verifyComplete()

                val refreshEdge = refreshMutations.captured.single().edge
                refreshEdge.target shouldBe "db.src:top_purchased:OUT:user1:__all__:item1:61000"
                refreshEdge.properties["refreshAt"] shouldBe 61_000L
            }

            "aggregate writing two events for the same entity produces two independent refresh entries" {
                val topk =
                    topkConfig(name = "top_purchased", table = TopkTable(score = "db.score_tbl"))
                        .copy(refreshAfterMillis = 60_000L)
                val group = groupWithTopks(name = "g1", topks = listOf(topk), directionType = DirectionType.OUT)
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any<Direction>(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 1))
                every {
                    mutationService.mutate("db", "score_tbl", any(), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                val refreshMutations = mutableListOf<List<MutationItem>>()
                every {
                    mutationService.mutate("topk", "refresh", capture(refreshMutations), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                val firstEvent = item("db", "src", source = "user1", target = "item1", version = 1_000L)
                val secondEvent = item("db", "src", source = "user1", target = "item2", version = 2_000L)

                StepVerifier
                    .create(service.aggregate(AggregationType.TOPK, listOf(firstEvent, secondEvent)))
                    .assertNext { results -> results shouldHaveSize 2 }
                    .verifyComplete()

                val targets = refreshMutations.map { it.single().edge.target }
                targets shouldContainExactlyInAnyOrder
                    listOf(
                        "db.src:top_purchased:OUT:user1:__all__:item1:61000",
                        "db.src:top_purchased:OUT:user1:__all__:item2:62000",
                    )
            }

            // --- refresh ---

            fun refreshEntry(
                topk: String = "top_purchased",
                target: String,
                refreshAt: Long = 61_000L,
            ): RefreshEntryPayload =
                RefreshEntryPayload(
                    database = "db",
                    table = "src",
                    topk = topk,
                    direction = Direction.OUT,
                    entity = "user1",
                    segment = null,
                    target = target,
                    refreshAt = refreshAt,
                )

            "getRefreshEntries scans exactly the requested partition and returns parsed entries" {
                val scannedPartitions = mutableListOf<Long>()
                val refreshRow =
                    EdgePayload(
                        version = 1L,
                        source = 42L,
                        target = "db.src:top_purchased:OUT:user1:__all__:item1:61000",
                        properties = mapOf("refreshAt" to 61_000L),
                        context = emptyMap(),
                    )

                every {
                    queryService.scan(
                        database = "topk",
                        table = "refresh",
                        index = "refresh_at_asc",
                        start = any<Long>(),
                        direction = Direction.OUT,
                        limit = 100,
                        offset = null,
                        ranges = "refreshAt:lte:61000",
                        filters = null,
                        features = emptyList(),
                    )
                } answers {
                    val partition = arg<Long>(3)
                    scannedPartitions += partition
                    Mono.just(
                        DataFrameEdgePayload(
                            edges = if (partition == 42L) listOf(refreshRow) else emptyList(),
                            count = if (partition == 42L) 1 else 0,
                            total = if (partition == 42L) 1 else 0,
                            offset = null,
                            hasNext = false,
                            context = emptyMap(),
                        ),
                    )
                }

                StepVerifier
                    .create(
                        service.getRefreshEntries(
                            partition = 42L,
                            now = 61_000L,
                            limit = 100,
                        ),
                    ).assertNext { entries ->
                        entries shouldBe listOf(refreshEntry(target = "item1"))
                    }.verifyComplete()

                scannedPartitions shouldBe listOf(42L)
            }

            "getRefreshEntries rejects a partition outside the fixed partition range" {
                shouldThrow<IllegalArgumentException> {
                    service.getRefreshEntries(
                        partition = AggregationConstants.TOPK_REFRESH_PARTITIONS.toLong(),
                        now = 0L,
                        limit = 100,
                    )
                }
            }

            "refresh re-aggregates one entry from its key alone and deletes exactly that entry" {
                val topk =
                    topkConfig(name = "top_purchased", table = TopkTable(score = "db.score_tbl"))
                        .copy(refreshAfterMillis = 60_000L)
                val group = groupWithTopks(name = "g1", topks = listOf(topk), directionType = DirectionType.OUT)
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                val entry = refreshEntry(target = "item1")

                every {
                    queryService.agg(any(), any(), any(), any(), any<Direction>(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 5))
                val scoreMutations = slot<List<MutationItem>>()
                every {
                    mutationService.mutate("db", "score_tbl", capture(scoreMutations), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                val deleteMutations = mutableListOf<List<MutationItem>>()
                every {
                    mutationService.mutate("topk", "refresh", capture(deleteMutations), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "DELETED")))

                StepVerifier
                    .create(service.refresh(entries = listOf(entry)))
                    .assertNext { results ->
                        results shouldHaveSize 1
                        results[0].status shouldBe "SUCCESS"
                    }.verifyComplete()

                val scoreEdge = scoreMutations.captured.single().edge
                scoreEdge.source shouldBe "db.src:top_purchased:OUT:user1:__all__"
                scoreEdge.target shouldBe "item1"

                val delete = deleteMutations.single().single()
                delete.type shouldBe EventType.DELETE
                delete.edge.source shouldBe
                    AggregationConstants.refreshSource(
                        database = "db",
                        table = "src",
                        topk = "top_purchased",
                        direction = Direction.OUT,
                        entity = "user1",
                        segment = null,
                        target = "item1",
                    )
                delete.edge.target shouldBe "db.src:top_purchased:OUT:user1:__all__:item1:61000"

                deleteMutations shouldHaveSize 1
            }

            "refresh processes a batch of entries independently and deletes all of them in one bulk mutation" {
                val topk =
                    topkConfig(name = "top_purchased", table = TopkTable(score = "db.score_tbl"))
                        .copy(refreshAfterMillis = 60_000L)
                val group = groupWithTopks(name = "g1", topks = listOf(topk), directionType = DirectionType.OUT)
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                val entries = listOf(refreshEntry(target = "item1"), refreshEntry(target = "item2"))

                every {
                    queryService.agg(any(), any(), any(), any(), any<Direction>(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 5))
                every {
                    mutationService.mutate("db", "score_tbl", any(), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                val deleteMutations = mutableListOf<List<MutationItem>>()
                every {
                    mutationService.mutate("topk", "refresh", capture(deleteMutations), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "DELETED"), mutationResult(status = "DELETED")))

                StepVerifier
                    .create(service.refresh(entries = entries))
                    .assertNext { results -> results shouldHaveSize 2 }
                    .verifyComplete()

                deleteMutations shouldHaveSize 1
                deleteMutations.single() shouldHaveSize 2
            }

            "refresh excludes an unresolved entry from delete" {
                val topk =
                    topkConfig(name = "top_purchased", table = TopkTable(score = "db.score_tbl"))
                        .copy(refreshAfterMillis = 60_000L)
                val group = groupWithTopks(name = "g1", topks = listOf(topk), directionType = DirectionType.OUT)
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                val unresolvedEntry = refreshEntry(topk = "missing_topk", target = "item1")
                val validEntry = refreshEntry(target = "item2")

                every {
                    queryService.agg(any(), any(), any(), any(), any<Direction>(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 5))
                every {
                    mutationService.mutate("db", "score_tbl", any(), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                val deleteMutations = mutableListOf<List<MutationItem>>()
                every {
                    mutationService.mutate("topk", "refresh", capture(deleteMutations), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "DELETED")))

                StepVerifier
                    .create(
                        service.refresh(
                            entries = listOf(unresolvedEntry, validEntry),
                        ),
                    ).assertNext { results -> results shouldHaveSize 1 }
                    .verifyComplete()

                val delete = deleteMutations.single().single()
                delete.edge.target shouldBe "db.src:top_purchased:OUT:user1:__all__:item2:61000"
            }
        },
    )

// region test fixtures

private fun topkConfig(
    name: String,
    table: TopkTable = TopkTable(score = "${name}__score"),
): Topk = Topk(topk = name, table = table)

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
    version: Long = 1L,
): AggregationItemPayload =
    AggregationItemPayload(
        database = database,
        table = table,
        edge =
            EdgePayload(
                version = version,
                source = source,
                target = target,
                properties = emptyMap(),
                context = emptyMap(),
            ),
    )

private fun aggPayload(count: Int): DataFrameEdgeAggPayload =
    DataFrameEdgeAggPayload(
        groups = listOf(EdgeAggPayload(start = "unused", direction = Direction.OUT, value = count.toLong(), context = emptyMap())),
        count = 1,
        context = emptyMap(),
    )

private fun mutationResult(status: String): MutationResult = MutationResult.of(key = MutationKey.SourceTarget("s", "t"), count = 1, status = status)

// endregion
