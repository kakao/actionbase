package com.kakao.actionbase.v2.engine.storage.memory

import com.kakao.actionbase.v2.core.code.hbase.Constants
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTable

import java.util.Arrays
import java.util.NavigableMap
import java.util.concurrent.ConcurrentHashMap

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.KeyValue
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.CheckAndMutate
import org.apache.hadoop.hbase.client.CheckAndMutateResult
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder
import org.apache.hadoop.hbase.client.Delete
import org.apache.hadoop.hbase.client.Get
import org.apache.hadoop.hbase.client.Increment
import org.apache.hadoop.hbase.client.Mutation
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.client.Result
import org.apache.hadoop.hbase.client.Scan
import org.apache.hadoop.hbase.client.TableDescriptor
import org.apache.hadoop.hbase.client.TableDescriptorBuilder
import org.apache.hadoop.hbase.filter.PrefixFilter
import org.apache.hadoop.hbase.util.Bytes

import reactor.core.publisher.Mono

class MemoryHBaseTable(
    namespace: String,
    tableName: String,
) : HBaseTable {
    override val name: TableName = TableName.valueOf(namespace, tableName)

    override val configuration: Configuration = Configuration()

    override val descriptor: Mono<TableDescriptor> =
        Mono.fromCallable {
            TableDescriptorBuilder
                .newBuilder(name)
                .setColumnFamily(ColumnFamilyDescriptorBuilder.of(Constants.DEFAULT_COLUMN_FAMILY))
                .build()
        }

    private val rows = ConcurrentHashMap<ByteArrayKey, ConcurrentHashMap<ByteArrayKey, ByteArray>>()

    override fun get(get: Get): Mono<Result> =
        Mono.fromCallable {
            val rowKey = ByteArrayKey.wrap(get.row)
            val row = rows[rowKey] ?: return@fromCallable Result.EMPTY_RESULT
            val cells = buildCells(get.row, row, get.getFamilyMap())
            if (cells.isEmpty()) Result.EMPTY_RESULT else Result.create(cells)
        }

    override fun get(gets: List<Get>): Mono<List<Result>> =
        Mono.fromCallable {
            gets.map { get ->
                val rowKey = ByteArrayKey.wrap(get.row)
                val row = rows[rowKey]
                if (row == null) {
                    Result.EMPTY_RESULT
                } else {
                    val cells = buildCells(get.row, row, get.getFamilyMap())
                    if (cells.isEmpty()) Result.EMPTY_RESULT else Result.create(cells)
                }
            }
        }

    override fun put(put: Put): Mono<Void> =
        Mono.fromRunnable {
            val rowKey = ByteArrayKey.wrap(put.row)
            val row = rows.computeIfAbsent(rowKey) { ConcurrentHashMap() }
            applyMutation(row, put)
        }

    override fun delete(delete: Delete): Mono<Void> =
        Mono.fromRunnable {
            val rowKey = ByteArrayKey.wrap(delete.row)
            val familyMap = delete.familyCellMap
            if (familyMap.isEmpty()) {
                rows.remove(rowKey)
                return@fromRunnable
            }
            val row = rows[rowKey] ?: return@fromRunnable
            applyDelete(row, familyMap)
            if (row.isEmpty()) {
                rows.remove(rowKey)
            }
        }

    override fun batch(deferredRequests: List<Any>): Mono<Void> =
        Mono.fromRunnable {
            deferredRequests.forEach { req ->
                when (req) {
                    is Put -> put(req).block()
                    is Delete -> delete(req).block()
                    is Increment -> increment(req).block()
                    else -> Unit
                }
            }
        }

    override fun exists(get: Get): Mono<Boolean> = get(get).map { !it.isEmpty }

    override fun checkAndMutate(checkAndMutate: CheckAndMutate): Mono<CheckAndMutateResult> =
        Mono.fromCallable {
            val rowKey = ByteArrayKey.wrap(checkAndMutate.row)
            val row = rows.computeIfAbsent(rowKey) { ConcurrentHashMap() }
            val success =
                synchronized(row) {
                    val qualifier = checkAndMutate.qualifier
                    val current = if (qualifier == null) null else row[ByteArrayKey.wrap(qualifier)]
                    if (compare(checkAndMutate, current)) {
                        val action = checkAndMutate.action
                        when (action) {
                            is Put -> applyMutation(row, action)
                            is Delete -> applyDelete(row, action.familyCellMap)
                            is Increment -> applyIncrement(row, action)
                            else -> Unit
                        }
                        true
                    } else {
                        false
                    }
                }
            CheckAndMutateResult(success, Result.EMPTY_RESULT)
        }

    override fun increment(increment: Increment): Mono<Result> =
        Mono.fromCallable {
            val rowKey = ByteArrayKey.wrap(increment.row)
            val row = rows.computeIfAbsent(rowKey) { ConcurrentHashMap() }
            val updated = synchronized(row) { applyIncrement(row, increment) }
            if (updated.isEmpty()) Result.EMPTY_RESULT else Result.create(updated)
        }

    override fun scan(
        scan: Scan,
        limit: Int,
    ): Mono<List<Result>> =
        Mono.fromCallable {
            val startRow = scan.startRow
            val stopRow = scan.stopRow
            val includeStart = scan.includeStartRow()
            val includeStop = scan.includeStopRow()
            val prefix = extractPrefix(scan)
            val familyMap = scan.familyMap
            val effectiveLimit = if (limit > 0) limit else Int.MAX_VALUE
            val keys =
                rows.keys
                    .filter { key ->
                        matchesPrefix(key.bytes, prefix) &&
                            matchesRange(key.bytes, startRow, stopRow, includeStart, includeStop)
                    }.sortedWith { a, b -> Arrays.compareUnsigned(a.bytes, b.bytes) }
            keys
                .take(effectiveLimit)
                .mapNotNull { key ->
                    val row = rows[key] ?: return@mapNotNull null
                    val cells = buildCells(key.bytes, row, familyMap)
                    if (cells.isEmpty()) null else Result.create(cells)
                }
        }

    private fun buildCells(
        row: ByteArray,
        columns: ConcurrentHashMap<ByteArrayKey, ByteArray>,
        familyMap: Map<ByteArray, java.util.NavigableSet<ByteArray>>?,
    ): List<KeyValue> {
        if (familyMap == null || familyMap.isEmpty()) {
            return columns.map { (qualifier, value) ->
                KeyValue(row, Constants.DEFAULT_COLUMN_FAMILY, qualifier.bytes, value)
            }
        }
        val qualifiers = familyMap.values.flatMap { it.toList() }
        return qualifiers.mapNotNull { qualifier ->
            val value = columns[ByteArrayKey.wrap(qualifier)] ?: return@mapNotNull null
            KeyValue(row, Constants.DEFAULT_COLUMN_FAMILY, qualifier, value)
        }
    }

    private fun applyMutation(
        row: ConcurrentHashMap<ByteArrayKey, ByteArray>,
        mutation: Mutation,
    ) {
        mutation.familyCellMap.forEach { (_, cells) ->
            cells.forEach { cell ->
                val qualifier =
                    ByteArrayKey.wrap(
                        cell.qualifierArray.copyOfRange(
                            cell.qualifierOffset,
                            cell.qualifierOffset + cell.qualifierLength,
                        ),
                    )
                val value = cell.valueArray.copyOfRange(cell.valueOffset, cell.valueOffset + cell.valueLength)
                row[qualifier] = value
            }
        }
    }

    private fun applyDelete(
        row: ConcurrentHashMap<ByteArrayKey, ByteArray>,
        familyMap: NavigableMap<ByteArray, MutableList<org.apache.hadoop.hbase.Cell>>,
    ) {
        if (familyMap.isEmpty()) {
            row.clear()
            return
        }
        familyMap.values.flatten().forEach { cell ->
            val qualifier =
                ByteArrayKey.wrap(
                    cell.qualifierArray.copyOfRange(
                        cell.qualifierOffset,
                        cell.qualifierOffset + cell.qualifierLength,
                    ),
                )
            row.remove(qualifier)
        }
    }

    private fun applyIncrement(
        row: ConcurrentHashMap<ByteArrayKey, ByteArray>,
        increment: Increment,
    ): List<KeyValue> {
        val updated = mutableListOf<KeyValue>()
        increment.familyMapOfLongs.forEach { (_, qualifiers) ->
            qualifiers.forEach { (qualifier, delta) ->
                val key = ByteArrayKey.wrap(qualifier)
                val current = row[key]
                val currentValue = if (current == null) 0L else Bytes.toLong(current)
                val next = currentValue + delta
                val bytes = Bytes.toBytes(next)
                row[key] = bytes
                updated += KeyValue(increment.row, Constants.DEFAULT_COLUMN_FAMILY, qualifier, bytes)
            }
        }
        return updated
    }

    private fun compare(
        checkAndMutate: CheckAndMutate,
        current: ByteArray?,
    ): Boolean {
        val value = checkAndMutate.value
        return when (checkAndMutate.compareOp) {
            org.apache.hadoop.hbase.CompareOperator.NO_OP -> true
            org.apache.hadoop.hbase.CompareOperator.EQUAL -> {
                if (value == null) {
                    current == null
                } else {
                    current != null && Arrays.equals(current, value)
                }
            }
            org.apache.hadoop.hbase.CompareOperator.NOT_EQUAL -> {
                if (value == null) {
                    current != null
                } else {
                    current == null || !Arrays.equals(current, value)
                }
            }
            org.apache.hadoop.hbase.CompareOperator.GREATER -> {
                current != null && value != null && Arrays.compareUnsigned(current, value) > 0
            }
            org.apache.hadoop.hbase.CompareOperator.GREATER_OR_EQUAL -> {
                current != null && value != null && Arrays.compareUnsigned(current, value) >= 0
            }
            org.apache.hadoop.hbase.CompareOperator.LESS -> {
                current != null && value != null && Arrays.compareUnsigned(current, value) < 0
            }
            org.apache.hadoop.hbase.CompareOperator.LESS_OR_EQUAL -> {
                current != null && value != null && Arrays.compareUnsigned(current, value) <= 0
            }
        }
    }

    private fun matchesPrefix(
        key: ByteArray,
        prefix: ByteArray?,
    ): Boolean {
        if (prefix == null || prefix.isEmpty()) return true
        if (key.size < prefix.size) return false
        return key.copyOfRange(0, prefix.size).contentEquals(prefix)
    }

    private fun matchesRange(
        key: ByteArray,
        start: ByteArray?,
        stop: ByteArray?,
        includeStart: Boolean,
        includeStop: Boolean,
    ): Boolean {
        if (start != null && start.isNotEmpty()) {
            val cmp = Arrays.compareUnsigned(key, start)
            if (cmp < 0 || (cmp == 0 && !includeStart)) return false
        }
        if (stop != null && stop.isNotEmpty()) {
            val cmp = Arrays.compareUnsigned(key, stop)
            if (cmp > 0 || (cmp == 0 && !includeStop)) return false
        }
        return true
    }

    private fun extractPrefix(scan: Scan): ByteArray? {
        val filter = scan.filter
        return if (filter is PrefixFilter) filter.prefix else null
    }

    private data class ByteArrayKey(
        val bytes: ByteArray,
    ) {
        companion object {
            fun wrap(bytes: ByteArray): ByteArrayKey = ByteArrayKey(bytes.copyOf())
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ByteArrayKey) return false
            return Arrays.equals(bytes, other.bytes)
        }

        override fun hashCode(): Int = Arrays.hashCode(bytes)
    }
}
