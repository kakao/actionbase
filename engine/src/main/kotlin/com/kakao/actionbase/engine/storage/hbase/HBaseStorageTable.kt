package com.kakao.actionbase.engine.storage.hbase

import com.kakao.actionbase.core.Constants
import com.kakao.actionbase.core.storage.HBaseRecord
import com.kakao.actionbase.core.storage.MutationRequest
import com.kakao.actionbase.engine.storage.StorageTable
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTable

import org.apache.hadoop.hbase.client.CheckAndMutate
import org.apache.hadoop.hbase.client.Delete
import org.apache.hadoop.hbase.client.Get
import org.apache.hadoop.hbase.client.Increment
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.client.Scan
import org.apache.hadoop.hbase.util.Bytes

import reactor.core.publisher.Mono

class HBaseStorageTable(
    private val table: HBaseTable,
) : StorageTable {
    override fun get(key: ByteArray): Mono<ByteArray?> {
        val get = Get(key).addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER)
        return table.get(get).handle { result, sink ->
            if (!result.isEmpty) {
                sink.next(result.getValue(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER))
            }
            // For empty result, don't emit anything (Mono will complete with null)
        }
    }

    override fun get(keys: List<ByteArray>): Mono<List<HBaseRecord>> {
        val gets = keys.map { Get(it).addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER) }
        return table.get(gets).map { results ->
            results.filter { !it.isEmpty }.map { result ->
                HBaseRecord(
                    key = result.row,
                    value = result.getValue(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER),
                )
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

    override fun delete(key: ByteArray): Mono<Void> {
        val delete = Delete(key)
        return table.delete(delete)
    }

    override fun scan(
        prefix: ByteArray,
        limit: Int,
        start: ByteArray?,
        stop: ByteArray?,
    ): Mono<List<HBaseRecord>> {
        val scan =
            Scan()
                .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER)
                .setRowPrefixFilter(prefix)

        if (start != null) scan.withStartRow(start, true)
        if (stop != null) scan.withStopRow(stop, false)

        return table.scan(scan, limit).map { results ->
            results.map { result ->
                HBaseRecord(
                    key = result.row,
                    value = result.getValue(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER),
                )
            }
        }
    }

    override fun increment(
        key: ByteArray,
        delta: Long,
    ): Mono<Long> {
        val increment = Increment(key).addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, delta)
        return table.increment(increment).map { result ->
            Bytes.toLong(result.getValue(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER))
        }
    }

    override fun batch(requests: List<MutationRequest>): Mono<Void> {
        val mutations =
            requests.map {
                when (it) {
                    is MutationRequest.Put ->
                        Put(it.key).addColumn(
                            Constants.DEFAULT_COLUMN_FAMILY,
                            Constants.DEFAULT_QUALIFIER,
                            it.value,
                        )
                    is MutationRequest.Delete -> Delete(it.key)
                    is MutationRequest.Increment ->
                        Increment(it.key).addColumn(
                            Constants.DEFAULT_COLUMN_FAMILY,
                            Constants.DEFAULT_QUALIFIER,
                            it.value,
                        )
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
        val checkAndMutate =
            CheckAndMutate
                .newBuilder(key)
                .ifNotExists(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER)
                .build(Put(key).addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, value))
        return table.checkAndMutate(checkAndMutate).map { it.isSuccess }
    }

    override fun deleteIfEquals(
        key: ByteArray,
        expectedValue: ByteArray,
    ): Mono<Boolean> {
        val checkAndMutate =
            CheckAndMutate
                .newBuilder(key)
                .ifEquals(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, expectedValue)
                .build(Delete(key))
        return table.checkAndMutate(checkAndMutate).map { it.isSuccess }
    }
}
