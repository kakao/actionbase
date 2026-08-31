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
import com.kakao.actionbase.engine.sql.calcite.SqlTransform

import java.util.concurrent.atomic.AtomicReference

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import reactor.test.StepVerifier

/**
 * A SQL transform blocks the thread it runs on: an unseen statement is parsed, planned and given its
 * sessions on the spot, and a known one waits when every session is busy — both measured by
 * `TransformSchedulerProbe`. On a WebFlux request that thread is an event loop, so the executor owes
 * the work a scheduler that may block.
 */
class ActionbaseQueryExecutorTransformSchedulerSpec :
    StringSpec({

        val engine =
            object : QueryEngine {
                override fun getTableBinding(
                    database: String,
                    alias: String,
                ): TableBinding = throw NotImplementedError()
            }

        val schema = StructType(listOf(StructField("target", PrimitiveType.STRING, "", false)))
        val shaped = DataFrame(listOf(Row(mapOf("target" to "item1"), schema)), schema, total = 1L)

        "a SQL transform runs off the thread that subscribed" {
            val ranOn = AtomicReference<String>()
            val transforms =
                mockk<SqlTransform> {
                    every { run(any(), any(), any()) } answers {
                        ranOn.set(Thread.currentThread().name)
                        shaped
                    }
                }

            val query =
                ActionbaseQuery(
                    fetch = emptyList(),
                    transform = listOf(ActionbaseQuery.Transform.Sql(name = "shaped", sql = "SELECT 1")),
                )

            val subscriber = Thread.currentThread().name

            StepVerifier
                .create(ActionbaseQueryExecutor(engine, transforms).query(query))
                .assertNext { it.keys shouldBe setOf("shaped") }
                .verifyComplete()

            ranOn.get() shouldNotBe subscriber
            ranOn.get() shouldContain "boundedElastic"
        }
    })
