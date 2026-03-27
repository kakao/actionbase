package com.kakao.actionbase.v2.engine.v3.query

import com.kakao.actionbase.engine.query.ActionbaseQuery
import com.kakao.actionbase.engine.query.ActionbaseQueryExecutor
import com.kakao.actionbase.engine.query.QueryBinding
import com.kakao.actionbase.engine.query.QueryScanFilter
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.core.types.DataType
import com.kakao.actionbase.v2.core.types.Field
import com.kakao.actionbase.v2.core.types.StructType
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.Row
import com.kakao.actionbase.v2.engine.sql.StatKey

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class ActionbaseQueryExecutorPostProcessSplitExplodeSpec :
    StringSpec({

        val queryBinding =
            object : QueryBinding {
                override fun getSelf(
                    database: String,
                    table: String,
                    src: List<Any>,
                    stats: Set<StatKey>,
                ): Mono<DataFrame> = throw NotImplementedError()

                override fun get(
                    database: String,
                    table: String,
                    src: List<Any>,
                    tgt: List<Any>,
                    stats: Set<StatKey>,
                ): Mono<DataFrame> = throw NotImplementedError()

                override fun count(
                    database: String,
                    table: String,
                    src: Set<Any>,
                    direction: Direction,
                ): Mono<DataFrame> = throw NotImplementedError()

                override fun scan(
                    database: String,
                    table: String,
                    filter: QueryScanFilter,
                    stats: Set<StatKey>,
                ): Mono<DataFrame> = throw NotImplementedError()
            }
        val executor = ActionbaseQueryExecutor(queryBinding)

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
