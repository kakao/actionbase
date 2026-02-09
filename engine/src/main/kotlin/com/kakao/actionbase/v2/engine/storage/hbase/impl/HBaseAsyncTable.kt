package com.kakao.actionbase.v2.engine.storage.hbase.impl

import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTable

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.AdvancedScanResultConsumer
import org.apache.hadoop.hbase.client.AsyncTable
import org.apache.hadoop.hbase.client.CheckAndMutate
import org.apache.hadoop.hbase.client.CheckAndMutateResult
import org.apache.hadoop.hbase.client.Delete
import org.apache.hadoop.hbase.client.Get
import org.apache.hadoop.hbase.client.Increment
import org.apache.hadoop.hbase.client.Mutation
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.client.Result
import org.apache.hadoop.hbase.client.Scan
import org.apache.hadoop.hbase.client.TableDescriptor

import reactor.core.publisher.Mono

class HBaseAsyncTable(
    private val asyncTable: AsyncTable<AdvancedScanResultConsumer>,
) : HBaseTable {
    override val name: TableName
        get() = asyncTable.name

    override val configuration: Configuration
        get() = asyncTable.configuration

    override val descriptor: Mono<TableDescriptor>
        get() = Mono.fromFuture(asyncTable.descriptor)

    override fun get(get: Get): Mono<Result> = Mono.fromFuture(asyncTable.get(get))

    override fun getAll(gets: List<Get>): Mono<List<Result>> {
        val futures = asyncTable.getAll(gets)
        return Mono.fromFuture(futures)
    }

    override fun put(put: Put): Mono<Void> = Mono.fromFuture(asyncTable.put(put))

    override fun delete(delete: Delete): Mono<Void> = Mono.fromFuture(asyncTable.delete(delete))

    override fun batchAll(deferredRequests: List<Any>): Mono<Void> {
        val mutations: List<Mutation> =
            deferredRequests.map {
                when (it) {
                    is Put -> it
                    is Delete -> it
                    is Increment -> it
                    else -> throw IllegalArgumentException("Unsupported mutation type: ${it::class.simpleName}")
                }
            }

        return Mono.fromFuture(asyncTable.batchAll<Any>(mutations)).then()
    }

    override fun exists(get: Get): Mono<Boolean> = Mono.fromFuture(asyncTable.exists(get))

    override fun increment(increment: Increment): Mono<Result> = Mono.fromFuture(asyncTable.increment(increment))

    override fun scan(
        scan: Scan,
        limit: Int,
    ): Mono<List<Result>> =
        Mono.fromCallable {
            asyncTable.getScanner(scan).use { it.take(limit).toList() }
        }

    override fun checkAndMutate(checkAndMutate: CheckAndMutate): Mono<CheckAndMutateResult> = Mono.fromFuture(asyncTable.checkAndMutate(checkAndMutate))
}
