package com.kakao.actionbase.v2.engine.v3.query

import com.kakao.actionbase.core.java.codec.common.hbase.Order
import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.core.metadata.common.StructType
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.engine.QueryEngine
import com.kakao.actionbase.engine.binding.TableBinding
import com.kakao.actionbase.engine.query.ActionbaseQuery
import com.kakao.actionbase.engine.query.ActionbaseQueryExecutor
import com.kakao.actionbase.engine.sql.DataFrame
import com.kakao.actionbase.engine.sql.Row

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import reactor.test.StepVerifier

class ActionbaseQueryExecutorAggregatorSpec :
    StringSpec({

        val engine =
            object : QueryEngine {
                override fun getTableBinding(
                    database: String,
                    alias: String,
                ): TableBinding = throw NotImplementedError()
            }
        val executor = ActionbaseQueryExecutor(engine)

        val categorySchema =
            StructType(
                listOf(
                    StructField("category", PrimitiveType.STRING, "", false),
                    StructField("amount", PrimitiveType.LONG, "", false),
                ),
            )

        fun categoryDataFrame(vararg entries: Pair<String, Long>): DataFrame =
            DataFrame(
                entries.map { (category, amount) -> Row(mapOf("category" to category, "amount" to amount), categorySchema) },
                categorySchema,
                total = entries.size.toLong(),
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
                    result.schema.fields[1].type shouldBe PrimitiveType.LONG
                    result.rows.map { listOf(it.data["category"], it.data["count"]) } shouldBe
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
                    result.rows.map { listOf(it.data["category"], it.data["count"]) } shouldBe
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
                    result.rows.map { listOf(it.data["category"], it.data["count"]) } shouldBe
                        listOf(
                            listOf("a", 3L),
                            listOf("b", 2L),
                        )
                    true
                }.verifyComplete()
        }

        "aggregateCount: produces empty DataFrame when input has no rows" {
            val df = DataFrame(emptyList(), categorySchema, total = 0L)
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

            shouldThrow<NoSuchElementException> {
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
                    result.schema.fields[1].type shouldBe PrimitiveType.DOUBLE
                    result.rows.map { listOf(it.data["category"], it.data["amount"]) } shouldBe
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
                    result.rows.map { listOf(it.data["category"], it.data["amount"]) } shouldBe
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
                    listOf(
                        StructField("region", PrimitiveType.STRING, "", false),
                        StructField("product", PrimitiveType.STRING, "", false),
                        StructField("amount", PrimitiveType.LONG, "", false),
                    ),
                )
            val df =
                DataFrame(
                    listOf(
                        Row(mapOf("region" to "KR", "product" to "A", "amount" to 10L), schema),
                        Row(mapOf("region" to "KR", "product" to "A", "amount" to 20L), schema),
                        Row(mapOf("region" to "KR", "product" to "B", "amount" to 30L), schema),
                        Row(mapOf("region" to "US", "product" to "A", "amount" to 40L), schema),
                    ),
                    schema,
                    total = 4L,
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
                    result.rows.map { listOf(it.data["region"], it.data["product"], it.data["amount"]) } shouldBe
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
                    result.rows.map { listOf(it.data["category"], it.data["amount"]) } shouldBe
                        listOf(
                            listOf("d", 40.0),
                            listOf("c", 30.0),
                        )
                    true
                }.verifyComplete()
        }

        "aggregateSum: produces empty DataFrame when input has no rows" {
            val df = DataFrame(emptyList(), categorySchema, total = 0L)
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

            shouldThrow<NullPointerException> {
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

            shouldThrow<NoSuchElementException> {
                executor.aggregateSum(df, agg).block()
            }
        }

        // endregion
    })
