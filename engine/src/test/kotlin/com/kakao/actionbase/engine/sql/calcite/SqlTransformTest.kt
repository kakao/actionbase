package com.kakao.actionbase.engine.sql.calcite

import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.core.metadata.common.StructType
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.engine.sql.DataFrame
import com.kakao.actionbase.engine.sql.Row

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers

class SqlTransformTest {
    private val transform = SqlTransform()

    @AfterEach
    fun close() {
        transform.close()
    }

    @Test
    fun `a left join with a null fallback keeps a ranked group that was never paid for`() {
        val result = transform.run(mapOf("hop1" to hop1, "hop2" to hop2), LEFT_JOIN_SQL)

        result.schema.fields.map { it.name } shouldBe listOf("productGroupId", "metric", "paidAt")
        result.schema.getField("metric").type shouldBe PrimitiveType.LONG
        result.rows.map { listOf(it.data["productGroupId"], it.data["metric"], it.data["paidAt"]) } shouldBe
            listOf(
                listOf("PG1", 30L, 1_700_000_000L),
                listOf("PG2", 20L, -1L),
                listOf("PG3", 10L, 1_600_000_000L),
            )
    }

    @Test
    fun `a single-column result comes back as a column`() {
        val sql = "SELECT hop1.target AS productGroupId FROM hop1 ORDER BY hop1.metric DESC"

        transform.run(mapOf("hop1" to hop1), sql).getColumn("productGroupId") shouldBe listOf("PG1", "PG2", "PG3")
    }

    @Test
    fun `an anti-join excludes what an earlier step already returned`() {
        val sql =
            """
            SELECT hop1.target AS productGroupId
            FROM hop1
            WHERE NOT EXISTS (SELECT 1 FROM hop0 WHERE hop0.target = hop1.target)
            ORDER BY hop1.metric DESC
            """.trimIndent()

        transform.run(mapOf("hop0" to ranked("PG1" to 99L), "hop1" to hop1), sql).getColumn("productGroupId") shouldBe listOf("PG2", "PG3")
    }

    @Test
    fun `the output schema is known before any row is read`() {
        val prepared = transform.prepare(mapOf("hop1" to rankedSchema, "hop2" to paidSchema), LEFT_JOIN_SQL)

        prepared.inputs shouldBe setOf("hop1", "hop2")
        prepared.parameterCount shouldBe 0
        prepared.schema.fields.map { it.name to it.type } shouldBe
            listOf("productGroupId" to PrimitiveType.STRING, "metric" to PrimitiveType.LONG, "paidAt" to PrimitiveType.LONG)
    }

    @Test
    fun `a placeholder is bound per execution rather than baked into the plan`() {
        val sql = "SELECT hop1.target AS productGroupId FROM hop1 WHERE hop1.metric >= ? ORDER BY hop1.metric DESC"
        val prepared = transform.prepare(mapOf("hop1" to rankedSchema), sql)

        prepared.parameterCount shouldBe 1
        prepared.execute(mapOf("hop1" to hop1), listOf(20L)).getColumn("productGroupId") shouldBe listOf("PG1", "PG2")
        prepared.execute(mapOf("hop1" to hop1), listOf(30L)).getColumn("productGroupId") shouldBe listOf("PG1")
        transform.transformCount shouldBe 1L
    }

    @Test
    fun `the wrong number of arguments is refused before the statement runs`() {
        val prepared = transform.prepare(mapOf("hop1" to rankedSchema), "SELECT hop1.target AS t FROM hop1 WHERE hop1.metric >= ?")

        shouldThrow<IllegalArgumentException> { prepared.execute(mapOf("hop1" to hop1)) }
            .message shouldBe "This transform takes 1 arguments, got 0."
    }

    @Test
    fun `the same sql over the same schemas is prepared once`() {
        transform.run(mapOf("hop1" to hop1, "hop2" to hop2), LEFT_JOIN_SQL)
        transform.run(mapOf("hop1" to ranked("PG9" to 1L), "hop2" to paid("PG9" to 5L)), LEFT_JOIN_SQL)

        transform.transformCount shouldBe 1L
    }

    @Test
    fun `the same sql over different schemas is prepared separately`() {
        val renamed = StructType(rankedSchema.fields.map { if (it.name == "metric") it.copy(name = "score") else it })
        val sql = "SELECT hop1.target AS productGroupId FROM hop1"

        transform.prepare(mapOf("hop1" to rankedSchema), sql)
        transform.prepare(mapOf("hop1" to renamed), sql)

        transform.transformCount shouldBe 2L
    }

    /**
     * The shape a WebFlux request path has: many executions in flight at once, none of them pinned to
     * the thread it started on. Each has to see its own rows.
     *
     * Warmed up first and run at exactly the pool size, which is the contract a non-blocking scheduler
     * needs: no session is opened and none is waited for while a request is on the thread.
     */
    @Test
    fun `concurrent executions of one prepared transform do not mix their frames`() {
        val prepared = transform.prepare(mapOf("hop1" to rankedSchema), "SELECT hop1.target AS productGroupId FROM hop1 ORDER BY hop1.metric DESC")
        prepared.sessionCount shouldBe PreparedTransform.DEFAULT_MAXIMUM_SESSIONS

        val mixed =
            Flux
                .range(1, 400)
                .parallel(PreparedTransform.DEFAULT_MAXIMUM_SESSIONS)
                .runOn(Schedulers.parallel())
                .map { task ->
                    val frame = ranked(*(1..task % 7 + 1).map { "T$task-$it" to it.toLong() }.toTypedArray())
                    prepared.execute(mapOf("hop1" to frame)).getColumn("productGroupId").toSet() != frame.getColumn("target").toSet()
                }.sequential()
                .filter { it }
                .count()
                .block()

        mixed shouldBe 0L
    }

    @Test
    fun `a transform executed with a frame it was not prepared for is refused`() {
        val prepared = transform.prepare(mapOf("hop1" to rankedSchema), "SELECT hop1.target AS productGroupId FROM hop1")

        shouldThrow<IllegalArgumentException> { prepared.execute(mapOf("hop1" to paid("PG1" to 1L))) }
            .message shouldBe "`hop1` was prepared for [source, target, metric] but was given [source, target, paidAt]."
    }

    @Test
    fun `executing without one of the prepared frames says which one is missing`() {
        val prepared = transform.prepare(mapOf("hop1" to rankedSchema, "hop2" to paidSchema), LEFT_JOIN_SQL)

        shouldThrow<IllegalArgumentException> { prepared.execute(mapOf("hop1" to hop1)) }
            .message shouldBe "This transform reads hop1, hop2; the execution is missing hop2."
    }

    @Test
    fun `preparing fills the pool so no execution has to open a session`() {
        val prepared = transform.prepare(mapOf("hop1" to rankedSchema), "SELECT hop1.target AS productGroupId FROM hop1")

        prepared.sessionCount shouldBe PreparedTransform.DEFAULT_MAXIMUM_SESSIONS
    }

    @Test
    fun `a transform not yet prepared is reported as absent rather than prepared on the spot`() {
        val schemas = mapOf("hop1" to rankedSchema)
        val sql = "SELECT hop1.target AS productGroupId FROM hop1"

        transform.prepared(schemas, sql) shouldBe null
        transform.transformCount shouldBe 0L

        val prepared = transform.prepare(schemas, sql)

        transform.prepared(schemas, sql) shouldBe prepared
    }

    @Test
    fun `a closed transform refuses to execute`() {
        val prepared = transform.prepare(mapOf("hop1" to rankedSchema), "SELECT hop1.target AS productGroupId FROM hop1")
        transform.close()

        shouldThrow<IllegalStateException> { prepared.execute(mapOf("hop1" to hop1)) }
    }

    private companion object {
        val LEFT_JOIN_SQL =
            """
            SELECT hop1.target AS productGroupId, hop1.metric AS metric, IFNULL(hop2.paidAt, -1) AS paidAt
            FROM      hop1
            LEFT JOIN hop2 ON hop1.source = hop2.source AND hop1.target = hop2.target
            ORDER BY  metric DESC, paidAt DESC
            """.trimIndent()

        val rankedSchema =
            StructType(
                listOf(
                    StructField("source", PrimitiveType.STRING, "", false),
                    StructField("target", PrimitiveType.STRING, "", false),
                    StructField("metric", PrimitiveType.LONG, "", false),
                ),
            )

        val paidSchema =
            StructType(
                listOf(
                    StructField("source", PrimitiveType.STRING, "", false),
                    StructField("target", PrimitiveType.STRING, "", false),
                    StructField("paidAt", PrimitiveType.LONG, "", false),
                ),
            )

        fun ranked(vararg entries: Pair<String, Long>): DataFrame = frame(rankedSchema, "metric", *entries)

        fun paid(vararg entries: Pair<String, Long>): DataFrame = frame(paidSchema, "paidAt", *entries)

        private fun frame(
            schema: StructType,
            valueField: String,
            vararg entries: Pair<String, Long>,
        ): DataFrame =
            DataFrame(
                entries.map { (target, value) -> Row(mapOf("source" to "U1", "target" to target, valueField to value), schema) },
                schema,
                total = entries.size.toLong(),
            )

        val hop1: DataFrame = ranked("PG1" to 30L, "PG2" to 20L, "PG3" to 10L)

        val hop2: DataFrame = paid("PG1" to 1_700_000_000L, "PG3" to 1_600_000_000L)
    }
}
