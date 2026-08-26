package com.kakao.actionbase.engine.experiments.calcite

import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.core.metadata.common.StructType
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.engine.sql.DataFrame
import com.kakao.actionbase.engine.sql.Row
import com.kakao.actionbase.engine.sql.calcite.SqlTransform
import com.kakao.actionbase.engine.sql.show

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

import io.kotest.matchers.shouldBe

/**
 * Onboarding walkthrough for running a `transform` step as SQL.
 *
 * The `fetch` half is faked: each hop is a hand-built [DataFrame], the same flat shape a `TOPK` or
 * `GET` step hands back today (`source`, `target`, then the edge properties). Only the `transform`
 * half is real — the frames are registered as Calcite tables under their fetch names and a SQL
 * string is executed against them.
 *
 * The query being modelled, for entity `U1`:
 *
 * ```
 * fetch  hop1: TOPK market_order_collection_v1 / top_product_groups_1y  -> ranked product groups
 * fetch  hop2: GET  market_order_product_group_v1                      -> when each was last paid
 * transform result: SQL
 * ```
 *
 * Note the property namespace. The design sketch writes `hop1.properties.metric`, but a fetched
 * frame is flat today, so the SQL below says `hop1.metric`. Adding a nested row form to
 * [DataFrame] is what would make the dotted form work.
 */
class DataFrameSqlTransformTest {
    private val transform = SqlTransform()

    @AfterEach
    fun close() {
        transform.close()
    }

    @Test
    fun `left join with a null fallback keeps a ranked group that was never paid for`() {
        val sql =
            """
            SELECT hop1.target             AS productGroupId,
                   hop1.metric             AS metric,
                   IFNULL(hop2.paidAt, -1) AS paidAt
            FROM      hop1
            LEFT JOIN hop2 ON hop1.source = hop2.source AND hop1.target = hop2.target
            ORDER BY  metric DESC, paidAt DESC
            """.trimIndent()

        val result = runAndShow(mapOf("hop1" to hop1, "hop2" to hop2), sql)

        result.schema.fields.map { it.name } shouldBe listOf("productGroupId", "metric", "paidAt")
        result.schema.getField("metric").type shouldBe PrimitiveType.LONG
        // PG2 is ranked but never paid for, so it survives the join and falls back to -1.
        result.rows.map { listOf(it.data["productGroupId"], it.data["metric"], it.data["paidAt"]) } shouldBe
            listOf(
                listOf("PG1", 30L, 1_700_000_000L),
                listOf("PG2", 20L, -1L),
                listOf("PG3", 10L, 1_600_000_000L),
            )
    }

    @Test
    fun `an inner join loses that group, which is why the join has to be a left join`() {
        val sql =
            """
            SELECT hop1.target AS productGroupId
            FROM hop1 JOIN hop2 ON hop1.source = hop2.source AND hop1.target = hop2.target
            ORDER BY hop1.metric DESC
            """.trimIndent()

        runAndShow(mapOf("hop1" to hop1, "hop2" to hop2), sql).getColumn("productGroupId") shouldBe listOf("PG1", "PG3")
    }

    @Test
    fun `an anti-join excludes what an earlier step already returned`() {
        // The third step of "what I saw -> who else saw it -> what they saw" has to drop step one's
        // rows from its own. Every step is in memory, so the exclusion is just a NOT EXISTS.
        val sql =
            """
            SELECT hop1.target AS productGroupId
            FROM hop1
            WHERE NOT EXISTS (SELECT 1 FROM hop0 WHERE hop0.target = hop1.target)
            ORDER BY hop1.metric DESC
            """.trimIndent()

        val alreadySeen = ranked("U1" to "PG1" to 99L)

        runAndShow(mapOf("hop0" to alreadySeen, "hop1" to hop1), sql).getColumn("productGroupId") shouldBe listOf("PG2", "PG3")
    }

    @Test
    fun `sorting and limiting happen in SQL rather than in an aggregator`() {
        val sql =
            """
            SELECT hop1.target AS productGroupId, hop1.metric AS metric
            FROM hop1
            ORDER BY hop1.metric ASC
            LIMIT 2
            """.trimIndent()

        runAndShow(mapOf("hop1" to hop1), sql).rows.map { it.data["productGroupId"] } shouldBe listOf("PG3", "PG2")
    }

    /**
     * Prints what goes in, the SQL, and what comes back. The result is a [DataFrame] again, so the
     * input frames and the output frame render through the same `show`.
     */
    private fun runAndShow(
        frames: Map<String, DataFrame>,
        sql: String,
    ): DataFrame {
        frames.forEach { (name, frame) -> frame.show("in: $name") }
        println("--- transform SQL ---\n$sql\n")
        return transform.run(frames, sql).show("out: result")
    }

    private companion object {
        /** What a `TOPK` fetch returns: one row per ranked product group, carrying its rank metric. */
        val rankedSchema =
            StructType(
                listOf(
                    StructField("source", PrimitiveType.STRING, "", false),
                    StructField("target", PrimitiveType.STRING, "", false),
                    StructField("metric", PrimitiveType.LONG, "", false),
                ),
            )

        /** What the `GET` fetch returns: the last payment time, for the pairs that have one. */
        val paidSchema =
            StructType(
                listOf(
                    StructField("source", PrimitiveType.STRING, "", false),
                    StructField("target", PrimitiveType.STRING, "", false),
                    StructField("paidAt", PrimitiveType.LONG, "", false),
                ),
            )

        fun ranked(vararg entries: Pair<Pair<String, String>, Long>): DataFrame = frame(rankedSchema, "metric", *entries)

        fun paid(vararg entries: Pair<Pair<String, String>, Long>): DataFrame = frame(paidSchema, "paidAt", *entries)

        private fun frame(
            schema: StructType,
            valueField: String,
            vararg entries: Pair<Pair<String, String>, Long>,
        ): DataFrame =
            DataFrame(
                entries.map { (key, value) ->
                    Row(mapOf("source" to key.first, "target" to key.second, valueField to value), schema)
                },
                schema,
                total = entries.size.toLong(),
            )

        val hop1: DataFrame = ranked("U1" to "PG1" to 30L, "U1" to "PG2" to 20L, "U1" to "PG3" to 10L)

        // PG2 is missing on purpose: a product group can be bought often and yet have no recent order.
        val hop2: DataFrame = paid("U1" to "PG1" to 1_700_000_000L, "U1" to "PG3" to 1_600_000_000L)
    }
}
