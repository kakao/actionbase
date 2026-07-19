package com.kakao.actionbase.test.hbase

import org.apache.hadoop.hbase.client.Delete
import org.apache.hadoop.hbase.client.Increment
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.client.Table
import org.apache.hadoop.hbase.util.Bytes

object OperationHelper {
    fun Table.perform(
        rowKey: ByteArray,
        family: ByteArray,
        qualifier: ByteArray,
        valuePrefix: String,
        operations: String,
    ) {
        val table = this
        // HBase row tombstones cover cells with timestamp <= tombstone timestamp, so a Put that lands
        // in the same millisecond as a preceding Delete gets shadowed by it. Track the Delete's
        // timestamp so the next plain Put is forced strictly after it instead of racing the clock.
        var previousDeleteTimestamp: Long? = null
        operations.split(", ").withIndex().forEach { (index, op) ->
            when {
                op == "Put" -> {
                    val put = Put(rowKey)
                    val value = Bytes.toBytes("${valuePrefix}$index")
                    previousDeleteTimestamp?.let { put.addColumn(family, qualifier, it + 1, value) }
                        ?: put.addColumn(family, qualifier, value)
                    table.put(put)
                    previousDeleteTimestamp = null
                }

                op.startsWith("Put(") -> {
                    val ttl = op.substringAfter("Put(").substringBefore(")").toLong()
                    val put = Put(rowKey)
                    put.addColumn(family, qualifier, Bytes.toBytes("${valuePrefix}$index"))
                    put.ttl = ttl
                    table.put(put)
                    previousDeleteTimestamp = null
                }

                op == "Delete" -> {
                    val delete = Delete(rowKey)
                    table.delete(delete)
                    previousDeleteTimestamp = System.currentTimeMillis()
                }

                op == "Increment" -> {
                    val increment = Increment(rowKey)
                    increment.addColumn(family, qualifier, 1L)
                    table.increment(increment)
                }

                op.startsWith("Delay(") -> {
                    val delayMs = op.substringAfter("Delay(").substringBefore(")").toLong()
                    Thread.sleep(delayMs)
                }
            }
        }
    }
}
