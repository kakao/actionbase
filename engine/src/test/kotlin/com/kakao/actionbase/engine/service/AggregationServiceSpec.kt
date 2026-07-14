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
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Field
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.GroupType
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.metadata.common.Topk
import com.kakao.actionbase.core.metadata.common.TopkTable
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
                val topk = topkConfig(name = "t1", table = TopkTable(score = "db.score_tbl", expire = "db.exp_tbl"))
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
                val topk = topkConfig(name = "t1", table = TopkTable(score = "db.score_tbl", expire = "db.exp_tbl"))
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
                edge.source shouldBe "user1|top_purchased"
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
                edge.source shouldBe "item1|top_purchased_by"
                edge.target shouldBe "user1"
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
                        "user1|top_both" to "item1",
                        "item1|top_both" to "user1",
                    )
            }

            // --- aggregate (expire CDC) ---

            "aggregate resolves original table from properties[table] when the item comes from the expire CDC" {
                val topk = topkConfig(name = "top_purchased", table = TopkTable(score = "commerce.score_tbl", expire = "commerce.exp_tbl"))
                val group =
                    groupWithTopks(
                        name = "g_out",
                        topks = listOf(topk),
                        directionType = DirectionType.BOTH,
                    )
                stubBindingWith(engine, database = "commerce", table = "purchases", groups = listOf(group))

                val queryRanges = slot<String>()
                every {
                    queryService.agg(any(), any(), any(), any(), any(), capture(queryRanges), any(), any())
                } returns Mono.just(aggPayload(count = 4))

                val mutations = slot<List<MutationItem>>()
                every {
                    mutationService.mutate(any(), any(), capture(mutations), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                val expireItem =
                    expireItem(
                        properties =
                            mapOf(
                                "table" to "commerce.purchases",
                                "source" to "user1",
                                "target" to "item1",
                                "direction" to "OUT",
                                "ranges" to "_target:eq:item1",
                            ),
                    )

                StepVerifier
                    .create(service.aggregate(AggregationType.TOPK, listOf(expireItem)))
                    .assertNext { results ->
                        results shouldHaveSize 1
                        results[0].status shouldBe "SUCCESS"
                        results[0].database shouldBe "commerce"
                        results[0].table shouldBe "purchases"
                    }.verifyComplete()

                queryRanges.captured shouldBe "_target:eq:item1"
                val edge = mutations.captured.single().edge
                edge.source shouldBe "user1|top_purchased"
                edge.target shouldBe "item1"
            }

            "aggregate rejects an expire CDC item missing the `table` property" {
                val expireItem = expireItem(properties = mapOf("source" to "u", "target" to "t", "direction" to "OUT"))

                StepVerifier
                    .create(service.aggregate(AggregationType.TOPK, listOf(expireItem)))
                    .expectErrorMatches { it is IllegalArgumentException && it.message!!.contains("table property is required") }
                    .verify()
            }

            "aggregate rejects an expire CDC item missing the `source` property" {
                val topk = topkConfig(name = "top_purchased", table = TopkTable(score = "commerce.score_tbl", expire = "commerce.exp_tbl"))
                val group = groupWithTopks(name = "g", topks = listOf(topk), directionType = DirectionType.OUT)
                stubBindingWith(engine, database = "commerce", table = "purchases", groups = listOf(group))

                val expireItem =
                    expireItem(
                        properties =
                            mapOf(
                                "table" to "commerce.purchases",
                                "target" to "item1",
                                "direction" to "OUT",
                                "ranges" to "_target:eq:item1",
                            ),
                    )

                StepVerifier
                    .create(service.aggregate(AggregationType.TOPK, listOf(expireItem)))
                    .expectErrorMatches { it is IllegalStateException && it.message!!.contains("`source` property is required") }
                    .verify()
            }

            // --- aggregate (expire write) ---

            "aggregate writes an expire row after the score row when expireAfterMillis is positive" {
                val expireAfter = 60_000L
                val topk =
                    Topk(
                        topk = "top_purchased",
                        expireAfterMillis = expireAfter,
                        table = TopkTable(score = "commerce.score_tbl", expire = "commerce.exp_tbl"),
                    )
                val group = groupWithTopks(name = "g_out", topks = listOf(topk), directionType = DirectionType.OUT)
                stubBindingWith(engine, database = "commerce", table = "purchases", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 4))

                val scoreMutations = slot<List<MutationItem>>()
                every {
                    mutationService.mutate("commerce", "score_tbl", capture(scoreMutations), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                val expireMutations = slot<List<MutationItem>>()
                every {
                    mutationService.mutate(
                        AggregationConstants.TOPK_DATABASE,
                        AggregationConstants.TOPK_EXPIRE_TABLE,
                        capture(expireMutations),
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

                scoreMutations.captured.single().edge.source shouldBe "user1|top_purchased"
                scoreMutations.captured.single().edge.target shouldBe "item1"

                val expireEdge = expireMutations.captured.single().edge
                expireEdge.source shouldBe
                    AggregationConstants.expireSource(
                        table = "commerce.purchases",
                        topk = "top_purchased",
                        entity = "user1",
                        target = "item1",
                    )
                val expiresAt = expireEdge.properties["expiresAt"] as Long
                expiresAt shouldBeGreaterThanOrEqual (before + expireAfter)
                expiresAt shouldBeLessThanOrEqual (after + expireAfter)
                expireEdge.target shouldBe
                    AggregationConstants.expireTarget(
                        table = "commerce.purchases",
                        topk = "top_purchased",
                        entity = "user1",
                        target = "item1",
                        expiresAt = expiresAt,
                    )
                expireEdge.properties["table"] shouldBe "commerce.purchases"
                expireEdge.properties["topk"] shouldBe "top_purchased"
                expireEdge.properties["start"] shouldBe "user1"
                expireEdge.properties["direction"] shouldBe "OUT"
                expireEdge.properties["processed"] shouldBe false
            }

            "aggregate skips the expire write when expireAfterMillis is not positive" {
                clearMocks(mutationService)
                val topk = topkConfig(name = "t1", table = TopkTable(score = "db.score_tbl", expire = "db.exp_tbl"))
                val group = groupWithTopks(name = "g1", topks = listOf(topk), directionType = DirectionType.OUT)
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 1))

                every {
                    mutationService.mutate("db", "score_tbl", any(), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                StepVerifier
                    .create(service.aggregate(AggregationType.TOPK, listOf(item("db", "src"))))
                    .assertNext { it.single().status shouldBe "SUCCESS" }
                    .verifyComplete()

                verify(exactly = 0) {
                    mutationService.mutate(
                        AggregationConstants.TOPK_DATABASE,
                        AggregationConstants.TOPK_EXPIRE_TABLE,
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                }
            }

            "aggregate skips the expire write when the score mutate reports ERROR" {
                clearMocks(mutationService)
                val topk =
                    Topk(
                        topk = "t1",
                        expireAfterMillis = 60_000L,
                        table = TopkTable(score = "db.score_tbl", expire = "db.exp_tbl"),
                    )
                val group = groupWithTopks(name = "g1", topks = listOf(topk), directionType = DirectionType.OUT)
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 1))

                every {
                    mutationService.mutate("db", "score_tbl", any(), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "ERROR")))

                StepVerifier
                    .create(service.aggregate(AggregationType.TOPK, listOf(item("db", "src"))))
                    .assertNext { it.single().status shouldBe "ERROR" }
                    .verifyComplete()

                verify(exactly = 0) {
                    mutationService.mutate(
                        AggregationConstants.TOPK_DATABASE,
                        AggregationConstants.TOPK_EXPIRE_TABLE,
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                }
            }

            "aggregate reports ERROR when the expire mutate reports ERROR" {
                val topk =
                    Topk(
                        topk = "t1",
                        expireAfterMillis = 60_000L,
                        table = TopkTable(score = "db.score_tbl", expire = "db.exp_tbl"),
                    )
                val group = groupWithTopks(name = "g1", topks = listOf(topk), directionType = DirectionType.OUT)
                stubBindingWith(engine, database = "db", table = "src", groups = listOf(group))

                every {
                    queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
                } returns Mono.just(aggPayload(count = 1))

                every {
                    mutationService.mutate("db", "score_tbl", any(), any(), any(), any(), any())
                } returns Mono.just(listOf(mutationResult(status = "CREATED")))

                every {
                    mutationService.mutate(
                        AggregationConstants.TOPK_DATABASE,
                        AggregationConstants.TOPK_EXPIRE_TABLE,
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
                val topk = topkConfig(name = "t1", table = TopkTable(score = "db.score_tbl", expire = "db.exp_tbl"))
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
): AggregationItemPayload =
    AggregationItemPayload(
        database = database,
        table = table,
        edge =
            EdgePayload(
                version = 1L,
                source = source,
                target = target,
                properties = emptyMap(),
                context = emptyMap(),
            ),
    )

private fun expireItem(properties: Map<String, Any?>): AggregationItemPayload =
    AggregationItemPayload(
        database = AggregationConstants.TOPK_DATABASE,
        table = AggregationConstants.TOPK_EXPIRE_TABLE,
        edge =
            EdgePayload(
                version = 1L,
                source = "unused",
                target = "unused",
                properties = properties,
                context = emptyMap(),
            ),
    )

private fun aggPayload(count: Int): DataFrameEdgeAggPayload = DataFrameEdgeAggPayload(groups = emptyList(), count = count, context = emptyMap())

private fun mutationResult(status: String): MutationResult = MutationResult.of(key = MutationKey.SourceTarget("s", "t"), count = 1, status = status)

// endregion
