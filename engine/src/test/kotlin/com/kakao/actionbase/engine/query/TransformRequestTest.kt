package com.kakao.actionbase.engine.query

import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.core.metadata.common.StructType
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.engine.QueryEngine
import com.kakao.actionbase.engine.binding.TableBinding
import com.kakao.actionbase.engine.sql.DataFrame
import com.kakao.actionbase.engine.sql.Row
import com.kakao.actionbase.engine.sql.calcite.SqlTransform

import org.junit.jupiter.api.Test

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import reactor.core.publisher.Mono

/**
 * The request shape as it is actually sent: `fetch` steps, a `transform` holding SQL, and `{entity}`
 * filled in per call.
 */
class TransformRequestTest {
    @Test
    fun `the request parses`() {
        val query = ActionbaseQuery.from(REQUEST)

        query.fetch.map { it.name } shouldBe listOf("hop1", "hop2")
        query.transform.map { it.name } shouldBe listOf("result")
        PreparedQuery.of(query).parameters shouldBe setOf("entity")
    }

    @Test
    fun `a raw newline inside a json string is rejected by json itself`() {
        val broken = "{\"fetch\": [], \"transform\": [{\"type\":\"SQL\",\"name\":\"r\",\"sql\":\"SELECT 1\nFROM x\"}]}"

        shouldThrow<Exception> { ActionbaseQuery.from(broken) }
    }

    @Test
    fun `the transform in the request runs over the frames the fetch steps returned`() {
        // `TOPK` resolves its ranking through table metadata, which a stub has no business faking, so the
        // ranking step is spelled as the `SCAN` it becomes underneath.
        val query = ActionbaseQuery.from(REQUEST.replace(TOPK_STEP, SCAN_STEP))
        val executor = ActionbaseQueryExecutor(engineReturning(scanned = ranked, fetched = paid), SqlTransform())

        val result = executor.query(PreparedQuery.of(query), mapOf("entity" to "U1")).block()!!

        result.keys shouldBe setOf("result")
        result.getValue("result").rows.map { listOf(it.data["productGroupId"], it.data["metric"], it.data["paidAt"]) } shouldBe
            listOf(
                listOf("PG1", 30L, 1_700_000_000L),
                listOf("PG2", 20L, -1L),
                listOf("PG3", 10L, 1_600_000_000L),
            )
    }

    private companion object {
        /** `SCAN` answers the ranking step and `gets` answers the `GET` step, so the join has two sides. */
        fun engineReturning(
            scanned: DataFrame,
            fetched: DataFrame,
        ): QueryEngine {
            val binding = mockk<TableBinding>()
            every { binding.scan(any(), any(), any(), any(), any(), any(), any(), any()) } returns Mono.just(scanned)
            every { binding.gets(any(), any()) } returns Mono.just(fetched)
            // The interface has a default, but a mock answers nothing it was not told to answer.
            every { binding.emptyFrame } returns DataFrame(emptyList(), scanned.schema, total = 0L)

            return object : QueryEngine {
                override fun getTableBinding(
                    database: String,
                    alias: String,
                ): TableBinding = binding
            }
        }

        val REQUEST =
            """
            {
              "fetch": [
                {
                  "type": "TOPK",
                  "name": "hop1",
                  "database": "market",
                  "table": "market_order_collection_v1",
                  "topk": "top_product_groups_1y",
                  "entity": { "type": "VALUE", "value": ["{entity}"] },
                  "limit": 100
                },
                {
                  "type": "GET",
                  "name": "hop2",
                  "database": "market",
                  "table": "market_order_product_group_v1",
                  "source": { "type": "VALUE", "value": ["{entity}"] },
                  "target": { "type": "REF", "ref": "hop1", "field": "target" }
                }
              ],
              "transform": [
                {
                  "type": "SQL",
                  "name": "result",
                  "sql": "SELECT hop1.target AS productGroupId, hop1.metric AS metric, IFNULL(hop2.paidAt, -1) AS paidAt FROM hop1 LEFT JOIN hop2 ON hop1.source = hop2.source AND hop1.target = hop2.target ORDER BY metric DESC, paidAt DESC"
                }
              ]
            }
            """.trimIndent()

        val TOPK_STEP =
            """
            "type": "TOPK",
                  "name": "hop1",
                  "database": "market",
                  "table": "market_order_collection_v1",
                  "topk": "top_product_groups_1y",
                  "entity": { "type": "VALUE", "value": ["{entity}"] },
            """.trimIndent().trim()

        val SCAN_STEP =
            """
            "type": "SCAN",
                  "name": "hop1",
                  "database": "market",
                  "table": "market_order_collection_v1",
                  "index": "top_product_groups_1y",
                  "direction": "OUT",
                  "source": { "type": "VALUE", "value": ["{entity}"] },
            """.trimIndent().trim()

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

        val ranked =
            DataFrame(
                listOf("PG1" to 30L, "PG2" to 20L, "PG3" to 10L).map { (target, metric) ->
                    Row(mapOf("source" to "U1", "target" to target, "metric" to metric), rankedSchema)
                },
                rankedSchema,
                total = 3L,
            )

        // PG2 is missing: a product group can rank high and still have no recent order.
        val paid =
            DataFrame(
                listOf("PG1" to 1_700_000_000L, "PG3" to 1_600_000_000L).map { (target, paidAt) ->
                    Row(mapOf("source" to "U1", "target" to target, "paidAt" to paidAt), paidSchema)
                },
                paidSchema,
                total = 2L,
            )
    }
}
