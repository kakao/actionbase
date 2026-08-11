package com.kakao.actionbase.engine.sql

import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.core.metadata.common.StructType
import com.kakao.actionbase.core.types.PrimitiveType

import org.junit.jupiter.api.Test

import io.kotest.matchers.shouldBe

class DataFrameShowTest {
    @Test
    fun `renders types under the column names and right-aligns numbers`() {
        val frame =
            DataFrame(
                listOf(
                    Row(mapOf("target" to "PG1", "metric" to 30L), schema),
                    Row(mapOf("target" to "PRODUCT_GROUP_2", "metric" to 1_700L), schema),
                ),
                schema,
                total = 2L,
            )

        frame.render("hop1") shouldBe
            """
            === hop1 === 2 of 2 rows
            +-----------------+--------+
            | target          | metric |
            | STRING          |  LONG? |
            +-----------------+--------+
            | PG1             |     30 |
            | PRODUCT_GROUP_2 |   1700 |
            +-----------------+--------+

            """.trimIndent()
    }

    @Test
    fun `renders a missing value as NULL, aligned the way its column is`() {
        val frame = DataFrame(listOf(Row(mapOf("target" to "PG1"), schema)), schema, total = 1L)

        frame.render("hop1").lines()[5] shouldBe "| PG1    |   NULL |"
    }

    @Test
    fun `an empty frame has nothing to tabulate`() {
        DataFrame.empty.render("hop1") shouldBe "=== hop1 === 0 of 0 rows\n(no columns)\n"
    }

    private companion object {
        val schema =
            StructType(
                listOf(
                    StructField("target", PrimitiveType.STRING, "", false),
                    StructField("metric", PrimitiveType.LONG, "", true),
                ),
            )
    }
}
