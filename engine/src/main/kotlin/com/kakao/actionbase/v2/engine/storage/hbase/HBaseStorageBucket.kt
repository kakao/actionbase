package com.kakao.actionbase.v2.engine.storage.hbase

import org.apache.hadoop.hbase.client.Delete as HDelete
import org.apache.hadoop.hbase.client.Increment as HIncrement
import org.apache.hadoop.hbase.client.Put as HPut

import com.kakao.actionbase.v2.engine.AsyncUtils
import com.kakao.actionbase.v2.engine.storage.StorageBucket
import com.kakao.actionbase.v2.engine.storage.StorageOperation

import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.AsyncConnection
import org.apache.hadoop.hbase.client.AsyncTable
import org.apache.hadoop.hbase.client.ResultScanner
import org.apache.hadoop.hbase.client.Row

import reactor.core.publisher.Mono

class HBaseStorageBucket(
    private val connection: AsyncConnection,
    private val hbaseNamespace: String,
    private val bucketName: String,
) : StorageBucket {
    companion object {
        private const val DEFAULT_FAMILY = "d"
        private val DEFAULT_FAMILY_BYTES = DEFAULT_FAMILY.toByteArray()
        private const val DEFAULT_QUALIFIER = "d"
        private val DEFAULT_QUALIFIER_BYTES = DEFAULT_QUALIFIER.toByteArray()
    }

    private val table: AsyncTable<ResultScanner> by lazy {
        connection.getTable(TableName.valueOf(hbaseNamespace, bucketName))
    }

    // ... existing methods ...

    override fun batch(operations: List<StorageOperation>): Mono<Void> {
        val hbaseOperations =
            operations.map { op ->
                when (op) {
                    is StorageOperation.PutOp -> HPut(op.put.key).addColumn(DEFAULT_FAMILY_BYTES, DEFAULT_QUALIFIER_BYTES, op.put.value) as org.apache.hadoop.hbase.client.Row
                    is StorageOperation.DeleteOp -> HDelete(op.delete.key) as org.apache.hadoop.hbase.client.Row
                    is StorageOperation.IncrementOp -> HIncrement(op.increment.key).addColumn(DEFAULT_FAMILY_BYTES, DEFAULT_QUALIFIER_BYTES, op.increment.amount) as org.apache.hadoop.hbase.client.Row
                }
            }
        return AsyncUtils.asMono(table.batch(hbaseOperations)).then()
    }

    private fun bytesToLong(bytes: ByteArray): Long {
        var result = 0L
        for (i in 0..7) {
            result = result shl 8
            result = result or (bytes[i].toLong() and 0xFF)
        }
        return result
    }
}
