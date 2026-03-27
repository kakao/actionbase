package com.kakao.actionbase.v2.engine.v3.query

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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import reactor.test.StepVerifier

class ActionbaseQueryExecutorPostProcessJsonObjectSpec :
    StringSpec({

        val labelProvider =
            object : LabelProvider {
                override fun getLabel(name: EntityName): Label = throw NotImplementedError()
            }
        val executor = ActionbaseQueryExecutor(labelProvider)

        "should extract JSON fields correctly" {
            val df =
                DataFrame(
                    listOf(
                        Row(arrayOf("1", """{"name": "John", "age": 30, "city": "New York"}""")),
                        Row(arrayOf("2", """{"name": "Jane", "age": 25, "city": "London"}""")),
                        Row(arrayOf("3", null)),
                    ),
                    StructType(
                        arrayOf(
                            Field("id", DataType.STRING, false),
                            Field("json_data", DataType.STRING, true),
                        ),
                    ),
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
                    result.rows[0].array shouldBe arrayOf("1", """{"name": "John", "age": 30, "city": "New York"}""", "John", 30)
                    result.rows[1].array shouldBe arrayOf("2", """{"name": "Jane", "age": 25, "city": "London"}""", "Jane", 25)
                    result.rows[2].array shouldBe arrayOf("3", null, null, null)
                    true
                }.verifyComplete()
        }

        "should drop original JSON field when specified" {
            val df =
                DataFrame(
                    listOf(
                        Row(arrayOf("1", """{"name": "John", "age": 30}""")),
                        Row(arrayOf("2", """{"name": "Jane", "age": 25}""")),
                    ),
                    StructType(
                        arrayOf(
                            Field("id", DataType.STRING, false),
                            Field("json_data", DataType.STRING, true),
                        ),
                    ),
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
                    result.rows[0].array shouldBe arrayOf("1", "John")
                    result.rows[1].array shouldBe arrayOf("2", "Jane")
                    true
                }.verifyComplete()
        }

        "should handle missing JSON fields gracefully" {
            val df =
                DataFrame(
                    listOf(
                        Row(arrayOf("1", """{"name": "John", "age": 30}""")),
                        Row(arrayOf("2", """{"name": "Jane"}""")),
                    ),
                    StructType(
                        arrayOf(
                            Field("id", DataType.STRING, false),
                            Field("json_data", DataType.STRING, true),
                        ),
                    ),
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
                    result.rows.size shouldBe 2
                    result.rows[0].array shouldBe arrayOf("1", """{"name": "John", "age": 30}""", "John", 30)
                    result.rows[1].array shouldBe arrayOf("2", """{"name": "Jane"}""", "Jane", null)
                    true
                }.verifyComplete()
        }

        "should handle different JSON value types correctly" {
            val df =
                DataFrame(
                    listOf(
                        Row(
                            arrayOf("1", """{"string": "text", "integer": 42, "float": 3.14, "boolean": true, "null": null}"""),
                        ),
                        Row(
                            arrayOf(
                                "2",
                                """{"string": "another", "integer": 100, "float": 2.718, "boolean": false, "null": null}""",
                            ),
                        ),
                    ),
                    StructType(
                        arrayOf(
                            Field("id", DataType.STRING, false),
                            Field("json_data", DataType.STRING, true),
                        ),
                    ),
                )

            val plan =
                ActionbaseQuery.PostProcessor.JsonObject(
                    field = "json_data",
                    paths =
                        listOf(
                            ActionbaseQuery.PostProcessor.JsonObject.Path("string", "extracted_string", DataType.STRING),
                            ActionbaseQuery.PostProcessor.JsonObject.Path("integer", "extracted_integer", DataType.INT),
                            ActionbaseQuery.PostProcessor.JsonObject.Path("float", "extracted_float", DataType.FLOAT),
                            ActionbaseQuery.PostProcessor.JsonObject.Path("boolean", "extracted_boolean", DataType.BOOLEAN),
                            ActionbaseQuery.PostProcessor.JsonObject.Path("null", "extracted_null", DataType.STRING),
                        ),
                    drop = true,
                )

            StepVerifier
                .create(executor.postProcessJsonObject(df, plan))
                .expectNextMatches { result ->
                    result.schema.fields.map {
                        it.name
                    } shouldBe listOf("id", "extracted_string", "extracted_integer", "extracted_float", "extracted_boolean", "extracted_null")
                    result.rows.size shouldBe 2
                    result.rows[0].array shouldBe arrayOf("1", "text", 42, 3.14, true, null)
                    result.rows[1].array shouldBe arrayOf("2", "another", 100, 2.718, false, null)
                    true
                }.verifyComplete()
        }

        "should handle nested JSON objects" {
            val df =
                DataFrame(
                    listOf(
                        Row(arrayOf("1", """{"user": {"name": "John", "details": {"age": 30}}}""")),
                        Row(arrayOf("2", """{"user": {"name": "Jane", "details": {"age": 25}}}""")),
                    ),
                    StructType(
                        arrayOf(
                            Field("id", DataType.STRING, false),
                            Field("json_data", DataType.STRING, true),
                        ),
                    ),
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
                    result.schema.fields.map { it.name } shouldBe listOf("id", "json_data", "extracted_name", "extracted_age")
                    result.rows.size shouldBe 2
                    result.rows[0].array shouldBe arrayOf("1", """{"user": {"name": "John", "details": {"age": 30}}}""", "John", 30)
                    result.rows[1].array shouldBe arrayOf("2", """{"user": {"name": "Jane", "details": {"age": 25}}}""", "Jane", 25)
                    true
                }.verifyComplete()
        }

        "should handle JSON arrays" {
            val df =
                DataFrame(
                    listOf(
                        Row(arrayOf("1", """{"names": ["John", "Doe"], "scores": [85, 90, 95]}""")),
                        Row(arrayOf("2", """{"names": ["Jane"], "scores": [92, 88]}""")),
                    ),
                    StructType(
                        arrayOf(
                            Field("id", DataType.STRING, false),
                            Field("json_data", DataType.STRING, true),
                        ),
                    ),
                )

            val plan =
                ActionbaseQuery.PostProcessor.JsonObject(
                    field = "json_data",
                    paths =
                        listOf(
                            ActionbaseQuery.PostProcessor.JsonObject.Path("names", "extracted_names", DataType.STRING),
                            ActionbaseQuery.PostProcessor.JsonObject.Path("scores", "extracted_scores", DataType.STRING),
                        ),
                    drop = false,
                )

            StepVerifier
                .create(executor.postProcessJsonObject(df, plan))
                .expectNextMatches { result ->
                    result.schema.fields.map { it.name } shouldBe listOf("id", "json_data", "extracted_names", "extracted_scores")
                    result.rows.size shouldBe 2
                    result.rows[0].array shouldBe arrayOf("1", """{"names": ["John", "Doe"], "scores": [85, 90, 95]}""", """["John","Doe"]""", """[85,90,95]""")
                    result.rows[1].array shouldBe arrayOf("2", """{"names": ["Jane"], "scores": [92, 88]}""", """["Jane"]""", """[92,88]""")
                    true
                }.verifyComplete()
        }

        "should handle empty JSON objects" {
            val df =
                DataFrame(
                    listOf(
                        Row(arrayOf("1", "{}")),
                        Row(arrayOf("2", "{}")),
                    ),
                    StructType(
                        arrayOf(
                            Field("id", DataType.STRING, false),
                            Field("json_data", DataType.STRING, true),
                        ),
                    ),
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
                    result.rows.size shouldBe 2
                    result.rows[0].array shouldBe arrayOf("1", "{}", null, null)
                    result.rows[1].array shouldBe arrayOf("2", "{}", null, null)
                    true
                }.verifyComplete()
        }
    })
