package com.kakao.actionbase.engine.query

import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.core.metadata.common.StructType
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.engine.QueryEngine
import com.kakao.actionbase.engine.binding.TableBinding
import com.kakao.actionbase.engine.sql.DataFrame
import com.kakao.actionbase.engine.sql.Row

import org.junit.jupiter.api.Test

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import reactor.core.publisher.Mono

class PreparedQueryTest {
    @Test
    fun `a parameter is found wherever a VALUE vertex holds it`() {
        val prepared = PreparedQuery.of(query(source = value("{entity}")))

        prepared.parameters shouldBe setOf("entity")
    }

    @Test
    fun `a value that only contains braces is not a parameter`() {
        PreparedQuery.of(query(source = value("user-{entity}"))).parameters shouldBe emptySet()
    }

    @Test
    fun `binding replaces the placeholder and leaves the shape alone`() {
        val template = query(source = value("{entity}"))
        val prepared = PreparedQuery.of(template)

        val bound = prepared.bind(mapOf("entity" to "U1"))

        (bound.fetch.single() as ActionbaseQuery.Item.Self).source shouldBe ActionbaseQuery.Vertex.Value(listOf("U1"))
        // The template is reusable: binding it again with something else has to work.
        (prepared.bind(mapOf("entity" to "U2")).fetch.single() as ActionbaseQuery.Item.Self).source shouldBe
            ActionbaseQuery.Vertex.Value(listOf("U2"))
        template.fetch.single() shouldBe query(source = value("{entity}")).fetch.single()
    }

    @Test
    fun `an argument keeps its own type instead of becoming a string`() {
        val bound = PreparedQuery.of(query(source = value("{entity}"))).bind(mapOf("entity" to 42L))

        (bound.fetch.single() as ActionbaseQuery.Item.Self).source shouldBe ActionbaseQuery.Vertex.Value(listOf(42L))
    }

    @Test
    fun `binding without an argument says which parameter is missing`() {
        val prepared = PreparedQuery.of(query(source = value("{entity}")))

        shouldThrow<IllegalArgumentException> { prepared.bind(emptyMap()) }
            .message shouldBe "This query takes entity; the call is missing entity."
    }

    @Test
    fun `a transform reads a step the caller does not get back`() {
        val executor = ActionbaseQueryExecutor(engineReturning("hop1" to ranked("PG1" to 30L, "PG2" to 20L)))
        val prepared =
            PreparedQuery.of(
                ActionbaseQuery(
                    fetch = listOf(self(name = "hop1", source = value("{entity}"), include = false)),
                    transform =
                        listOf(
                            ActionbaseQuery.Transform.Sql(
                                name = "result",
                                sql = "SELECT hop1.target AS productGroupId FROM hop1 ORDER BY hop1.metric DESC",
                            ),
                        ),
                ),
            )

        val returned = executor.query(prepared, mapOf("entity" to "U1")).block()!!

        returned.keys shouldBe setOf("result")
        returned.getValue("result").getColumn("productGroupId") shouldBe listOf("PG1", "PG2")
    }

    @Test
    fun `a transform binds its own placeholders from the same arguments`() {
        val executor = ActionbaseQueryExecutor(engineReturning("hop1" to ranked("PG1" to 30L, "PG2" to 20L, "PG3" to 10L)))
        val prepared =
            PreparedQuery.of(
                ActionbaseQuery(
                    fetch = listOf(self(name = "hop1", source = value("{entity}"))),
                    transform =
                        listOf(
                            // The name is written where the value goes; it becomes a `?` on the way out.
                            ActionbaseQuery.Transform.Sql(
                                name = "result",
                                sql = "SELECT hop1.target AS productGroupId FROM hop1 WHERE hop1.metric >= {floor} ORDER BY hop1.metric DESC",
                            ),
                        ),
                ),
            )

        prepared.parameters shouldBe setOf("entity", "floor")
        executor
            .query(prepared, mapOf("entity" to "U1", "floor" to 20L))
            .block()!!
            .getValue("result")
            .getColumn("productGroupId") shouldBe listOf("PG1", "PG2")
        executor
            .query(prepared, mapOf("entity" to "U1", "floor" to 30L))
            .block()!!
            .getValue("result")
            .getColumn("productGroupId") shouldBe listOf("PG1")
    }

    @Test
    fun `an included step comes back alongside the transform`() {
        val executor = ActionbaseQueryExecutor(engineReturning("hop1" to ranked("PG1" to 30L)))
        val prepared =
            PreparedQuery.of(
                ActionbaseQuery(
                    fetch = listOf(self(name = "hop1", source = value("{entity}"), include = true)),
                    transform = listOf(ActionbaseQuery.Transform.Sql(name = "result", sql = "SELECT hop1.target AS t FROM hop1")),
                ),
            )

        executor.query(prepared, mapOf("entity" to "U1")).block()!!.keys shouldBe setOf("hop1", "result")
    }

    private companion object {
        val rankedSchema =
            StructType(
                listOf(
                    StructField("source", PrimitiveType.STRING, "", false),
                    StructField("target", PrimitiveType.STRING, "", false),
                    StructField("metric", PrimitiveType.LONG, "", false),
                ),
            )

        fun ranked(vararg entries: Pair<String, Long>): DataFrame =
            DataFrame(
                entries.map { (target, metric) -> Row(mapOf("source" to "U1", "target" to target, "metric" to metric), rankedSchema) },
                rankedSchema,
                total = entries.size.toLong(),
            )

        fun value(vararg values: Any): ActionbaseQuery.Vertex = ActionbaseQuery.Vertex.Value(values.toList())

        fun self(
            name: String,
            source: ActionbaseQuery.Vertex,
            include: Boolean = false,
        ): ActionbaseQuery.Item.Self = ActionbaseQuery.Item.Self(name = name, database = "market", table = "orders", source = source, include = include)

        fun query(source: ActionbaseQuery.Vertex): ActionbaseQuery = ActionbaseQuery(fetch = listOf(self(name = "hop1", source = source)))

        /** A stub engine whose every step returns [frame], so the test is about transforms, not storage. */
        fun engineReturning(frame: Pair<String, DataFrame>): QueryEngine {
            val binding = mockk<TableBinding>()
            every { binding.gets(any(), any()) } returns Mono.just(frame.second)
            // The interface has a default, but a mock answers nothing it was not told to answer.
            every { binding.emptyFrame } returns DataFrame(emptyList(), frame.second.schema, total = 0L)

            return object : QueryEngine {
                override fun getTableBinding(
                    database: String,
                    alias: String,
                ): TableBinding = binding
            }
        }
    }
}
