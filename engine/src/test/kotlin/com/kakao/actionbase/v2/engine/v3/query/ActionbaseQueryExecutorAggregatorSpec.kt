package com.kakao.actionbase.v2.engine.v3.query

import com.kakao.actionbase.core.java.codec.common.hbase.Order
import com.kakao.actionbase.engine.query.ActionbaseQuery
import com.kakao.actionbase.engine.query.ActionbaseQueryExecutor
import com.kakao.actionbase.engine.query.LabelProvider
import com.kakao.actionbase.v2.core.types.DataType
import com.kakao.actionbase.v2.core.types.Field
import com.kakao.actionbase.v2.core.types.StructType
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.label.Label
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.Row

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import reactor.test.StepVerifier

class ActionbaseQueryExecutorAggregatorSpec :
    StringSpec({

        val labelProvider =
            object : LabelProvider {
                override fun getLabel(name: EntityName): Label = throw NotImplementedError()
            }
        val executor = ActionbaseQueryExecutor(labelProvider)

        val categorySchema =
            StructType(
                arrayOf(
                    Field("category", DataType.STRING, false),
                    Field("amount", DataType.LONG, false),
                ),
            )

        fun categoryDataFrame(vararg entries: Pair<String, Long>): DataFrame =
            DataFrame(
                entries.map { (category, amount) -> Row(arrayOf(category, amount)) },
                categorySchema,
            )

        // region aggregateCount

        "aggregateCount: groups rows by field and counts occurrences in DESC order" {
            val df =
                categoryDataFrame(
                    "a" to 10L,
                    "a" to 20L,
                    "a" to 30L,
                    "b" to 40L,
                    "b" to 50L,
                    "c" to 60L,
                )

            val agg = ActionbaseQuery.Aggregator.Count(field = "category", order = Order.DESC, limit = 10)

            StepVerifier
                .create(executor.aggregateCount(df, agg))
                .expectNextMatches { result ->
                    result.schema.fields.map { it.name } shouldBe listOf("category", "count")
                    result.schema.fields[1].type shouldBe DataType.LONG
                    result.rows.map { it.array.toList() } shouldBe
                        listOf(
                            listOf("a", 3L),
                            listOf("b", 2L),
                            listOf("c", 1L),
                        )
                    true
                }.verifyComplete()
        }

        "aggregateCount: sorts ascending when order is ASC" {
            val df =
                categoryDataFrame(
                    "a" to 10L,
                    "a" to 20L,
                    "a" to 30L,
                    "b" to 40L,
                    "c" to 60L,
                )

            val agg = ActionbaseQuery.Aggregator.Count(field = "category", order = Order.ASC, limit = 10)

            StepVerifier
                .create(executor.aggregateCount(df, agg))
                .expectNextMatches { result ->
                    result.rows.map { it.array.toList() } shouldBe
                        listOf(
                            listOf("b", 1L),
                            listOf("c", 1L),
                            listOf("a", 3L),
                        )
                    true
                }.verifyComplete()
        }

        "aggregateCount: truncates result set according to limit" {
            val df =
                categoryDataFrame(
                    "a" to 10L,
                    "a" to 20L,
                    "a" to 30L,
                    "b" to 40L,
                    "b" to 50L,
                    "c" to 60L,
                    "d" to 70L,
                )

            val agg = ActionbaseQuery.Aggregator.Count(field = "category", order = Order.DESC, limit = 2)

            StepVerifier
                .create(executor.aggregateCount(df, agg))
                .expectNextMatches { result ->
                    result.rows.size shouldBe 2
                    result.rows.map { it.array.toList() } shouldBe
                        listOf(
                            listOf("a", 3L),
                            listOf("b", 2L),
                        )
                    true
                }.verifyComplete()
        }

        "aggregateCount: produces empty DataFrame when input has no rows" {
            val df = DataFrame(emptyList(), categorySchema)
            val agg = ActionbaseQuery.Aggregator.Count(field = "category", order = Order.DESC, limit = 10)

            StepVerifier
                .create(executor.aggregateCount(df, agg))
                .expectNextMatches { result ->
                    result.rows shouldBe emptyList()
                    result.schema.fields.map { it.name } shouldBe listOf("category", "count")
                    true
                }.verifyComplete()
        }

        "aggregateCount: throws when target field does not exist" {
            val df = categoryDataFrame("a" to 10L)
            val agg = ActionbaseQuery.Aggregator.Count(field = "missing", order = Order.DESC, limit = 10)

            shouldThrow<IllegalArgumentException> {
                executor.aggregateCount(df, agg).block()
            }
        }

        // endregion

        // region aggregateSum

        "aggregateSum: groups by single key and sums values in DESC order" {
            val df =
                categoryDataFrame(
                    "a" to 10L,
                    "a" to 20L,
                    "b" to 5L,
                    "b" to 15L,
                    "b" to 25L,
                    "c" to 100L,
                )

            val agg =
                ActionbaseQuery.Aggregator.Sum(
                    valueField = "amount",
                    keyFields = listOf("category"),
                    order = Order.DESC,
                    limit = 10,
                )

            StepVerifier
                .create(executor.aggregateSum(df, agg))
                .expectNextMatches { result ->
                    result.schema.fields.map { it.name } shouldBe listOf("category", "amount")
                    result.schema.fields[1].type shouldBe DataType.DOUBLE
                    result.rows.map { it.array.toList() } shouldBe
                        listOf(
                            listOf("c", 100.0),
                            listOf("b", 45.0),
                            listOf("a", 30.0),
                        )
                    true
                }.verifyComplete()
        }

        "aggregateSum: sorts ascending when order is ASC" {
            val df =
                categoryDataFrame(
                    "a" to 50L,
                    "b" to 10L,
                    "c" to 30L,
                )

            val agg =
                ActionbaseQuery.Aggregator.Sum(
                    valueField = "amount",
                    keyFields = listOf("category"),
                    order = Order.ASC,
                    limit = 10,
                )

            StepVerifier
                .create(executor.aggregateSum(df, agg))
                .expectNextMatches { result ->
                    result.rows.map { it.array.toList() } shouldBe
                        listOf(
                            listOf("b", 10.0),
                            listOf("c", 30.0),
                            listOf("a", 50.0),
                        )
                    true
                }.verifyComplete()
        }

        "aggregateSum: groups by multiple key fields" {
            val schema =
                StructType(
                    arrayOf(
                        Field("region", DataType.STRING, false),
                        Field("product", DataType.STRING, false),
                        Field("amount", DataType.LONG, false),
                    ),
                )
            val df =
                DataFrame(
                    listOf(
                        Row(arrayOf("KR", "A", 10L)),
                        Row(arrayOf("KR", "A", 20L)),
                        Row(arrayOf("KR", "B", 30L)),
                        Row(arrayOf("US", "A", 40L)),
                    ),
                    schema,
                )

            val agg =
                ActionbaseQuery.Aggregator.Sum(
                    valueField = "amount",
                    keyFields = listOf("region", "product"),
                    order = Order.DESC,
                    limit = 10,
                )

            StepVerifier
                .create(executor.aggregateSum(df, agg))
                .expectNextMatches { result ->
                    result.schema.fields.map { it.name } shouldBe listOf("region", "product", "amount")
                    result.rows.map { it.array.toList() } shouldBe
                        listOf(
                            listOf("US", "A", 40.0),
                            listOf("KR", "A", 30.0),
                            listOf("KR", "B", 30.0),
                        )
                    true
                }.verifyComplete()
        }

        "aggregateSum: truncates result set according to limit" {
            val df =
                categoryDataFrame(
                    "a" to 10L,
                    "b" to 20L,
                    "c" to 30L,
                    "d" to 40L,
                )

            val agg =
                ActionbaseQuery.Aggregator.Sum(
                    valueField = "amount",
                    keyFields = listOf("category"),
                    order = Order.DESC,
                    limit = 2,
                )

            StepVerifier
                .create(executor.aggregateSum(df, agg))
                .expectNextMatches { result ->
                    result.rows.size shouldBe 2
                    result.rows.map { it.array.toList() } shouldBe
                        listOf(
                            listOf("d", 40.0),
                            listOf("c", 30.0),
                        )
                    true
                }.verifyComplete()
        }

        "aggregateSum: produces empty DataFrame when input has no rows" {
            val df = DataFrame(emptyList(), categorySchema)
            val agg =
                ActionbaseQuery.Aggregator.Sum(
                    valueField = "amount",
                    keyFields = listOf("category"),
                    order = Order.DESC,
                    limit = 10,
                )

            StepVerifier
                .create(executor.aggregateSum(df, agg))
                .expectNextMatches { result ->
                    result.rows shouldBe emptyList()
                    result.schema.fields.map { it.name } shouldBe listOf("category", "amount")
                    true
                }.verifyComplete()
        }

        "aggregateSum: throws when value field does not exist" {
            val df = categoryDataFrame("a" to 10L)
            val agg =
                ActionbaseQuery.Aggregator.Sum(
                    valueField = "missing",
                    keyFields = listOf("category"),
                    order = Order.DESC,
                    limit = 10,
                )

            shouldThrow<IllegalArgumentException> {
                executor.aggregateSum(df, agg).block()
            }
        }

        "aggregateSum: throws when key field does not exist" {
            val df = categoryDataFrame("a" to 10L)
            val agg =
                ActionbaseQuery.Aggregator.Sum(
                    valueField = "amount",
                    keyFields = listOf("missing"),
                    order = Order.DESC,
                    limit = 10,
                )

            shouldThrow<IllegalArgumentException> {
                executor.aggregateSum(df, agg).block()
            }
        }

        // endregion
    })
