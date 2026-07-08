package com.kakao.actionbase.engine.service

import com.kakao.actionbase.core.edge.MutationKey
import com.kakao.actionbase.core.edge.payload.AggregationItemPayload
import com.kakao.actionbase.core.edge.payload.DataFrameEdgeAggPayload
import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest.MutationItem
import com.kakao.actionbase.core.edge.payload.EdgePayload
import com.kakao.actionbase.core.edge.payload.MutationResult
import com.kakao.actionbase.core.metadata.common.Aggregations
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Field
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.GroupType
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.metadata.common.Topk
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

private fun aggPayload(count: Int): DataFrameEdgeAggPayload = DataFrameEdgeAggPayload(groups = emptyList(), count = count, context = emptyMap())

private fun mutationResult(status: String): MutationResult = MutationResult.of(key = MutationKey.SourceTarget("s", "t"), count = 1, status = status)

// endregion
