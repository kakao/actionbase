package com.kakao.actionbase.v2.engine.v3.query

import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.core.metadata.common.StructType
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.engine.QueryEngine
import com.kakao.actionbase.engine.binding.TableBinding
import com.kakao.actionbase.engine.query.ActionbaseQuery
import com.kakao.actionbase.engine.query.ActionbaseQueryExecutor
import com.kakao.actionbase.engine.sql.DataFrame
import com.kakao.actionbase.engine.sql.Row
import com.kakao.actionbase.v2.core.types.DataType

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import reactor.test.StepVerifier

class ActionbaseQueryExecutorPostProcessJsonObjectSpec :
    StringSpec({

        val engine =
            object : QueryEngine {
                override fun getTableBinding(
                    database: String,
                    alias: String,
                ): TableBinding = throw NotImplementedError()
            }
        val executor = ActionbaseQueryExecutor(engine)

        val idJsonSchema =
            StructType(
                listOf(
                    StructField("id", PrimitiveType.STRING, "", false),
                    StructField("json_data", PrimitiveType.STRING, "", true),
                ),
            )

        fun idJsonDataFrame(vararg entries: Pair<String, String?>): DataFrame =
            DataFrame(
                entries.map { (id, json) -> Row(mapOf("id" to id, "json_data" to json), idJsonSchema) },
                idJsonSchema,
                total = entries.size.toLong(),
            )

        "should extract JSON fields correctly" {
            val df =
                idJsonDataFrame(
                    "1" to """{"name": "John", "age": 30, "city": "New York"}""",
                    "2" to """{"name": "Jane", "age": 25, "city": "London"}""",
                    "3" to null,
                )

            val plan =
                ActionbaseQuery.PostProcessor.JsonObject(
                    field = "json_data",
                    paths =
                        listOf(
                            ActionbaseQuery.PostProcessor.JsonObject.Path("name", "extracted_name", DataType.STRING),
                            ActionbaseQuery.PostProcessor.JsonObject.Path("age", "extracted_age", DataType.INT),
                        ),
                    drop = false,
                )

            StepVerifier
                .create(executor.postProcessJsonObject(df, plan))
                .expectNextMatches { result ->
                    result.schema.fields.map { it.name } shouldBe listOf("id", "json_data", "extracted_name", "extracted_age")
                    result.rows.size shouldBe 3
                    result.rows[0].data shouldBe mapOf("id" to "1", "json_data" to """{"name": "John", "age": 30, "city": "New York"}""", "extracted_name" to "John", "extracted_age" to 30)
                    result.rows[1].data shouldBe mapOf("id" to "2", "json_data" to """{"name": "Jane", "age": 25, "city": "London"}""", "extracted_name" to "Jane", "extracted_age" to 25)
                    result.rows[2].data shouldBe mapOf("id" to "3", "json_data" to null, "extracted_name" to null, "extracted_age" to null)
                    true
                }.verifyComplete()
        }

        "should drop original JSON field when specified" {
            val df =
                idJsonDataFrame(
                    "1" to """{"name": "John", "age": 30}""",
                    "2" to """{"name": "Jane", "age": 25}""",
                )

            val plan =
                ActionbaseQuery.PostProcessor.JsonObject(
                    field = "json_data",
                    paths =
                        listOf(
                            ActionbaseQuery.PostProcessor.JsonObject.Path("name", "extracted_name", DataType.STRING),
                        ),
                    drop = true,
                )

            StepVerifier
                .create(executor.postProcessJsonObject(df, plan))
                .expectNextMatches { result ->
                    result.schema.fields.map { it.name } shouldBe listOf("id", "extracted_name")
                    result.rows.size shouldBe 2
                    result.rows[0].data shouldBe mapOf("id" to "1", "extracted_name" to "John")
                    result.rows[1].data shouldBe mapOf("id" to "2", "extracted_name" to "Jane")
                    true
                }.verifyComplete()
        }

        "should handle missing JSON fields gracefully" {
            val df =
                idJsonDataFrame(
                    "1" to """{"name": "John", "age": 30}""",
                    "2" to """{"name": "Jane"}""",
                )

            val plan =
                ActionbaseQuery.PostProcessor.JsonObject(
                    field = "json_data",
                    paths =
                        listOf(
                            ActionbaseQuery.PostProcessor.JsonObject.Path("name", "extracted_name", DataType.STRING),
                            ActionbaseQuery.PostProcessor.JsonObject.Path("age", "extracted_age", DataType.INT),
                        ),
                    drop = false,
                )

            StepVerifier
                .create(executor.postProcessJsonObject(df, plan))
                .expectNextMatches { result ->
                    result.rows[1].data["extracted_age"] shouldBe null
                    true
                }.verifyComplete()
        }

        "should handle nested JSON objects" {
            val df =
                idJsonDataFrame(
                    "1" to """{"user": {"name": "John", "details": {"age": 30}}}""",
                    "2" to """{"user": {"name": "Jane", "details": {"age": 25}}}""",
                )

            val plan =
                ActionbaseQuery.PostProcessor.JsonObject(
                    field = "json_data",
                    paths =
                        listOf(
                            ActionbaseQuery.PostProcessor.JsonObject.Path("user.name", "extracted_name", DataType.STRING),
                            ActionbaseQuery.PostProcessor.JsonObject.Path("user.details.age", "extracted_age", DataType.INT),
                        ),
                    drop = false,
                )

            StepVerifier
                .create(executor.postProcessJsonObject(df, plan))
                .expectNextMatches { result ->
                    result.rows[0].data["extracted_name"] shouldBe "John"
                    result.rows[0].data["extracted_age"] shouldBe 30
                    result.rows[1].data["extracted_name"] shouldBe "Jane"
                    result.rows[1].data["extracted_age"] shouldBe 25
                    true
                }.verifyComplete()
        }

        "should handle empty JSON objects" {
            val df =
                idJsonDataFrame(
                    "1" to "{}",
                    "2" to "{}",
                )

            val plan =
                ActionbaseQuery.PostProcessor.JsonObject(
                    field = "json_data",
                    paths =
                        listOf(
                            ActionbaseQuery.PostProcessor.JsonObject.Path("name", "extracted_name", DataType.STRING),
                        ),
                    drop = false,
                )

            StepVerifier
                .create(executor.postProcessJsonObject(df, plan))
                .expectNextMatches { result ->
                    result.rows[0].data["extracted_name"] shouldBe null
                    result.rows[1].data["extracted_name"] shouldBe null
                    true
                }.verifyComplete()
        }
    })
