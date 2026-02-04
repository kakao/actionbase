package com.kakao.actionbase.v2.engine.storage.memory

import com.kakao.actionbase.v2.core.code.hbase.Constants

import org.apache.hadoop.hbase.client.CheckAndMutate
import org.apache.hadoop.hbase.client.Delete
import org.apache.hadoop.hbase.client.Get
import org.apache.hadoop.hbase.client.Increment
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.client.Scan
import org.apache.hadoop.hbase.util.Bytes
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MemoryHBaseTableTest {
    private val table = MemoryHBaseTable("ns", "edges")

    @Test
    fun `put and get returns stored value`() {
        val row = Bytes.toBytes("row-1")
        val value = Bytes.toBytes("value-1")

        val put =
            Put(row)
                .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, value)
        table.put(put).block()

        val get =
            Get(row)
                .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER)
        val result = table.get(get).block()

        assertArrayEquals(value, result?.value())
    }

    @Test
    fun `delete removes row`() {
        val row = Bytes.toBytes("row-2")
        val value = Bytes.toBytes("value-2")

        val put =
            Put(row)
                .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, value)
        table.put(put).block()

        val delete = Delete(row)
        table.delete(delete).block()

        val get =
            Get(row)
                .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER)
        val result = table.get(get).block()

        assertTrue(result?.isEmpty == true)
    }

    @Test
    fun `checkAndMutate setnx behavior`() {
        val row = Bytes.toBytes("row-3")
        val value = Bytes.toBytes("value-3")

        val put =
            Put(row)
                .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, value)
        val check =
            CheckAndMutate
                .newBuilder(row)
                .ifNotExists(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER)
                .build(put)

        val first = table.checkAndMutate(check).block()
        val second = table.checkAndMutate(check).block()

        assertTrue(first?.isSuccess == true)
        assertFalse(second?.isSuccess == true)
    }

    @Test
    fun `increment updates value`() {
        val row = Bytes.toBytes("row-4")
        val increment =
            Increment(row)
                .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, 5L)

        val result = table.increment(increment).block()
        val value = result?.value()

        assertEquals(5L, Bytes.toLong(value))
    }

    @Test
    fun `scan respects prefix and range`() {
        val row1 = Bytes.toBytes("user:1")
        val row2 = Bytes.toBytes("user:2")
        val row3 = Bytes.toBytes("admin:1")

        listOf(row1, row2, row3).forEach { row ->
            val put =
                Put(row)
                    .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, Bytes.toBytes("v"))
            table.put(put).block()
        }

        val scan =
            Scan()
                .setRowPrefixFilter(Bytes.toBytes("user:"))
                .withStartRow(row1, true)
                .withStopRow(row2, true)
                .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER)

        val results = table.scan(scan, 10).block() ?: emptyList()

        assertEquals(2, results.size)
        assertArrayEquals(row1, results[0].row)
        assertArrayEquals(row2, results[1].row)
    }
}
