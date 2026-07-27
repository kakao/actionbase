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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class LabelEntityFunctionsSpec :
    StringSpec({

        "hasAggregation returns true when a group defines topk" {
            labelWithTopks(listOf(topk("t1"))).hasAggregation().shouldBeTrue()
        }

        "hasAggregation returns false when no group defines any aggregation" {
            labelWithTopks(emptyList()).hasAggregation().shouldBeFalse()
        }

        "hasAggregation(type=TOPK) returns true when a group defines topk" {
            labelWithTopks(listOf(topk("t1"))).hasAggregation(AggregationType.TOPK).shouldBeTrue()
        }

        "hasAggregation(type=TOPK) returns false when a user label defines no topk" {
            labelWithTopks(emptyList()).hasAggregation(AggregationType.TOPK).shouldBeFalse()
        }

        "toQualifiedAggregations emits one entry per kind on a user table" {
            val label =
                labelAt(
                    database = "db",
                    table = "orders",
                    groups =
                        listOf(
                            groupWithTopks("g1", listOf(topk("t1"))),
                            groupWithTopks("g2", emptyList()),
                            groupWithTopks("g3", listOf(topk("t3"))),
                        ),
                )

            val result = label.toQualifiedAggregations()

            result shouldHaveSize 1
            result.single().type shouldBe AggregationType.TOPK
            result.single().database shouldBe "db"
            result.single().table shouldBe "orders"
        }

        "toQualifiedAggregations returns empty for a user table with no topk" {
            labelWithTopks(emptyList()).toQualifiedAggregations().shouldBeEmpty()
        }

        "toQualifiedAggregations(type=TOPK) keeps a user label that defines topk" {
            val result = labelWithTopks(listOf(topk("t1"))).toQualifiedAggregations(AggregationType.TOPK)

            result shouldHaveSize 1
            result.single().type shouldBe AggregationType.TOPK
        }

        "toQualifiedAggregations emits a dedupe per topk: directed endpoint then non-bucket fields" {
            val label =
                labelAt(
                    database = "db",
                    table = "orders",
                    groups =
                        listOf(
                            // per-entity, OUT -> keys on the source endpoint plus its fields
                            group("g1", GroupDirectionType.OUT, listOf(field("target"), field("category")), listOf(topk("t1", entity = "source"))),
                            groupWithTopks("g2", emptyList()),
                            // global, IN, no group fields -> the target endpoint is still keyed on
                            group("g3", GroupDirectionType.IN, emptyList(), listOf(topk("t3"))),
                        ),
                )

            val dedupes = label.toQualifiedAggregations().single().dedupes

            dedupes.map { it.name } shouldBe listOf("t1", "t3")
            dedupes[0].fields shouldBe listOf("source", "target", "category")
            dedupes[1].fields shouldBe listOf("target")
        }

        "toQualifiedAggregations drops bucket fields from the dedupe key" {
            val label =
                labelAt(
                    database = "db",
                    table = "orders",
                    groups =
                        listOf(
                            group("g", GroupDirectionType.OUT, listOf(field("target"), bucketField("day")), listOf(topk("t", entity = "source"))),
                        ),
                )

            label.toQualifiedAggregations().single().dedupes.single().fields shouldBe listOf("source", "target")
        }
    }) {
    companion object {
        private fun topk(
            name: String,
            entity: String = "__GLOBAL__",
            rank: String = "${name}__topk",
        ): Topk = Topk(topk = name, entity = entity, dimension = "target", rank = rank)

        private fun groupWithTopks(
            name: String,
            topks: List<Topk>,
        ): Group = group(name, GroupDirectionType.BOTH, emptyList(), topks)

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

        private fun field(name: String): Group.Field = Group.Field(name)

        private fun bucketField(name: String): Group.Field =
            Group.Field(
                name,
                bucket = Bucket.Date(name = name, unit = Bucket.ValueUnit.MILLISECOND, timezone = "UTC", format = "yyyy-MM-dd"),
            )

        private fun labelAt(
            database: String,
            table: String,
            groups: List<Group>,
        ): LabelEntity =
            LabelEntity(
                active = true,
                name = EntityName(database, table),
                desc = "",
                type = LabelType.INDEXED,
                schema = edgeSchema(),
                dirType = DirectionType.BOTH,
                storage = "test",
                groups = groups,
            )

        private fun labelWithTopks(topks: List<Topk>): LabelEntity =
            labelAt(
                database = "db",
                table = "orders",
                groups = listOf(groupWithTopks("g", topks)),
            )

        private fun edgeSchema() =
            EdgeSchema(
                VertexField(VertexType.LONG, "src"),
                VertexField(VertexType.LONG, "tgt"),
                listOf(Field("payload", DataType.STRING, true, "")),
            )
    }
}
