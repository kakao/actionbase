package com.kakao.actionbase.engine.service

import com.kakao.actionbase.core.metadata.common.Aggregations
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Field
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.GroupType
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.metadata.common.Topk
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.v2.core.metadata.Direction

import org.junit.jupiter.api.Test

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

class RankScanTest {
    @Test
    fun `reads the rank table declared by the topk, in rank order`() {
        val rank = rankScan()

        rank.database shouldBe "commerce"
        rank.table shouldBe "orders_table__topk"
        rank.index shouldBe "metric_desc"
        rank.direction shouldBe Direction.OUT
    }

    @Test
    fun `keys the scan by the source table and the entity`() {
        rankScan().start shouldBe "commerce|orders_table|top_purchased|user1"
    }

    @Test
    fun `orders the named values the way the group declares them, not the way they were passed`() {
        val rank =
            rankScan(
                schema = schema(dimensionFields = listOf("category", "region")),
                dimensionValues = mapOf("region" to "seoul", "category" to "fruit"),
            )

        rank.start shouldBe "commerce|orders_table|top_purchased|user1|fruit|seoul"
    }

    @Test
    fun `a value left out reads as empty`() {
        val rank =
            rankScan(
                schema = schema(dimensionFields = listOf("category", "region")),
                dimensionValues = mapOf("region" to "seoul"),
            )

        rank.start shouldBe "commerce|orders_table|top_purchased|user1||seoul"
    }

    @Test
    fun `a value the group does not declare is ignored`() {
        val rank =
            rankScan(
                schema = schema(dimensionFields = listOf("category")),
                dimensionValues = mapOf("category" to "fruit", "shoeSize" to "270"),
            )

        rank.start shouldBe "commerce|orders_table|top_purchased|user1|fruit"
    }

    @Test
    fun `a global topk keys by the sentinel and ignores the entity`() {
        val rank = rankScan(schema = schema(entity = "__GLOBAL__"), entity = "user1")

        rank.start shouldBe "commerce|orders_table|top_purchased|__GLOBAL__"
    }

    @Test
    fun `rejects a topk the table does not declare`() {
        shouldThrow<IllegalArgumentException> { rankScan(topk = "top_viewed") }
    }
}

// region test fixtures

private fun rankScan(
    schema: ModelSchema = schema(),
    topk: String = "top_purchased",
    entity: String? = "user1",
    dimensionValues: Map<String, String> = emptyMap(),
): RankScan =
    RankScan.from(
        schema = schema,
        database = "commerce",
        table = "orders_table",
        topk = topk,
        entity = entity,
        dimensionValues = dimensionValues,
    )

private fun schema(
    entity: String = "source",
    dimensionFields: List<String> = emptyList(),
): ModelSchema =
    ModelSchema.Edge(
        source = Field(type = PrimitiveType.STRING, comment = "user"),
        target = Field(type = PrimitiveType.STRING, comment = "item"),
        direction = DirectionType.OUT,
        groups =
            listOf(
                Group(
                    group = "purchased_count",
                    type = GroupType.COUNT,
                    fields = listOf(Group.Field(name = "_target")) + dimensionFields.map { Group.Field(name = it) },
                    aggregations =
                        Aggregations(
                            topk = listOf(Topk(topk = "top_purchased", entity = entity, dimension = "target", rank = "commerce.orders_table__topk")),
                        ),
                ),
            ),
    )

// endregion
