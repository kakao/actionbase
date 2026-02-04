package com.kakao.actionbase.v2.engine.storage.memory

import com.kakao.actionbase.engine.datastore.impl.ByteArrayStore
import com.kakao.actionbase.v2.core.code.hbase.Constants
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTable

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Arrays
import java.util.NavigableMap

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

    private val store = ByteArrayStore()

    override fun get(get: Get): Mono<Result> =
        Mono.fromCallable {
            val cells = readCells(get.row, get.getFamilyMap())
            if (cells.isEmpty()) Result.EMPTY_RESULT else Result.create(cells)
        }

    override fun get(gets: List<Get>): Mono<List<Result>> =
        Mono.fromCallable {
            gets.map { get ->
                val cells = readCells(get.row, get.getFamilyMap())
                if (cells.isEmpty()) Result.EMPTY_RESULT else Result.create(cells)
            }
        }

    override fun put(put: Put): Mono<Void> =
        Mono.fromRunnable {
            applyMutation(put)
        }

    override fun delete(delete: Delete): Mono<Void> =
        Mono.fromRunnable {
            val familyMap = delete.familyCellMap
            if (familyMap.isEmpty()) {
                deleteRow(delete.row)
                return@fromRunnable
            }
            applyDelete(delete.row, familyMap)
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
            val qualifier = checkAndMutate.qualifier
            val current =
                if (qualifier == null) {
                    null
                } else {
                    store[encodeKey(checkAndMutate.row, qualifier)]
                }
            val success =
                if (compare(checkAndMutate, current)) {
                    val action = checkAndMutate.action
                    when (action) {
                        is Put -> applyMutation(action)
                        is Delete -> {
                            if (action.familyCellMap.isEmpty()) {
                                deleteRow(action.row)
                            } else {
                                applyDelete(action.row, action.familyCellMap)
                            }
                        }
                        is Increment -> applyIncrement(action)
                        else -> Unit
                    }
                    true
                } else {
                    false
                }
            CheckAndMutateResult(success, Result.EMPTY_RESULT)
        }

    override fun increment(increment: Increment): Mono<Result> =
        Mono.fromCallable {
            val updated = applyIncrement(increment)
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
            val records = store.prefixScan(byteArrayOf())
            val grouped =
                records
                    .map { record ->
                        val (row, qualifier) = decodeKey(record.key)
                        Triple(row, qualifier, record.value)
                    }.filter { (row, _, _) ->
                        matchesPrefix(row, prefix) &&
                            matchesRange(row, startRow, stopRow, includeStart, includeStop)
                    }.groupBy { it.first }

            grouped
                .entries
                .sortedWith { a, b -> Arrays.compareUnsigned(a.key, b.key) }
                .take(effectiveLimit)
                .mapNotNull { (row, entries) ->
                    val cells =
                        entries.mapNotNull { (_, qualifier, value) ->
                            if (isQualifierAllowed(qualifier, familyMap)) {
                                KeyValue(row, Constants.DEFAULT_COLUMN_FAMILY, qualifier, value)
                            } else {
                                null
                            }
                        }
                    if (cells.isEmpty()) null else Result.create(cells)
                }
        }

    private fun readCells(
        row: ByteArray,
        familyMap: Map<ByteArray, java.util.NavigableSet<ByteArray>>?,
    ): List<KeyValue> {
        val prefix = encodeRowPrefix(row)
        val records = store.prefixScan(prefix)
        if (records.isEmpty()) return emptyList()

        return records.mapNotNull { record ->
            val (_, qualifier) = decodeKey(record.key)
            if (isQualifierAllowed(qualifier, familyMap)) {
                KeyValue(row, Constants.DEFAULT_COLUMN_FAMILY, qualifier, record.value)
            } else {
                null
            }
        }
    }

    private fun applyMutation(mutation: Mutation) {
        mutation.familyCellMap.forEach { (_, cells) ->
            cells.forEach { cell ->
                val qualifier =
                    cell.qualifierArray.copyOfRange(
                        cell.qualifierOffset,
                        cell.qualifierOffset + cell.qualifierLength,
                    )
                val value = cell.valueArray.copyOfRange(cell.valueOffset, cell.valueOffset + cell.valueLength)
                store[encodeKey(mutation.row, qualifier)] = value
            }
        }
    }

    private fun applyDelete(
        row: ByteArray,
        familyMap: NavigableMap<ByteArray, MutableList<org.apache.hadoop.hbase.Cell>>,
    ) {
        familyMap.values.flatten().forEach { cell ->
            val qualifier =
                cell.qualifierArray.copyOfRange(
                    cell.qualifierOffset,
                    cell.qualifierOffset + cell.qualifierLength,
                )
            store.remove(encodeKey(row, qualifier))
        }
    }

    private fun deleteRow(row: ByteArray) {
        val prefix = encodeRowPrefix(row)
        store.prefixScan(prefix).forEach { record ->
            store.remove(record.key)
        }
    }

    private fun applyIncrement(increment: Increment): List<KeyValue> {
        val updated = mutableListOf<KeyValue>()
        increment.familyMapOfLongs.forEach { (_, qualifiers) ->
            qualifiers.forEach { (qualifier, delta) ->
                val key = encodeKey(increment.row, qualifier)
                val next = store.increment(key, delta)
                val bytes = Bytes.toBytes(next)
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

    private fun isQualifierAllowed(
        qualifier: ByteArray,
        familyMap: Map<ByteArray, java.util.NavigableSet<ByteArray>>?,
    ): Boolean {
        if (familyMap == null || familyMap.isEmpty()) return true
        return familyMap.values.any { it.any { q -> Arrays.equals(q, qualifier) } }
    }

    private fun encodeRowPrefix(row: ByteArray): ByteArray {
        val buffer =
            ByteBuffer
                .allocate(4 + row.size)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(row.size)
                .put(row)
        return buffer.array()
    }

    private fun encodeKey(
        row: ByteArray,
        qualifier: ByteArray,
    ): ByteArray {
        val buffer =
            ByteBuffer
                .allocate(4 + row.size + qualifier.size)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(row.size)
                .put(row)
                .put(qualifier)
        return buffer.array()
    }

    private fun decodeKey(key: ByteArray): Pair<ByteArray, ByteArray> {
        val buffer = ByteBuffer.wrap(key).order(ByteOrder.BIG_ENDIAN)
        val rowSize = buffer.int
        val row = ByteArray(rowSize)
        buffer.get(row)
        val qualifier = ByteArray(buffer.remaining())
        buffer.get(qualifier)
        return row to qualifier
    }
}
