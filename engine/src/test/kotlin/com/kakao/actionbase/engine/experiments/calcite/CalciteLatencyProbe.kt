package com.kakao.actionbase.engine.experiments.calcite

import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.core.metadata.common.StructType
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.engine.sql.DataFrame
import com.kakao.actionbase.engine.sql.Row
import com.kakao.actionbase.engine.sql.calcite.SqlTransform

import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.Properties

import org.apache.calcite.DataContext
import org.apache.calcite.jdbc.CalciteConnection
import org.apache.calcite.linq4j.Enumerable
import org.apache.calcite.linq4j.Linq4j
import org.apache.calcite.rel.type.RelDataType
import org.apache.calcite.rel.type.RelDataTypeFactory
import org.apache.calcite.schema.ScannableTable
import org.apache.calcite.schema.impl.AbstractTable
import org.apache.calcite.sql.type.SqlTypeName
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Measures where the time goes when a `transform` step is a SQL string: connection setup, parse,
 * validate, plan, code generation, and only then the row work.
 *
 * Numbers from a 14-core M-series laptop, JDK 17, Calcite 1.40, with
 * `calcite.bindable.cache.maxSize=1000` as this module sets it:
 *
 * ```
 *                                             100 x 70    1000 x 700
 * A. first query ever (cold JVM)             631,760 us     7,015 us
 * B. new connection + executeQuery per call    6,739 us     4,174 us
 * C. shared connection, executeQuery per call  3,623 us     2,965 us
 * D. prepared once, frames swapped per call       77 us       598 us
 * E. SqlTransform.run (first call, prepares)   7,099 us     5,108 us
 * F. SqlTransform.run (statement cached)          84 us       679 us
 * G. PreparedTransform.execute                    67 us       667 us
 * H. hand-written join + project + sort           14 us       113 us
 * ```
 *
 * B and C barely differ from each other, and neither moves when the frames grow tenfold: what repeats
 * per request is parse, validate and plan, not row work, and the JDBC driver caches no plan between
 * statements. With `calcite.bindable.cache.maxSize` left at Calcite's default of 0, both are roughly
 * 4 ms slower still, because every execution recompiles the generated class with Janino.
 *
 * E is what the first request for an unseen statement pays, and the reason a registered query should
 * prepare and warm up when it is registered rather than when it is first asked for.
 *
 * G is the shipped path. It costs about five times H, the same join written out as one pass — 53 us
 * more at the 100 rows a `TOPK` step returns, against a 5 ms query budget. The multiple holds as the
 * frames grow, so a transform over thousands of rows is where this stops being free.
 */
@Disabled("Measures Calcite's own planning cost rather than actionbase logic; run it by hand when the numbers matter.")
class CalciteLatencyProbe {
    @Test
    fun `where the milliseconds go`() {
        listOf(100, 1_000).forEach { rowCount ->
            val hop1 = ranked(rowCount)
            val hop2 = paid(rowCount * 7 / 10)
            println("\n##### hop1=${hop1.rows.size} rows, hop2=${hop2.rows.size} rows #####")

            // A. The very first query this JVM runs: driver load, schema build, parse, plan, Janino.
            val cold = timeOnce { runSql(mapOf("hop1" to hop1, "hop2" to hop2), SQL) }
            report("A. first query ever (cold JVM)", listOf(cold))

            // B. What a naive request path costs: a connection per request, so nothing is reused.
            report(
                "B. new connection + executeQuery per call",
                measure(30) { runSql(mapOf("hop1" to hop1, "hop2" to hop2), SQL) },
            )

            // C. Connection reused, statement not. Does Calcite remember the plan for this SQL?
            connection().use { connection ->
                register(connection, hop1, hop2)
                report("C. shared connection, executeQuery per call", measure(30) { drain(connection.createStatement().executeQuery(SQL)) })
            }

            // D. Plan built once, data swapped per call: what a prepared transform would cost.
            connection().use { connection ->
                val tables = register(connection, hop1, hop2)
                val statement = connection.prepareStatement(SQL)
                report("D. prepared once, frames swapped per call", measure(200) { execute(statement, tables, hop1, hop2) })
            }

            // E. What the engine actually ships: SqlTransform, plan cached, frames per execution.
            val transform = SqlTransform()
            report("E. SqlTransform.run (first call, plans)", listOf(timeOnce { transform.run(mapOf("hop1" to hop1, "hop2" to hop2), SQL) }))
            report("F. SqlTransform.run (plan cached)", measure(200) { transform.run(mapOf("hop1" to hop1, "hop2" to hop2), SQL) })

            val prepared = transform.prepare(mapOf("hop1" to hop1.schema, "hop2" to hop2.schema), SQL)
            prepared.warmUp(2)
            report("G. PreparedTransform.execute", measure(200) { prepared.execute(mapOf("hop1" to hop1, "hop2" to hop2)) })
            transform.close()

            // H. The same left join, projection and sort written by hand, for scale.
            report("H. hand-written join + project + sort", measure(200) { byHand(hop1, hop2).rows.size })
            // The baseline has to answer the same thing the SQL does, or the comparison is empty.
            val fromSql = SqlTransform().use { it.run(mapOf("hop1" to hop1, "hop2" to hop2), SQL) }
            val expected = byHand(hop1, hop2)
            check(fromSql.rows.map { it.data } == expected.rows.map { it.data } && fromSql.schema == expected.schema) {
                "baseline disagrees with SQL:\n${fromSql.schema}\n${expected.schema}\n${fromSql.rows.take(3).map { it.data }}\n${expected.rows.take(3).map { it.data }}"
            }
        }
    }

    @Test
    fun `a statement prepared once sees the frames that replaced the ones it was planned against`() {
        connection().use { connection ->
            val tables = register(connection, ranked(3), paid(2))
            val statement = connection.prepareStatement(SQL)

            val first = execute(statement, tables, ranked(3), paid(2))
            val second = execute(statement, tables, ranked(7), paid(5))

            println("prepared once: $first rows, then $second rows after swapping frames")
            check(first == 3 && second == 7) { "The plan is serving stale rows: got $first then $second." }
        }
    }

    /**
     * The baseline the SQL path is measured against: the same left join, projection and sort as [SQL],
     * written out. `DataFrame` carries no join operator on this branch, so it is spelled here rather
     * than borrowed.
     *
     * It builds the same `DataFrame` the SQL path returns, rows and schema included. A version that
     * stops at arrays measures an eighth of the time and answers a different question.
     */
    private fun byHand(
        hop1: DataFrame,
        hop2: DataFrame,
    ): DataFrame {
        val paidByKey = hop2.rows.associateBy({ it.data["source"] to it.data["target"] }, { it.data["paidAt"] })
        val rows =
            hop1.rows
                .map { row ->
                    val paidAt = paidByKey[row.data["source"] to row.data["target"]] ?: -1L
                    Row(mapOf("productGroupId" to row.data["target"], "metric" to row.data["metric"], "paidAt" to paidAt), joinedSchema)
                }.sortedWith(compareByDescending { it.data["metric"] as Long })

        return DataFrame(rows, joinedSchema, total = rows.size.toLong())
    }

    /** The naive path: a connection and a statement per call, nothing reused. */
    private fun runSql(
        frames: Map<String, DataFrame>,
        sql: String,
    ): Int =
        connection().use { connection ->
            val rootSchema = connection.unwrap(CalciteConnection::class.java).rootSchema
            frames.forEach { (name, frame) -> rootSchema.add(name, SwappableTable(frame)) }

            connection.createStatement().use { statement -> drain(statement.executeQuery(sql)) }
        }

    private fun register(
        connection: Connection,
        hop1: DataFrame,
        hop2: DataFrame,
    ): List<SwappableTable> {
        val tables = listOf(SwappableTable(hop1), SwappableTable(hop2))
        val rootSchema = connection.unwrap(CalciteConnection::class.java).rootSchema
        rootSchema.add("hop1", tables[0])
        rootSchema.add("hop2", tables[1])
        return tables
    }

    private fun execute(
        statement: PreparedStatement,
        tables: List<SwappableTable>,
        hop1: DataFrame,
        hop2: DataFrame,
    ): Int {
        tables[0].frame = hop1
        tables[1].frame = hop2
        return drain(statement.executeQuery())
    }

    private fun drain(resultSet: ResultSet): Int {
        var rows = 0
        resultSet.use {
            while (it.next()) {
                it.getObject(1)
                rows++
            }
        }
        return rows
    }

    private fun measure(
        iterations: Int,
        block: () -> Any?,
    ): List<Long> {
        repeat(iterations / 3) { block() }
        return (1..iterations).map { timeOnce(block) }
    }

    private fun timeOnce(block: () -> Any?): Long {
        val start = System.nanoTime()
        block()
        return System.nanoTime() - start
    }

    private fun report(
        label: String,
        samples: List<Long>,
    ) {
        val sorted = samples.sorted()
        val micros = { nanos: Long -> "%,.0f".format(nanos / 1_000.0) }
        println(
            "%-44s p50=%9s us  min=%9s us  max=%9s us  (n=%d)".format(
                label,
                micros(sorted[sorted.size / 2]),
                micros(sorted.first()),
                micros(sorted.last()),
                samples.size,
            ),
        )
    }

    /** A table whose schema is fixed but whose rows are replaced between executions. */
    private class SwappableTable(
        var frame: DataFrame,
    ) : AbstractTable(),
        ScannableTable {
        private val fields = frame.schema.fields

        override fun getRowType(typeFactory: RelDataTypeFactory): RelDataType =
            typeFactory
                .builder()
                .also { builder ->
                    fields.forEach { field ->
                        val sqlType = if (field.type == PrimitiveType.STRING) SqlTypeName.VARCHAR else SqlTypeName.BIGINT
                        builder.add(field.name, typeFactory.createTypeWithNullability(typeFactory.createSqlType(sqlType), field.nullable))
                    }
                }.build()

        override fun scan(root: DataContext): Enumerable<Array<Any?>> = Linq4j.asEnumerable(frame.rows.map { row -> Array<Any?>(fields.size) { i -> row.data[fields[i].name] } })
    }

    private companion object {
        val SQL =
            """
            SELECT hop1.target AS productGroupId, hop1.metric AS metric, IFNULL(hop2.paidAt, -1) AS paidAt
            FROM      hop1
            LEFT JOIN hop2 ON hop1.source = hop2.source AND hop1.target = hop2.target
            ORDER BY  metric DESC, paidAt DESC
            """.trimIndent()

        val joinedSchema =
            StructType(
                listOf(
                    StructField("productGroupId", PrimitiveType.STRING, "", false),
                    StructField("metric", PrimitiveType.LONG, "", false),
                    StructField("paidAt", PrimitiveType.LONG, "", false),
                ),
            )

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

        fun ranked(rowCount: Int): DataFrame =
            DataFrame(
                (1..rowCount).map { i -> Row(mapOf("source" to "U1", "target" to "PG$i", "metric" to (rowCount - i).toLong()), rankedSchema) },
                rankedSchema,
                total = rowCount.toLong(),
            )

        fun paid(rowCount: Int): DataFrame =
            DataFrame(
                (1..rowCount).map { i -> Row(mapOf("source" to "U1", "target" to "PG$i", "paidAt" to 1_700_000_000L - i), paidSchema) },
                paidSchema,
                total = rowCount.toLong(),
            )

        fun connection(): Connection =
            DriverManager.getConnection(
                "jdbc:calcite:",
                Properties().apply {
                    setProperty("lex", "JAVA")
                    setProperty("fun", "all")
                },
            )
    }
}
