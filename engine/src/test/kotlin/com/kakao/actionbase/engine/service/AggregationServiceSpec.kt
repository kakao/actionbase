package com.kakao.actionbase.engine.service

import com.kakao.actionbase.core.edge.MutationKey
import com.kakao.actionbase.core.edge.payload.AggregationItemPayload
import com.kakao.actionbase.core.edge.payload.DataFrameEdgeAggPayload
import com.kakao.actionbase.core.edge.payload.DataFrameEdgePayload
import com.kakao.actionbase.core.edge.payload.EdgeAggPayload
import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest.MutationItem
import com.kakao.actionbase.core.edge.payload.EdgePayload
import com.kakao.actionbase.core.edge.payload.MutationResult
import com.kakao.actionbase.core.metadata.common.Aggregations
import com.kakao.actionbase.core.metadata.common.Direction
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Field
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.GroupType
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.metadata.common.TopKTableNames
import com.kakao.actionbase.core.metadata.common.Topk
import com.kakao.actionbase.core.metadata.common.TopkScope
import com.kakao.actionbase.core.metadata.common.TopkTable
import com.kakao.actionbase.core.metadata.payload.AggregationType
import com.kakao.actionbase.core.metadata.payload.ExpireTableRef
import com.kakao.actionbase.core.state.EventType
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.engine.AggregationEngine
import com.kakao.actionbase.engine.QualifiedGroups
import com.kakao.actionbase.engine.binding.TableBinding
import com.kakao.actionbase.v2.engine.util.objectMapper

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

            "getAggregations returns only tables that define topk" {
                every { engine.getAllQualifiedGroups() } returns
                    listOf(
                        edgeSummary(database = "db", table = "with_topk", topks = listOf(topkConfig("t1"))),
                        edgeSummary(database = "db", table = "no_topk", topks = emptyList()),
                        vertexSummary(database = "db", table = "vertex"),
                    )

                val result = service.getAggregations()

                val topks = result.flatMap { md -> md.aggregations.flatMap { it.topk } }
                topks shouldContainExactlyInAnyOrder listOf(topkConfig("t1"))
            }

            "getAggregations returns empty topk when no table defines any aggregation" {
                every { engine.getAllQualifiedGroups() } returns
                    listOf(
                        edgeSummary(database = "db", table = "no_topk", topks = emptyList()),
                        vertexSummary(database = "db", table = "vertex"),
                    )

                val result = service.getAggregations()

                result.flatMap { md -> md.aggregations.flatMap { it.topk } }.shouldBeEmpty()
            }

            // --- getExpireTables ---

            "getExpireTables returns distinct expire tables across all topk declarations" {
                every { engine.getAllQualifiedGroups() } returns
                    listOf(
                        edgeSummary(
                            database = "db",
                            table = "t1",
                            topks =
                                listOf(
                                    topkConfig("a", table = TopkTable(score = "db.a__score", expire = "topk.expire")),
                                    topkConfig("b", table = TopkTable(score = "db.b__score", expire = "topk.expire")),
                                ),
                        ),
                        edgeSummary(
                            database = "db",
                            table = "t2",
                            topks = listOf(topkConfig("c", table = TopkTable(score = "db.c__score", expire = ""))),
                        ),
                        vertexSummary(database = "db", table = "vertex"),
                    )

                service.getExpireTables() shouldBe listOf(ExpireTableRef(database = "topk", table = "expire"))
            }

            // --- aggregate ---

            "aggregate returns SUCCESS when mutate succeeds" {
                val topk = topkConfig(name = "t1", table = TopkTable(score = "db.score_tbl", expire = "db.exp_tbl"))
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
                val topk = topkConfig(name = "t1", table = TopkTable(score = "db.score_tbl", expire = "db.exp_tbl"))
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

            "aggregate stores score as a Double and segment as the URL-encoded resolved ranges" {
                val topk =
                    topkConfig(name = "top_seg", table = TopkTable(score = "db.score_tbl", expire = "db.exp_tbl"))
                        .copy(ranges = "gender:eq:{gender}")
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
                edge.properties["score"] shouldBe 3.0
                edge.properties["segment"] shouldBe "gender%3Aeq%3AF"
            }

            "aggregate for OUT direction uses source as entity and keeps target as ranked value" {
                val topk = topkConfig(name = "top_purchased", table = TopkTable(score = "db.score_tbl", expire = "db.exp_tbl"))
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
                edge.source shouldBe "db.src:top_purchased:OUT:user1"
                edge.target shouldBe "item1"
            }

            "aggregate for IN direction swaps source and target so the ranking is per target entity" {
                val topk = topkConfig(name = "top_purchased_by", table = TopkTable(score = "db.score_tbl", expire = "db.exp_tbl"))
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
                edge.source shouldBe "db.src:top_purchased_by:IN:item1"
                edge.target shouldBe "user1"
            }

            "aggregate for GLOBAL scope uses a fixed entity so different sources share the same score row" {
                val topk =
                    topkConfig(name = "top_global", table = TopkTable(score = "db.score_tbl", expire = "db.exp_tbl"))
                        .copy(scope = TopkScope.GLOBAL)
                val group =
                    groupWithTopks(
                        name = "g_global",
                        topks = listOf(topk),
                        directionType = DirectionType.OUT,
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
                        "db.src:top_global:OUT:${TopKTableNames.GLOBAL_ENTITY}",
                        "db.src:top_global:OUT:${TopKTableNames.GLOBAL_ENTITY}",
                    )
                edges.map { it.target } shouldContainExactlyInAnyOrder listOf("item1", "item2")
            }

            "aggregate for BOTH direction fans out into one OUT and one IN mutation" {
                val topk = topkConfig(name = "top_both", table = TopkTable(score = "db.score_tbl", expire = "db.exp_tbl"))
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
                        "db.src:top_both:OUT:user1" to "item1",
                        "db.src:top_both:IN:item1" to "user1",
                    )
            }

            "aggregate maps thrown errors into ERROR status with the error message" {
                val topk = topkConfig(name = "t1", table = TopkTable(score = "db.score_tbl", expire = "db.exp_tbl"))
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

            // --- aggregate (expire row bookkeeping) ---

            "aggregate does not write an expire entry when the topk has no expiry configured" {
                val topk = topkConfig(name = "t1", table = TopkTable(score = "db.score_tbl", expire = "db.exp_tbl"))
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

                verify(exactly = 0) { mutationService.mutate("db", "exp_tbl", any(), any(), any(), any(), any()) }
            }

            "aggregate writes one event-scoped expire entry keyed by expiredAt when the topk configures expiry" {
                val topk =
                    topkConfig(name = "top_purchased", table = TopkTable(score = "db.score_tbl", expire = "db.exp_tbl"))
                        .copy(expireAfterMillis = 60_000L)
                val group = groupWithTopks(name = "g1", topks = listOf(topk), directionType = DirectionType.OUT)
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any<Direction>(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 1))
                every {
                    mutationService.mutate("db", "score_tbl", any(), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                val expireMutations = slot<List<MutationItem>>()
                every {
                    mutationService.mutate("db", "exp_tbl", capture(expireMutations), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                val eventItem = item("db", "src", source = "user1", target = "item1", version = 1_000L)

                StepVerifier
                    .create(service.aggregate(AggregationType.TOPK, listOf(eventItem)))
                    .assertNext { results -> results shouldHaveSize 1 }
                    .verifyComplete()

                val expireEdge = expireMutations.captured.single().edge
                expireEdge.target shouldBe "db.src|top_purchased|OUT|user1|item1|61000"
                expireEdge.properties["expiredAt"] shouldBe 61_000L
            }

            "aggregate writing two events for the same entity produces two independent expire entries" {
                val topk =
                    topkConfig(name = "top_purchased", table = TopkTable(score = "db.score_tbl", expire = "db.exp_tbl"))
                        .copy(expireAfterMillis = 60_000L)
                val group = groupWithTopks(name = "g1", topks = listOf(topk), directionType = DirectionType.OUT)
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any<Direction>(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 1))
                every {
                    mutationService.mutate("db", "score_tbl", any(), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                val expireMutations = mutableListOf<List<MutationItem>>()
                every {
                    mutationService.mutate("db", "exp_tbl", capture(expireMutations), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                val firstEvent = item("db", "src", source = "user1", target = "item1", version = 1_000L)
                val secondEvent = item("db", "src", source = "user1", target = "item2", version = 2_000L)

                StepVerifier
                    .create(service.aggregate(AggregationType.TOPK, listOf(firstEvent, secondEvent)))
                    .assertNext { results -> results shouldHaveSize 2 }
                    .verifyComplete()

                val targets = expireMutations.map { it.single().edge.target }
                targets shouldContainExactlyInAnyOrder
                    listOf(
                        "db.src|top_purchased|OUT|user1|item1|61000",
                        "db.src|top_purchased|OUT|user1|item2|62000",
                    )
            }

            // --- sweep ---

            "sweep re-aggregates an expired row from its stored payload, deletes it, and skips it if scanned again" {
                val topk =
                    topkConfig(name = "top_purchased", table = TopkTable(score = "db.score_tbl", expire = "topk.expire"))
                        .copy(expireAfterMillis = 60_000L)
                val group = groupWithTopks(name = "g1", topks = listOf(topk), directionType = DirectionType.OUT)
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                val storedEdge = item("db", "src", source = "user1", target = "item1", version = 1_000L).edge
                val expirePayloadJson =
                    objectMapper.writeValueAsString(
                        mapOf(
                            "database" to "db",
                            "table" to "src",
                            "group" to "g1",
                            "topk" to "top_purchased",
                            "direction" to "OUT",
                            "edge" to storedEdge,
                        ),
                    )
                val expiredRow =
                    EdgePayload(
                        version = 1L,
                        source = 42L,
                        target = "db.src|top_purchased|OUT|user1|item1|61000",
                        properties = mapOf("expiredAt" to 61_000L, "payload" to expirePayloadJson),
                        context = emptyMap(),
                    )

                every {
                    queryService.scan("topk", "expire", "expired_at_asc", 42L, any<Direction>(), 10, null, "expiredAt:lte:70000", null, emptyList())
                } returns
                    Mono.just(
                        DataFrameEdgePayload(edges = listOf(expiredRow), count = 1, total = 1, offset = null, hasNext = false, context = emptyMap()),
                    )

                every {
                    queryService.agg(any(), any(), any(), any(), any<Direction>(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 5))
                every {
                    mutationService.mutate("db", "score_tbl", any(), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                val deleteMutations = slot<List<MutationItem>>()
                every {
                    mutationService.mutate("topk", "expire", capture(deleteMutations), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "DELETED")))

                StepVerifier
                    .create(service.sweep(expireDatabase = "topk", expireTable = "expire", partition = 42L, now = 70_000L))
                    .assertNext { results ->
                        results shouldHaveSize 1
                        results[0].status shouldBe "SUCCESS"
                    }.verifyComplete()

                val delete = deleteMutations.captured.single()
                delete.type shouldBe EventType.DELETE
                delete.edge.source shouldBe 42L
                delete.edge.target shouldBe "db.src|top_purchased|OUT|user1|item1|61000"

                // sweep never re-tracks expiry for the item it just refreshed.
                verify(exactly = 1) { mutationService.mutate("topk", "expire", any(), any(), any(), any(), any()) }
            }

            "sweep pages through the scan when a partition holds more rows than one page returns" {
                val topk =
                    topkConfig(name = "top_purchased", table = TopkTable(score = "db.score_tbl", expire = "topk.expire"))
                        .copy(expireAfterMillis = 60_000L)
                val group = groupWithTopks(name = "g1", topks = listOf(topk), directionType = DirectionType.OUT)
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                fun expiredRowFor(target: String): EdgePayload {
                    val storedEdge = item("db", "src", source = "user1", target = target, version = 1_000L).edge
                    val payloadJson =
                        objectMapper.writeValueAsString(
                            mapOf(
                                "database" to "db",
                                "table" to "src",
                                "group" to "g1",
                                "topk" to "top_purchased",
                                "direction" to "OUT",
                                "edge" to storedEdge,
                            ),
                        )
                    return EdgePayload(
                        version = 1L,
                        source = 42L,
                        target = "db.src|top_purchased|OUT|user1|$target|61000",
                        properties = mapOf("expiredAt" to 61_000L, "payload" to payloadJson),
                        context = emptyMap(),
                    )
                }

                every {
                    queryService.scan("topk", "expire", "expired_at_asc", 42L, any<Direction>(), 10, null, "expiredAt:lte:70000", null, emptyList())
                } returns
                    Mono.just(
                        DataFrameEdgePayload(
                            edges = listOf(expiredRowFor("item1")),
                            count = 1,
                            total = 2,
                            offset = "cursor-1",
                            hasNext = true,
                            context = emptyMap(),
                        ),
                    )
                every {
                    queryService.scan(
                        "topk",
                        "expire",
                        "expired_at_asc",
                        42L,
                        any<Direction>(),
                        10,
                        "cursor-1",
                        "expiredAt:lte:70000",
                        null,
                        emptyList(),
                    )
                } returns
                    Mono.just(
                        DataFrameEdgePayload(
                            edges = listOf(expiredRowFor("item2")),
                            count = 1,
                            total = 2,
                            offset = null,
                            hasNext = false,
                            context = emptyMap(),
                        ),
                    )

                every {
                    queryService.agg(any(), any(), any(), any(), any<Direction>(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 5))
                every {
                    mutationService.mutate("db", "score_tbl", any(), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))
                every {
                    mutationService.mutate("topk", "expire", any(), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "DELETED")))

                StepVerifier
                    .create(service.sweep(expireDatabase = "topk", expireTable = "expire", partition = 42L, now = 70_000L))
                    .assertNext { results -> results shouldHaveSize 2 }
                    .verifyComplete()
            }
        },
    )

// region test fixtures

private fun topkConfig(
    name: String,
    table: TopkTable = TopkTable(score = "${name}__score", expire = "${name}__expire"),
): Topk = Topk(topk = name, table = table)

private fun groupWithTopks(
    name: String,
    topks: List<Topk>,
    directionType: DirectionType = DirectionType.BOTH,
): Group =
    Group(
        group = name,
        type = GroupType.SUM,
        fields = emptyList(),
        directionType = directionType,
        aggregations = Aggregations(topk = topks),
    )

private fun stringField(): Field = Field(type = PrimitiveType.STRING, comment = "")

private fun edgeSummary(
    database: String,
    table: String,
    topks: List<Topk>,
): QualifiedGroups =
    QualifiedGroups(
        database = database,
        table = table,
        groups = listOf(groupWithTopks("g", topks)),
    )

private fun vertexSummary(
    database: String,
    table: String,
): QualifiedGroups =
    QualifiedGroups(
        database = database,
        table = table,
        groups = emptyList(),
    )

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
