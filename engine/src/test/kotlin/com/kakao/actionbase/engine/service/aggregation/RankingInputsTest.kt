package com.kakao.actionbase.engine.service.aggregation

import com.kakao.actionbase.core.metadata.common.AggregationType
import com.kakao.actionbase.core.metadata.common.Aggregations
import com.kakao.actionbase.core.metadata.common.Bucket
import com.kakao.actionbase.core.metadata.common.Direction
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.GroupType
import com.kakao.actionbase.core.metadata.common.Topk

import org.junit.jupiter.api.Test

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class RankingInputsTest {
    @Test
    fun `OUT ranks the source, with the dimension value as the target`() {
        val inputs = RankingInputs.from(event = event(source = "user1", target = "item1"), direction = Direction.OUT, topk = topk(entity = "source", dimension = "target"))

        inputs.entity shouldBe "user1"
        inputs.topkDimensionValue shouldBe "item1"
        inputs.dimensionValues.shouldBeEmpty()
    }

    @Test
    fun `IN ranks the target, with the source as the target`() {
        val inputs = RankingInputs.from(event = event(source = "user1", target = "item1"), direction = Direction.IN, topk = topk(entity = "target", dimension = "source"))

        inputs.entity shouldBe "item1"
        inputs.topkDimensionValue shouldBe "user1"
        inputs.dimensionValues.shouldBeEmpty()
    }

    @Test
    fun `the global sentinel entity is kept as-is`() {
        val inputs = RankingInputs.from(event = event(source = "user1"), direction = Direction.OUT, topk = topk(entity = "__GLOBAL__"))

        inputs.entity shouldBe "__GLOBAL__"
    }

    @Test
    fun `bucket fields are excluded from the dimension values`() {
        val inputs =
            RankingInputs.from(
                event =
                    event(
                        properties = mapOf("category" to "fruit", "purchasedAt" to 1_700_000_000_000L),
                        fields = listOf(Group.Field(name = "category"), Group.Field(name = "purchasedAt", bucket = day())),
                    ),
                direction = Direction.OUT,
                topk = topk(dimension = "target"),
            )

        inputs.dimensionValues shouldBe listOf("fruit")
    }

    @Test
    fun `a property-backed dimension becomes the target and drops out of the dimension values`() {
        val inputs =
            RankingInputs.from(
                event =
                    event(
                        properties = mapOf("category" to "fruit", "purchasedAt" to 1_700_000_000_000L),
                        fields = listOf(Group.Field(name = "category"), Group.Field(name = "purchasedAt", bucket = day())),
                    ),
                direction = Direction.OUT,
                topk = topk(dimension = "category"),
            )

        inputs.topkDimensionValue shouldBe "fruit"
        inputs.dimensionValues.shouldBeEmpty()
    }

    @Test
    fun `multiple non-bucket fields join the dimension values in order`() {
        val inputs =
            RankingInputs.from(
                event =
                    event(
                        properties = mapOf("category" to "fruit", "region" to "seoul", "purchasedAt" to 1_700_000_000_000L),
                        fields =
                            listOf(
                                Group.Field(name = "_target"),
                                Group.Field(name = "category"),
                                Group.Field(name = "region"),
                                Group.Field(name = "purchasedAt", bucket = day()),
                            ),
                    ),
                direction = Direction.OUT,
                topk = topk(dimension = "target"),
            )

        inputs.dimensionValues shouldBe listOf("fruit", "seoul")
    }

    @Test
    fun `declared properties resolve from endpoints and edge properties`() {
        val inputs =
            RankingInputs.from(
                event = event(target = "item1", properties = mapOf("category" to "fruit")),
                direction = Direction.OUT,
                topk = topk(additionalProperties = listOf("category", "target")),
            )

        inputs.properties shouldBe mapOf("category" to "fruit", "target" to "item1")
    }

    @Test
    fun `ranges placeholders are interpolated from the edge`() {
        val inputs = RankingInputs.from(event = event(target = "item1"), direction = Direction.OUT, topk = topk(ranges = "_target:eq:{_target}"))

        inputs.ranges shouldBe "_target:eq:item1"
    }
}

// region test fixtures

private fun event(
    source: String = "user1",
    target: String = "item1",
    properties: Map<String, Any?> = emptyMap(),
    fields: List<Group.Field> = emptyList(),
): EdgeAggregationEvent =
    EdgeAggregationEvent(
        type = AggregationType.TOPK,
        database = "commerce",
        table = "orders",
        source = source,
        target = target,
        properties = properties,
        direction = DirectionType.BOTH,
        group = Group(group = "purchased_count", type = GroupType.SUM, fields = fields, directionType = DirectionType.BOTH, aggregations = Aggregations(topk = emptyList())),
        aggregations = Aggregations(topk = emptyList()),
    )

// `refreshAfterMillis` and `rank` do not affect resolution, so they stay defaulted
private fun topk(
    entity: String = "source",
    dimension: String = "target",
    ranges: String = "",
    additionalProperties: List<String> = emptyList(),
): Topk = Topk(topk = "top_purchased", entity = entity, dimension = dimension, ranges = ranges, rank = "commerce.orders__topk", additionalProperties = additionalProperties)

private fun day(): Bucket.Date = Bucket.Date(name = "day", unit = Bucket.ValueUnit.MILLISECOND, timezone = "UTC", format = "yyyy-MM-dd")

// endregion
