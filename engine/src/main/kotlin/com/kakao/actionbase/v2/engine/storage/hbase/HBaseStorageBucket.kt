package com.kakao.actionbase.v2.engine.storage.hbase

import com.kakao.actionbase.core.Constants
import com.kakao.actionbase.core.storage.HBaseRecord
import com.kakao.actionbase.core.storage.MutationRequest
import com.kakao.actionbase.v2.engine.storage.StorageBucket

import org.apache.hadoop.hbase.client.AdvancedScanResultConsumer
import org.apache.hadoop.hbase.client.AsyncTable
import org.apache.hadoop.hbase.client.Delete
import org.apache.hadoop.hbase.client.Get
import org.apache.hadoop.hbase.client.Increment
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.client.Scan
import org.apache.hadoop.hbase.filter.PrefixFilter
import org.apache.hadoop.hbase.util.Bytes

import reactor.core.publisher.Mono

class HBaseStorageBucket(
    private val table: AsyncTable<AdvancedScanResultConsumer>,
) : StorageBucket {
    override fun get(key: ByteArray): Mono<ByteArray?> =
        Mono
            .fromFuture(
                table.get(
                    Get(key)
                        .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER),
                ),
            ).map { result ->
                if (result.isEmpty) null else result.getValue(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER)
            }

    override fun get(keys: List<ByteArray>): Mono<List<HBaseRecord>> =
        Mono
            .fromFuture(
                table.getAll(
                    keys.map { key ->
                        Get(key).addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER)
                    },
                ),
            ).map { results ->
                results.mapIndexedNotNull { index, result ->
                    if (result.isEmpty) {
                        null
                    } else {
                        HBaseRecord(
                            key = keys[index],
                            value = result.getValue(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER),
                        )
                    }
                }
            }

    override fun put(
        key: ByteArray,
        value: ByteArray,
    ): Mono<Void> =
        Mono.fromFuture(
            table.put(
                Put(key)
                    .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, value),
            ),
        )

    override fun delete(key: ByteArray): Mono<Void> =
        Mono.fromFuture(
            table.delete(
                Delete(key),
            ),
        )

    override fun scan(
        prefix: ByteArray,
        limit: Int,
        start: ByteArray?,
        stop: ByteArray?,
    ): Mono<List<HBaseRecord>> {
        val scan = Scan().setLimit(limit)

        // Set prefix filter if provided
        if (prefix.isNotEmpty()) {
            scan.filter = PrefixFilter(prefix)
        }

        // Set start row if provided
        start?.let { scan.withStartRow(it, true) }

        // Set stop row if provided
        stop?.let { scan.withStopRow(it, false) }

        // Add column family and qualifier
        scan.addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER)

        return Mono
            .fromFuture(table.getScanner(scan))
            .map { scanner ->
                scanner.use { s ->
                    s.toList().map { result ->
                        HBaseRecord(
                            key = result.row,
                            value = result.getValue(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER),
                        )
                    }
                }
            }
    }

    override fun increment(
        key: ByteArray,
        delta: Long,
    ): Mono<Long> =
        Mono
            .fromFuture(
                table.increment(
                    Increment(key)
                        .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, delta),
                ),
            ).map { result ->
                result.getValue(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER).let { Bytes.toLong(it) }
            }

    override fun batch(requests: List<MutationRequest>): Mono<Void> =
        Mono
            .fromFuture(
                table.batchAll<Any>(
                    requests.map { request ->
                        when (request) {
                            is MutationRequest.Put ->
                                Put(request.key)
                                    .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, request.value)

                            is MutationRequest.Delete -> Delete(request.key)

                            is MutationRequest.Increment ->
                                Increment(request.key)
                                    .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, request.value)
                        }
                    },
                ),
            ).then()

    override fun exists(key: ByteArray): Mono<Boolean> =
        Mono.fromFuture(
            table.exists(
                Get(key)
                    .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER),
            ),
        )

    override fun setIfNotExists(
        key: ByteArray,
        value: ByteArray,
    ): Mono<Boolean> =
        Mono.fromFuture(
            table
                .checkAndMutate(
                    key,
                    Constants.DEFAULT_COLUMN_FAMILY,
                ).qualifierEquals(Constants.DEFAULT_QUALIFIER, null) // Check that qualifier doesn't exist
                .thenPut(
                    Put(key)
                        .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, value),
                ),
        )

    override fun deleteIfEquals(
        key: ByteArray,
        expectedValue: ByteArray,
    ): Mono<Boolean> =
        Mono.fromFuture(
            table
                .checkAndMutate(
                    key,
                    Constants.DEFAULT_COLUMN_FAMILY,
                ).qualifierEquals(Constants.DEFAULT_QUALIFIER, expectedValue)
                .thenDelete(Delete(key)),
        )
}
