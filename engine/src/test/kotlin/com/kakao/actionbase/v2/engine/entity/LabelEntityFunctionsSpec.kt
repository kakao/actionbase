package com.kakao.actionbase.v2.engine.entity

import com.kakao.actionbase.core.metadata.common.DirectionType as GroupDirectionType

import com.kakao.actionbase.core.metadata.common.AggregationConstants.TOPK_DATABASE
import com.kakao.actionbase.core.metadata.common.AggregationConstants.TOPK_EXPIRE_TABLE
import com.kakao.actionbase.core.metadata.common.AggregationType
import com.kakao.actionbase.core.metadata.common.Aggregations
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.GroupType
import com.kakao.actionbase.core.metadata.common.Topk
import com.kakao.actionbase.core.metadata.common.TopkTable
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

        "hasAggregation ignores the system table registry" {
            labelAt(TOPK_DATABASE, TOPK_EXPIRE_TABLE, groups = emptyList()).hasAggregation().shouldBeFalse()
        }

        "hasAggregation(type=TOPK) returns true when a group defines topk" {
            labelWithTopks(listOf(topk("t1"))).hasAggregation(AggregationType.TOPK).shouldBeTrue()
        }

        "hasAggregation(type=TOPK) returns false when a user label defines no topk" {
            labelWithTopks(emptyList()).hasAggregation(AggregationType.TOPK).shouldBeFalse()
        }

        "isSystemTable returns true for the topk expire table" {
            labelAt(TOPK_DATABASE, TOPK_EXPIRE_TABLE, groups = emptyList()).isSystemTable().shouldBeTrue()
        }

        "isSystemTable returns false for a user table" {
            labelWithTopks(emptyList()).isSystemTable().shouldBeFalse()
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
            result.single().expire.shouldBeFalse()
        }

        "toQualifiedAggregations returns empty for a user table with no topk" {
            labelWithTopks(emptyList()).toQualifiedAggregations().shouldBeEmpty()
        }

        "toQualifiedAggregations returns empty for a system table (system path is separate)" {
            labelAt(TOPK_DATABASE, TOPK_EXPIRE_TABLE, groups = emptyList()).toQualifiedAggregations().shouldBeEmpty()
        }

        "toQualifiedAggregations(type=TOPK) keeps a user label that defines topk" {
            val result = labelWithTopks(listOf(topk("t1"))).toQualifiedAggregations(AggregationType.TOPK)

            result shouldHaveSize 1
            result.single().type shouldBe AggregationType.TOPK
        }

        "toSystemQualifiedAggregations emits an expire entry for the topk system table" {
            val result =
                labelAt(TOPK_DATABASE, TOPK_EXPIRE_TABLE, groups = emptyList())
                    .toSystemQualifiedAggregations()

            result shouldHaveSize 1
            result.single().type shouldBe AggregationType.TOPK
            result.single().database shouldBe TOPK_DATABASE
            result.single().table shouldBe TOPK_EXPIRE_TABLE
            result.single().expire.shouldBeTrue()
        }

        "toSystemQualifiedAggregations returns empty for a user table" {
            labelWithTopks(listOf(topk("t1"))).toSystemQualifiedAggregations().shouldBeEmpty()
        }

        "toSystemQualifiedAggregations(type=TOPK) emits the topk expire entry" {
            val result =
                labelAt(TOPK_DATABASE, TOPK_EXPIRE_TABLE, groups = emptyList())
                    .toSystemQualifiedAggregations(AggregationType.TOPK)

            result shouldHaveSize 1
            result.single().expire.shouldBeTrue()
        }
    }) {
    companion object {
        private fun topk(
            name: String,
            table: TopkTable = TopkTable(score = "${name}__topk", expire = "$TOPK_DATABASE.$TOPK_EXPIRE_TABLE"),
        ): Topk = Topk(topk = name, table = table)

        private fun groupWithTopks(
            name: String,
            topks: List<Topk>,
        ): Group =
            Group(
                group = name,
                type = GroupType.SUM,
                fields = emptyList(),
                directionType = GroupDirectionType.BOTH,
                aggregations = Aggregations(topk = topks),
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
