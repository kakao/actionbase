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

class ActionbaseQueryExecutorPostProcessSplitExplodeSpec :
    StringSpec({

        val labelProvider =
            object : LabelProvider {
                override fun getLabel(name: EntityName): Label = throw NotImplementedError()
            }
        val executor = ActionbaseQueryExecutor(labelProvider)

        "should split and explode a string field without dropping the original field" {
            val df =
                DataFrame(
                    listOf(
                        Row(arrayOf("1", "apple,banana,cherry")),
                        Row(arrayOf("2", "dog,cat")),
                        Row(arrayOf("3", "")),
                    ),
                    StructType(
                        arrayOf(
                            Field("id", DataType.STRING, false),
                            Field("fruits", DataType.STRING, true),
                        ),
                    ),
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
                    result.rows[0].array shouldBe arrayOf("1", "apple,banana,cherry", "apple")
                    result.rows[1].array shouldBe arrayOf("1", "apple,banana,cherry", "banana")
                    result.rows[2].array shouldBe arrayOf("1", "apple,banana,cherry", "cherry")
                    result.rows[3].array shouldBe arrayOf("2", "dog,cat", "dog")
                    result.rows[4].array shouldBe arrayOf("2", "dog,cat", "cat")
                    true
                }.verifyComplete()
        }

        "should split and explode a string field and drop the original field" {
            val df =
                DataFrame(
                    listOf(
                        Row(arrayOf("1", "apple,banana,cherry")),
                        Row(arrayOf("2", "dog,cat")),
                        Row(arrayOf("3", "")),
                    ),
                    StructType(
                        arrayOf(
                            Field("id", DataType.STRING, false),
                            Field("fruits", DataType.STRING, true),
                        ),
                    ),
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
                    result.rows[0].array shouldBe arrayOf("1", "apple")
                    result.rows[1].array shouldBe arrayOf("1", "banana")
                    result.rows[2].array shouldBe arrayOf("1", "cherry")
                    result.rows[3].array shouldBe arrayOf("2", "dog")
                    result.rows[4].array shouldBe arrayOf("2", "cat")
                    true
                }.verifyComplete()
        }
    })
