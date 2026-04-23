package com.kakao.actionbase.engine.storage

import com.kakao.actionbase.core.storage.StorageOp
import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

import org.apache.hadoop.hbase.client.Append
import org.apache.hadoop.hbase.client.Delete
import org.apache.hadoop.hbase.client.Increment
import org.apache.hadoop.hbase.client.Put
import org.junit.jupiter.api.Test

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

class StorageOpCollectorTest {
    private val table = "test_table"
    private val mapper = jacksonObjectMapper()

    @ObjectSourceParameterizedTest
    @ObjectSource(
        cases = """
        - name: Put with single cell
          kind: PUT
          row: row-1
          family: cf
          qualifier: q
          value: value
          expectedJson: '{"table":"test_table","row":"cm93LTE=","cells":[{"family":"Y2Y=","qualifier":"cQ==","value":"dmFsdWU=","type":"Put"}],"kind":"Put"}'

        - name: Delete full row yields no cells
          kind: DELETE
          row: row-2
          expectedJson: '{"table":"test_table","row":"cm93LTI=","cells":[],"kind":"Delete"}'

        - name: Delete with addColumns emits DeleteColumn cell with null value
          kind: DELETE_COLUMNS
          row: row-3
          family: cf
          qualifier: q
          expectedJson: '{"table":"test_table","row":"cm93LTM=","cells":[{"family":"Y2Y=","qualifier":"cQ==","value":null,"type":"DeleteColumn"}],"kind":"Delete"}'

        - name: Delete with addFamily emits DeleteFamily cell with null value
          kind: DELETE_FAMILY
          row: row-3f
          family: cf
          expectedJson: '{"table":"test_table","row":"cm93LTNm","cells":[{"family":"Y2Y=","qualifier":"","value":null,"type":"DeleteFamily"}],"kind":"Delete"}'

        - name: Increment with long delta
          kind: INCREMENT
          row: row-4
          family: cf
          qualifier: q
          delta: 7
          expectedJson: '{"table":"test_table","row":"cm93LTQ=","deltas":[{"family":"Y2Y=","qualifier":"cQ==","delta":7}],"kind":"Increment"}'

        - name: Put with multi-byte value base64 encodes correctly
          kind: PUT
          row: row
          family: cf
          qualifier: q
          valueHex: 00000000deadbeef
          expectedJson: '{"table":"test_table","row":"cm93","cells":[{"family":"Y2Y=","qualifier":"cQ==","value":"AAAAAN6tvu8=","type":"Put"}],"kind":"Put"}'
        """,
    )
    fun `Mutation is captured as StorageOp`(
        kind: String,
        row: String,
        family: String?,
        qualifier: String?,
        value: String?,
        valueHex: String?,
        delta: Long?,
        expectedJson: String,
    ) {
        val mutation: Any =
            when (kind) {
                "PUT" ->
                    Put(row.toByteArray()).addColumn(
                        family!!.toByteArray(),
                        qualifier!!.toByteArray(),
                        value?.toByteArray() ?: hexToBytes(valueHex!!),
                    )
                "DELETE" -> Delete(row.toByteArray())
                "DELETE_COLUMNS" -> Delete(row.toByteArray()).addColumns(family!!.toByteArray(), qualifier!!.toByteArray())
                "DELETE_FAMILY" -> Delete(row.toByteArray()).addFamily(family!!.toByteArray())
                "INCREMENT" -> Increment(row.toByteArray()).addColumn(family!!.toByteArray(), qualifier!!.toByteArray(), delta!!)
                else -> error("unknown kind: $kind")
            }

        val collector = StorageOpCollector()
        collector.collect(mutation, table)
        val op = collector.snapshot().single()

        assertEquals(expectedJson, mapper.writeValueAsString(op))
    }

    @Test
    fun `Put across multiple column families emits one cell per family`() {
        val put =
            Put("row".toByteArray())
                .addColumn("cf1".toByteArray(), "q".toByteArray(), "v1".toByteArray())
                .addColumn("cf2".toByteArray(), "q".toByteArray(), "v2".toByteArray())

        val collector = StorageOpCollector()
        collector.collect(put, table)
        val op = collector.snapshot().single() as StorageOp.Put

        assertEquals(2, op.cells.size)
        assertEquals(setOf("Y2Yx", "Y2Yy"), op.cells.map { it.family }.toSet())
        op.cells.forEach { assertEquals("Put", it.type) }
    }

    @Test
    fun `collectAll surfaces non-Mutation entries as Unknown and preserves order`() {
        val collector = StorageOpCollector()
        val put = Put("a".toByteArray()).addColumn("cf".toByteArray(), "q".toByteArray(), "v".toByteArray())
        val delete = Delete("b".toByteArray())

        collector.collectAll(listOf(put, "not-a-mutation", delete), table)
        val ops = collector.snapshot()

        assertEquals(3, ops.size)
        assertTrue(ops[0] is StorageOp.Put)
        assertTrue(ops[1] is StorageOp.Unknown)
        assertEquals("kotlin.String", (ops[1] as StorageOp.Unknown).type)
        assertTrue(ops[2] is StorageOp.Delete)
    }

    @Test
    fun `Append is captured as Unknown rather than routed through Mutation path`() {
        val collector = StorageOpCollector()
        val append = Append("row".toByteArray()).addColumn("cf".toByteArray(), "q".toByteArray(), "v".toByteArray())

        collector.collect(append, table)
        val op = collector.snapshot().single()

        assertTrue(op is StorageOp.Unknown)
        assertEquals("org.apache.hadoop.hbase.client.Append", (op as StorageOp.Unknown).type)
    }

    @Test
    fun `collect stops appending once the cap is reached and marks truncated`() {
        val collector = StorageOpCollector(maxOps = 2)
        repeat(5) { i ->
            collector.collect(
                Put("row-$i".toByteArray()).addColumn("cf".toByteArray(), "q".toByteArray(), "v".toByteArray()),
                table,
            )
        }

        assertEquals(2, collector.snapshot().size)
        assertTrue(collector.isTruncated())
    }

    @Test
    fun `toContextMap omits truncated flag when not truncated and includes it when truncated`() {
        val ok = StorageOpCollector(maxOps = 2)
        ok.collect(Put("r".toByteArray()).addColumn("cf".toByteArray(), "q".toByteArray(), "v".toByteArray()), table)
        val okCtx = ok.toContextMap()
        assertTrue(okCtx.containsKey(StorageOpCollector.CONTEXT_KEY))
        assertFalse(okCtx.containsKey(StorageOpCollector.TRUNCATED_KEY))

        val over = StorageOpCollector(maxOps = 1)
        repeat(3) { over.collect(Put("r".toByteArray()), table) }
        val overCtx = over.toContextMap()
        assertEquals(true, overCtx[StorageOpCollector.TRUNCATED_KEY])
    }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i ->
            ((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte()
        }
}
