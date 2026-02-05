package com.kakao.actionbase.v2.engine.storage.hbase.impl

import com.kakao.actionbase.v2.core.code.hbase.Constants
import com.kakao.actionbase.v2.engine.storage.StorageOperation

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
import org.apache.hadoop.hbase.client.Scan
import org.apache.hadoop.hbase.client.Table
import org.apache.hadoop.hbase.client.TableDescriptor

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

    override fun get(gets: List<Get>): Mono<List<Result>> = Mono.fromCallable { table.get(gets).asList() }

    override fun put(put: Put): Mono<Void> = Mono.fromCallable { table.put(put) }.then()

    override fun delete(delete: Delete): Mono<Void> = Mono.fromCallable { table.delete(delete) }.then()

    override fun batch(operations: List<StorageOperation>): Mono<Void> {
        val mutations: List<Mutation> =
            operations.map { op ->
                when (op) {
                    is StorageOperation.PutOp -> Put(op.put.key).addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, op.put.value)
                    is StorageOperation.DeleteOp -> Delete(op.delete.key)
                    is StorageOperation.IncrementOp -> Increment(op.increment.key).addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER, op.increment.amount)
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
