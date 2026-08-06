package com.kakao.actionbase.v2.engine.entity

import com.kakao.actionbase.core.metadata.common.DirectionType as GroupDirectionType

import com.kakao.actionbase.core.metadata.common.AggregationType
import com.kakao.actionbase.core.metadata.common.Aggregations
import com.kakao.actionbase.core.metadata.common.Bucket
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.GroupType
import com.kakao.actionbase.core.metadata.common.Topk
import com.kakao.actionbase.v2.core.metadata.DirectionType
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.core.types.DataType
import com.kakao.actionbase.v2.core.types.EdgeSchema
import com.kakao.actionbase.v2.core.types.Field
import com.kakao.actionbase.v2.core.types.VertexField
import com.kakao.actionbase.v2.core.types.VertexType

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class LabelEntityFunctionsTest {
    @Nested
    inner class DetectingAggregations {
        @Test
        fun `returns true when a group defines topk`() {
            label(groups = listOf(group(name = "purchased_count", direction = GroupDirectionType.BOTH, fields = emptyList(), topks = listOf(topk(name = "top_purchased")))))
                .hasAggregation()
                .shouldBeTrue()
        }

        @Test
        fun `returns false when no group defines any aggregation`() {
            label(groups = listOf(group(name = "purchased_count", direction = GroupDirectionType.BOTH, fields = emptyList(), topks = emptyList())))
                .hasAggregation()
                .shouldBeFalse()
        }

        @Test
        fun `with the TOPK filter, returns true when a group defines topk`() {
            label(groups = listOf(group(name = "purchased_count", direction = GroupDirectionType.BOTH, fields = emptyList(), topks = listOf(topk(name = "top_purchased")))))
                .hasAggregation(AggregationType.TOPK)
                .shouldBeTrue()
        }

        @Test
        fun `with the TOPK filter, returns false when the label defines no topk`() {
            label(groups = listOf(group(name = "purchased_count", direction = GroupDirectionType.BOTH, fields = emptyList(), topks = emptyList())))
                .hasAggregation(AggregationType.TOPK)
                .shouldBeFalse()
        }
    }

    @Nested
    inner class BuildingQualifiedAggregations {
        @Test
        fun `emits one entry per kind on a user table`() {
            val label =
                label(
                    groups =
                        listOf(
                            group(name = "purchased_count", direction = GroupDirectionType.BOTH, fields = emptyList(), topks = listOf(topk(name = "top_purchased"))),
                            group(name = "viewed_count", direction = GroupDirectionType.BOTH, fields = emptyList(), topks = emptyList()),
                            group(name = "liked_count", direction = GroupDirectionType.BOTH, fields = emptyList(), topks = listOf(topk(name = "top_liked"))),
                        ),
                )

            val result = label.toQualifiedAggregations()

            result shouldHaveSize 1
            result.single().type shouldBe AggregationType.TOPK
            result.single().database shouldBe "commerce"
            result.single().table shouldBe "orders"
        }

        @Test
        fun `returns empty for a user table with no topk`() {
            label(groups = listOf(group(name = "purchased_count", direction = GroupDirectionType.BOTH, fields = emptyList(), topks = emptyList())))
                .toQualifiedAggregations()
                .shouldBeEmpty()
        }

        @Test
        fun `with the TOPK filter, keeps a label that defines topk`() {
            val result =
                label(groups = listOf(group(name = "purchased_count", direction = GroupDirectionType.BOTH, fields = emptyList(), topks = listOf(topk(name = "top_purchased")))))
                    .toQualifiedAggregations(AggregationType.TOPK)

            result shouldHaveSize 1
            result.single().type shouldBe AggregationType.TOPK
        }

        @Test
        fun `unions every ranking's dedupe fields into one key`() {
            val label =
                label(
                    groups =
                        listOf(
                            group(name = "purchased_count", direction = GroupDirectionType.OUT, fields = listOf(Group.Field("category")), topks = listOf(topk(name = "top_purchased", entity = "source"))),
                            group(name = "viewed_count", direction = GroupDirectionType.BOTH, fields = emptyList(), topks = emptyList()),
                            group(name = "liked_count", direction = GroupDirectionType.OUT, fields = emptyList(), topks = listOf(topk(name = "top_liked", entity = "source"))),
                            group(name = "shared_count", direction = GroupDirectionType.OUT, fields = listOf(Group.Field("channel")), topks = listOf(topk(name = "top_shared", entity = "source"))),
                            group(name = "purchased_by_count", direction = GroupDirectionType.IN, fields = emptyList(), topks = listOf(topk(name = "top_purchasers"))),
                        ),
                )

            label.toQualifiedAggregations().single().dedupeFields shouldBe
                listOf("source", "category", "channel", "target")
        }

        @Test
        fun `drops bucket fields from the dedupe key`() {
            val label =
                label(
                    groups =
                        listOf(
                            group(
                                name = "purchased_count",
                                direction = GroupDirectionType.OUT,
                                fields =
                                    listOf(
                                        Group.Field("target"),
                                        Group.Field("day", bucket = Bucket.Date(name = "day", unit = Bucket.ValueUnit.MILLISECOND, timezone = "UTC", format = "yyyy-MM-dd")),
                                    ),
                                topks = listOf(topk(name = "top_purchased", entity = "source")),
                            ),
                        ),
                )

            label.toQualifiedAggregations().single().dedupeFields shouldBe listOf("source", "target")
        }
    }
}

// region test fixtures

// builds the commerce.orders label; only `groups` varies, the rest is required-but-irrelevant setup
private fun label(groups: List<Group>): LabelEntity =
    LabelEntity(
        active = true,
        name = EntityName("commerce", "orders"),
        desc = "",
        type = LabelType.INDEXED,
        schema =
            EdgeSchema(
                VertexField(VertexType.LONG, "src"),
                VertexField(VertexType.LONG, "tgt"),
                listOf(Field("payload", DataType.STRING, true, "")),
            ),
        dirType = DirectionType.BOTH,
        storage = "test",
        groups = groups,
    )

private fun group(
    name: String,
    direction: GroupDirectionType,
    fields: List<Group.Field>,
    topks: List<Topk>,
): Group =
    Group(
        group = name,
        type = GroupType.SUM,
        fields = fields,
        directionType = direction,
        aggregations = Aggregations(topk = topks),
    )

// `dimension` and `rank` do not affect these functions, so they stay defaulted
private fun topk(
    name: String,
    entity: String = "__GLOBAL__",
): Topk = Topk(topk = name, entity = entity, dimension = "target", rank = "${name}__topk")

// endregion
