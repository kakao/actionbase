package com.kakao.actionbase.v2.engine.storage.hbase.impl

import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTable

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.CheckAndMutate
import org.apache.hadoop.hbase.client.CheckAndMutateResult
import org.apache.hadoop.hbase.client.Delete
import org.apache.hadoop.hbase.client.Get
import org.apache.hadoop.hbase.client.Increment
import org.apache.hadoop.hbase.client.Mutation
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.client.Result
import org.apache.hadoop.hbase.client.ResultScanner
import org.apache.hadoop.hbase.client.Row
import org.apache.hadoop.hbase.client.RowMutations
import org.apache.hadoop.hbase.client.Scan
import org.apache.hadoop.hbase.client.Table
import org.apache.hadoop.hbase.client.TableDescriptor
import org.apache.hadoop.hbase.filter.CompareFilter

import reactor.core.publisher.Mono

class HBaseSyncTable(
    private val table: Table,
) : HBaseTable {
    override val name: TableName
        get() = table.name

    override val configuration: Configuration
        get() = table.configuration

    override val descriptor: Mono<TableDescriptor>
        get() = Mono.fromCallable { table.descriptor }

    override fun get(get: Get): Mono<Result> = Mono.fromCallable { table.get(get) }

    override fun getAll(gets: List<Get>): Mono<List<Result>> = Mono.fromCallable { table.get(gets).asList() }

    override fun put(put: Put): Mono<Void> = Mono.fromCallable { table.put(put) }.then()

    override fun delete(delete: Delete): Mono<Void> = Mono.fromCallable { table.delete(delete) }.then()

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
        return Mono.fromCallable { table.batch(mutations, null) }.then()
    }

    override fun exists(get: Get): Mono<Boolean> = Mono.fromCallable { table.exists(get) }

    override fun checkAndMutate(checkAndMutate: CheckAndMutate): Mono<CheckAndMutateResult> = Mono.fromCallable { table.checkAndMutate(checkAndMutate) }

    override fun increment(increment: Increment): Mono<Result> = Mono.fromCallable { table.increment(increment) }

    override fun scan(
        scan: Scan,
        limit: Int,
    ): Mono<List<Result>> = Mono.fromCallable { table.getScanner(scan).use { it.take(limit).toList() } }
}

class NewMockTable(
    private val mockTable: Table,
) : Table by mockTable {
    override fun get(get: Get): Result = mockTable.get(get)

    override fun get(gets: List<Get>): Array<Result> = mockTable.get(gets)

    override fun put(put: Put): Unit = mockTable.put(put)

    override fun delete(delete: Delete): Unit = mockTable.delete(delete)

    override fun exists(get: Get): Boolean = mockTable.exists(get)

    override fun batch(
        actions: MutableList<out Row>,
        results: Array<out Any>?,
    ) = synchronized(this) {
        mockTable.batch(actions, results)
    }

    override fun checkAndMutate(checkAndMutate: CheckAndMutate): CheckAndMutateResult =
        synchronized(this) {
            val success =
                mockTable.checkAndMutate(
                    checkAndMutate.row,
                    checkAndMutate.family,
                    checkAndMutate.qualifier,
                    CompareFilter.CompareOp.EQUAL,
                    checkAndMutate.value,
                    RowMutations(checkAndMutate.action.row)
                        .add(checkAndMutate.action as Mutation),
                )
            CheckAndMutateResult(success, Result.EMPTY_RESULT)
        }

    override fun getScanner(scan: Scan): ResultScanner = mockTable.getScanner(scan)
}
