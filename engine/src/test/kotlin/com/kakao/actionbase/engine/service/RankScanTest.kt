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
        val rank = RankScan.from(schema = schema(), database = "commerce", table = "orders_table", topk = "top_purchased", entity = "user1", dimensionValues = emptyList())

        rank.database shouldBe "commerce"
        rank.table shouldBe "orders_table__topk"
        rank.index shouldBe "metric_desc"
        rank.direction shouldBe Direction.OUT
    }

    @Test
    fun `keys the scan by the source table and the entity`() {
        val rank = RankScan.from(schema = schema(), database = "commerce", table = "orders_table", topk = "top_purchased", entity = "user1", dimensionValues = emptyList())

        rank.start shouldBe "commerce|orders_table|top_purchased|user1"
    }

    @Test
    fun `dimension values extend the key in the order they were given`() {
        val rank = RankScan.from(schema = schema(), database = "commerce", table = "orders_table", topk = "top_purchased", entity = "user1", dimensionValues = listOf("fruit", "seoul"))

        rank.start shouldBe "commerce|orders_table|top_purchased|user1|fruit|seoul"
    }

    @Test
    fun `rejects a topk the table does not declare`() {
        shouldThrow<IllegalArgumentException> {
            RankScan.from(schema = schema(), database = "commerce", table = "orders_table", topk = "top_viewed", entity = "user1", dimensionValues = emptyList())
        }
    }
}

// region test fixtures

// only `topk` and `rank` steer the resolution, so the rest of the schema stays minimal
private fun schema(): ModelSchema =
    ModelSchema.Edge(
        source = Field(type = PrimitiveType.STRING, comment = "user"),
        target = Field(type = PrimitiveType.STRING, comment = "item"),
        direction = DirectionType.OUT,
        groups =
            listOf(
                Group(
                    group = "purchased_count",
                    type = GroupType.COUNT,
                    fields = listOf(Group.Field(name = "_target")),
                    aggregations = Aggregations(topk = listOf(Topk(topk = "top_purchased", entity = "source", dimension = "target", rank = "commerce.orders_table__topk"))),
                ),
            ),
    )

// endregion
