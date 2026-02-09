package com.kakao.actionbase.v2.engine.storage.hbase

import com.kakao.actionbase.v2.engine.storage.hbase.impl.HBaseAsyncTable
import com.kakao.actionbase.v2.engine.storage.hbase.impl.HBaseSyncTable

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.AdvancedScanResultConsumer
import org.apache.hadoop.hbase.client.AsyncTable
import org.apache.hadoop.hbase.client.CheckAndMutate
import org.apache.hadoop.hbase.client.CheckAndMutateResult
import org.apache.hadoop.hbase.client.Delete
import org.apache.hadoop.hbase.client.Get
import org.apache.hadoop.hbase.client.Increment
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.client.Result
import org.apache.hadoop.hbase.client.Scan
import org.apache.hadoop.hbase.client.Table
import org.apache.hadoop.hbase.client.TableDescriptor

import reactor.core.publisher.Mono

interface HBaseTable {
    val name: TableName

    val configuration: Configuration

    val descriptor: Mono<TableDescriptor>

    fun get(get: Get): Mono<Result>

    fun getAll(gets: List<Get>): Mono<List<Result>>

    fun put(put: Put): Mono<Void>

    fun delete(delete: Delete): Mono<Void>

    fun batchAll(deferredRequests: List<Any>): Mono<Void>

    fun exists(get: Get): Mono<Boolean>

    fun checkAndMutate(checkAndMutate: CheckAndMutate): Mono<CheckAndMutateResult>

    fun increment(increment: Increment): Mono<Result>

    fun scan(
        scan: Scan,
        limit: Int,
    ): Mono<List<Result>>

    companion object {
        fun create(table: AsyncTable<AdvancedScanResultConsumer>): HBaseTable = HBaseAsyncTable(table)

        fun create(table: Table): HBaseTable = HBaseSyncTable(table)
    }
}
