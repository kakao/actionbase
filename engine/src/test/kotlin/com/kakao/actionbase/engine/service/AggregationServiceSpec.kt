package com.kakao.actionbase.engine.service

import com.kakao.actionbase.core.edge.MutationKey
import com.kakao.actionbase.core.edge.payload.AggregationItemPayload
import com.kakao.actionbase.core.edge.payload.DataFrameEdgeAggPayload
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
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.engine.AggregationEngine
import com.kakao.actionbase.engine.QualifiedGroups
import com.kakao.actionbase.engine.binding.TableBinding

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
                    queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
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
                    queryService.agg(any(), any(), any(), any(), any(), any(), any(), any())
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
                        "db.src:top_both:OUT:user1" to "item1",
                        "db.src:top_both:IN:item1" to "user1",
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
                edge.source shouldBe "commerce.purchases:top_purchased:OUT:user1"
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
        database = TopKTableNames.EXPIRE_TABLE_DATABASE,
        table = TopKTableNames.EXPIRE_TABLE_NAME,
        edge =
            EdgePayload(
                version = 1L,
                source = "unused",
                target = "unused",
                properties = properties,
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
