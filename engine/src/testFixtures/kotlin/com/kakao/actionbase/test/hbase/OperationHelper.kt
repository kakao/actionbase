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
        operations.split(", ").withIndex().forEach { (index, op) ->
            when {
                op == "Put" -> {
                    val put = Put(rowKey)
                    put.addColumn(family, qualifier, Bytes.toBytes("${valuePrefix}$index"))
                    table.put(put)
                }

                op.startsWith("Put(") -> {
                    val ttl = op.substringAfter("Put(").substringBefore(")").toLong()
                    val put = Put(rowKey)
                    put.addColumn(family, qualifier, Bytes.toBytes("${valuePrefix}$index"))
                    put.ttl = ttl
                    table.put(put)
                }

                op == "Delete" -> {
                    val delete = Delete(rowKey)
                    table.delete(delete)
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
