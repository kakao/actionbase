package com.kakao.actionbase.engine.service

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
import com.kakao.actionbase.v2.engine.v3.V3TableDescriptor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.mockk.every
import io.mockk.mockk

class AggregationServiceSpec :
    StringSpec(
        {
            val engine = mockk<AggregationEngine>()
            val service = AggregationService(engine)

            "getAggregations returns only tables that define topk" {
                every { engine.getAllTables() } returns
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
                every { engine.getAllTables() } returns
                    listOf(
                        edgeSummary(database = "db", table = "no_topk", topks = emptyList()),
                        vertexSummary(database = "db", table = "vertex"),
                    )

                val result = service.getAggregations()

                result.flatMap { md -> md.aggregations.flatMap { it.topk } }.shouldBeEmpty()
            }
        },
    )

// region test fixtures

private fun topkConfig(
    name: String,
    table: TopkTable? = TopkTable(score = "${name}__score", expire = "${name}__expire"),
): Topk = Topk(topk = name, ranges = null, expire = false, expireAfterMillis = null, table = table)

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
        aggregations = if (topks.isEmpty()) null else Aggregations(topk = topks),
    )

private fun stringField(): Field = Field(type = PrimitiveType.STRING, comment = "")

private fun edgeSummary(
    database: String,
    table: String,
    topks: List<Topk>,
): V3TableDescriptor =
    V3TableDescriptor.Edge(
        database = database,
        table = table,
        schema =
            ModelSchema.Edge(
                source = stringField(),
                target = stringField(),
                direction = DirectionType.BOTH,
                groups = listOf(groupWithTopks("g", topks)),
            ),
    )

private fun vertexSummary(
    database: String,
    table: String,
): V3TableDescriptor =
    V3TableDescriptor.Vertex(
        database = database,
        table = table,
        schema = ModelSchema.Vertex(id = stringField()),
    )

// endregion
