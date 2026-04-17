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

class ActionbaseQueryExecutorPostProcessSplitExplodeSpec :
    StringSpec({

        val engine =
            object : QueryEngine {
                override fun getTableBinding(
                    database: String,
                    alias: String,
                ): TableBinding = throw NotImplementedError()
            }
        val executor = ActionbaseQueryExecutor(engine)

        val idFruitsSchema =
            StructType(
                listOf(
                    StructField("id", PrimitiveType.STRING, "", false),
                    StructField("fruits", PrimitiveType.STRING, "", true),
                ),
            )

        fun idFruitsDataFrame(vararg entries: Pair<String, String>): DataFrame =
            DataFrame(
                entries.map { (id, fruits) -> Row(mapOf("id" to id, "fruits" to fruits), idFruitsSchema) },
                idFruitsSchema,
                total = entries.size.toLong(),
            )

        "should split and explode a string field without dropping the original field" {
            val df =
                idFruitsDataFrame(
                    "1" to "apple,banana,cherry",
                    "2" to "dog,cat",
                    "3" to "",
                )

            val plan =
                ActionbaseQuery.PostProcessor.SplitExplode(
                    field = "fruits",
                    regex = ",",
                    limit = 0,
                    alias = "fruit",
                    dataType = DataType.STRING,
                    drop = false,
                )

            StepVerifier
                .create(executor.postProcessorSplitExplode(df, plan))
                .expectNextMatches { result ->
                    result.schema.fields.map { it.name } shouldBe listOf("id", "fruits", "fruit")
                    result.rows.size shouldBe 5
                    result.rows[0].data shouldBe mapOf("id" to "1", "fruits" to "apple,banana,cherry", "fruit" to "apple")
                    result.rows[1].data shouldBe mapOf("id" to "1", "fruits" to "apple,banana,cherry", "fruit" to "banana")
                    result.rows[2].data shouldBe mapOf("id" to "1", "fruits" to "apple,banana,cherry", "fruit" to "cherry")
                    result.rows[3].data shouldBe mapOf("id" to "2", "fruits" to "dog,cat", "fruit" to "dog")
                    result.rows[4].data shouldBe mapOf("id" to "2", "fruits" to "dog,cat", "fruit" to "cat")
                    true
                }.verifyComplete()
        }

        "should split and explode a string field and drop the original field" {
            val df =
                idFruitsDataFrame(
                    "1" to "apple,banana,cherry",
                    "2" to "dog,cat",
                    "3" to "",
                )

            val plan =
                ActionbaseQuery.PostProcessor.SplitExplode(
                    field = "fruits",
                    regex = ",",
                    limit = 0,
                    alias = "fruit",
                    dataType = DataType.STRING,
                    drop = true,
                )

            StepVerifier
                .create(executor.postProcessorSplitExplode(df, plan))
                .expectNextMatches { result ->
                    result.schema.fields.map { it.name } shouldBe listOf("id", "fruit")
                    result.rows.size shouldBe 5
                    result.rows[0].data shouldBe mapOf("id" to "1", "fruit" to "apple")
                    result.rows[1].data shouldBe mapOf("id" to "1", "fruit" to "banana")
                    result.rows[2].data shouldBe mapOf("id" to "1", "fruit" to "cherry")
                    result.rows[3].data shouldBe mapOf("id" to "2", "fruit" to "dog")
                    result.rows[4].data shouldBe mapOf("id" to "2", "fruit" to "cat")
                    true
                }.verifyComplete()
        }
    })
