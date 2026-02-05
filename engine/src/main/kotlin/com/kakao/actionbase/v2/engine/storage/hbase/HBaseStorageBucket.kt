package com.kakao.actionbase.v2.engine.storage.hbase

import com.kakao.actionbase.core.storage.HBaseRecord
import com.kakao.actionbase.core.storage.MutationRequest
import com.kakao.actionbase.v2.core.code.hbase.Constants
import com.kakao.actionbase.v2.engine.storage.StorageBucket

import org.apache.hadoop.hbase.client.CheckAndMutate
import org.apache.hadoop.hbase.client.Delete
import org.apache.hadoop.hbase.client.Get
import org.apache.hadoop.hbase.client.Increment
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.client.Result
import org.apache.hadoop.hbase.client.Scan
import org.apache.hadoop.hbase.util.Bytes

import reactor.core.publisher.Mono

class HBaseStorageBucket(
    val table: HBaseTable,
) : StorageBucket {
    override fun get(key: ByteArray): Mono<ByteArray?> {
        val get = Get(key).addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER)
        return table
            .get(get)
            .mapNotNull { result ->
                if (result.isEmpty) {
                    null
                } else {
                    result.value()
                }
            }
    }

    override fun get(keys: List<ByteArray>): Mono<List<HBaseRecord>> {
        val gets =
            keys.map { key ->
                Get(key).addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER)
            }
        return table.get(gets).map { results ->
            results.mapNotNull { result ->
                if (result.isEmpty) {
                    null
                } else {
                    HBaseRecord(key = result.row, value = result.value())
                }
            }
        }
    }

    override fun put(
        key: ByteArray,
        value: ByteArray,
    ): Mono<Void> {
        val put = Put(key).addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, value)
        return table.put(put)
    }

    override fun delete(key: ByteArray): Mono<Void> = table.delete(Delete(key))

    override fun scan(
        prefix: ByteArray,
        limit: Int,
        start: ByteArray?,
        stop: ByteArray?,
    ): Mono<List<HBaseRecord>> {
        val scan = Scan().setRowPrefixFilter(prefix)
        start?.let { scan.withStartRow(it, false) }
        stop?.let { scan.withStopRow(it, false) }
        scan.addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER)
        return table.scan(scan, limit).map { results ->
            results.mapNotNull { result ->
                if (result.isEmpty) {
                    null
                } else {
                    HBaseRecord(key = result.row, value = result.value())
                }
            }
        }
    }

    override fun increment(
        key: ByteArray,
        delta: Long,
    ): Mono<Long> {
        val increment =
            Increment(key)
                .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, delta)
        return table.increment(increment).map { result ->
            Bytes.toLong(result.value())
        }
    }

    override fun batch(requests: List<MutationRequest>): Mono<Void> {
        val mutations =
            requests.map { request ->
                when (request) {
                    is MutationRequest.Put ->
                        Put(request.key)
                            .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, request.value)
                    is MutationRequest.Delete ->
                        Delete(request.key)
                    is MutationRequest.Increment ->
                        Increment(request.key)
                            .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, request.value)
                }
            }
        return table.batch(mutations)
    }

    override fun exists(key: ByteArray): Mono<Boolean> {
        val get = Get(key).addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER)
        return table.exists(get)
    }

    override fun setIfNotExists(
        key: ByteArray,
        value: ByteArray,
    ): Mono<Boolean> {
        val request =
            CheckAndMutate
                .newBuilder(key)
                .ifNotExists(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER)
                .build(Put(key).addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, value))
        return table.checkAndMutate(request).map { it.isSuccess }
    }

    override fun deleteIfEquals(
        key: ByteArray,
        expectedValue: ByteArray,
    ): Mono<Boolean> {
        val request =
            CheckAndMutate
                .newBuilder(key)
                .ifEquals(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, expectedValue)
                .build(Delete(key))
        return table.checkAndMutate(request).map { it.isSuccess }
    }

    fun getRaw(get: Get): Mono<Result> = table.get(get)

    fun getRaw(gets: List<Get>): Mono<List<Result>> = table.get(gets)

    fun scanRaw(
        scan: Scan,
        limit: Int,
    ): Mono<List<Result>> = table.scan(scan, limit)

    fun batchRaw(mutations: List<Any>): Mono<Void> = table.batch(mutations)

    fun incrementRaw(increment: Increment): Mono<Result> = table.increment(increment)

    fun checkAndMutateRaw(checkAndMutate: CheckAndMutate): Mono<Boolean> = table.checkAndMutate(checkAndMutate).map { it.isSuccess }
}
