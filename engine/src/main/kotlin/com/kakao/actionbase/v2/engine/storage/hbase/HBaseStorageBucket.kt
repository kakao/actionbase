package com.kakao.actionbase.v2.engine.storage.hbase

import org.apache.hadoop.hbase.client.Delete as HDelete
import org.apache.hadoop.hbase.client.Get as HGet
import org.apache.hadoop.hbase.client.Increment as HIncrement
import org.apache.hadoop.hbase.client.Put as HPut
import org.apache.hadoop.hbase.client.Scan as HScan

import com.kakao.actionbase.v2.engine.AsyncUtils
import com.kakao.actionbase.v2.engine.storage.Delete
import com.kakao.actionbase.v2.engine.storage.Get
import com.kakao.actionbase.v2.engine.storage.Increment
import com.kakao.actionbase.v2.engine.storage.Put
import com.kakao.actionbase.v2.engine.storage.Scan
import com.kakao.actionbase.v2.engine.storage.StorageBucket
import com.kakao.actionbase.v2.engine.storage.result.GetResult
import com.kakao.actionbase.v2.engine.storage.result.ScanResult

import java.util.function.Consumer

import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.AsyncConnection
import org.apache.hadoop.hbase.client.AsyncTable
import org.apache.hadoop.hbase.client.Result
import org.apache.hadoop.hbase.client.ResultScanner

import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
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

    override fun put(put: Put): Mono<Void> {
        val hput = HPut(put.key).addColumn(DEFAULT_FAMILY_BYTES, DEFAULT_QUALIFIER_BYTES, put.value)
        return AsyncUtils.asMono(table.put(hput))
    }

    override fun get(get: Get): Mono<GetResult> {
        val hget = HGet(get.key)
        return AsyncUtils.asMono(table.get(hget)).map {
            if (it.isEmpty) {
                GetResult.NotFound
            } else {
                GetResult.Found(it.getValue(DEFAULT_FAMILY_BYTES, DEFAULT_QUALIFIER_BYTES))
            }
        }
    }

    override fun delete(delete: Delete): Mono<Void> {
        val hdelete = HDelete(delete.key)
        return AsyncUtils.asMono(table.delete(hdelete))
    }

    override fun scan(scan: Scan): Flux<ScanResult> {
        val hscan =
            HScan()
                .setScanMetricsEnabled(false)
                .setAsyncPrefetch(true)
                .setCaching(100)
                .setPrefix(scan.prefix)
                .setLimit(scan.limit)

        return Flux
            .create(
                Consumer<FluxSink<Result>> { sink ->
                    table
                        .getScanner(hscan)
                        .forEach { result ->
                            sink.next(result)
                        }.whenComplete { _, throwable ->
                            if (throwable != null) {
                                sink.error(throwable)
                            } else {
                                sink.complete()
                            }
                        }
                },
            ).map {
                ScanResult.Data(it.row, it.getValue(DEFAULT_FAMILY_BYTES, DEFAULT_QUALIFIER_BYTES))
            }
    }

    override fun increment(increment: Increment): Mono<Long> {
        val hincrement = HIncrement(increment.key).addColumn(DEFAULT_FAMILY_BYTES, DEFAULT_QUALIFIER_BYTES, increment.amount)
        return AsyncUtils
            .asMono(table.increment(hincrement))
            .map {
                it.getValue(DEFAULT_FAMILY_BYTES, DEFAULT_QUALIFIER_BYTES)
            }.map {
                bytesToLong(it)
            }
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
