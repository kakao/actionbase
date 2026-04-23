package com.kakao.actionbase.engine.storage

import com.kakao.actionbase.core.storage.StorageOp

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

import org.apache.hadoop.hbase.Cell
import org.apache.hadoop.hbase.client.Delete
import org.apache.hadoop.hbase.client.Increment
import org.apache.hadoop.hbase.client.Mutation
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.util.Bytes
import org.slf4j.LoggerFactory

/**
 * Single-owner appender used under a Reactor chain (per-RMW in V3, per-edge in V2).
 * Not safe to share across parallel publishers — signals must arrive serially.
 */
class StorageOpCollector(
    private val maxOps: Int = DEFAULT_MAX_OPS,
) {
    private val ops = mutableListOf<StorageOp>()
    private var truncated = false

    fun collect(
        request: Any,
        table: String,
    ) {
        if (ops.size >= maxOps) {
            truncated = true
            return
        }
        ops.add(toStorageOp(request, table))
    }

    fun collectAll(
        requests: Collection<Any>,
        table: String,
    ) {
        requests.forEach { collect(it, table) }
    }

    fun snapshot(): List<StorageOp> = ops.toList()

    fun isTruncated(): Boolean = truncated

    fun toContextMap(): Map<String, Any> =
        buildMap {
            put(CONTEXT_KEY, snapshot())
            if (truncated) put(TRUNCATED_KEY, true)
        }

    private fun toStorageOp(
        request: Any,
        table: String,
    ): StorageOp =
        when (request) {
            is Put -> StorageOp.Put(table, request.row.toBase64(), extractCells(request))
            is Delete -> StorageOp.Delete(table, request.row.toBase64(), extractCells(request))
            is Increment -> StorageOp.Increment(table, request.row.toBase64(), extractDeltas(request))
            else -> {
                val type = request::class.qualifiedName ?: request::class.java.name
                if (warnedTypes.add(type)) {
                    log.warn("StorageOpCollector received unsupported request type: {}", type)
                }
                StorageOp.Unknown(table, type = type)
            }
        }

    private fun extractCells(mutation: Mutation): List<StorageOp.Cell> =
        mutation.familyCellMap.values
            .asSequence()
            .flatMap { it }
            .map { cell ->
                StorageOp.Cell(
                    family = cell.familyBase64(),
                    qualifier = cell.qualifierBase64(),
                    value = if (cell.type == Cell.Type.Put) cell.valueBase64() else null,
                    type = cell.type.name,
                )
            }.toList()

    private fun extractDeltas(increment: Increment): List<StorageOp.Delta> =
        increment.familyCellMap.values
            .asSequence()
            .flatMap { it }
            .map { cell ->
                StorageOp.Delta(
                    family = cell.familyBase64(),
                    qualifier = cell.qualifierBase64(),
                    delta = Bytes.toLong(cell.valueArray, cell.valueOffset, cell.valueLength),
                )
            }.toList()

    private fun Cell.familyBase64(): String = encodeBase64(familyArray, familyOffset, familyLength.toInt())

    private fun Cell.qualifierBase64(): String = encodeBase64(qualifierArray, qualifierOffset, qualifierLength)

    private fun Cell.valueBase64(): String = encodeBase64(valueArray, valueOffset, valueLength)

    private fun ByteArray.toBase64(): String = BASE64.encodeToString(this)

    private fun encodeBase64(
        src: ByteArray,
        offset: Int,
        length: Int,
    ): String {
        val encoded = BASE64.encode(ByteBuffer.wrap(src, offset, length))
        return String(encoded.array(), encoded.arrayOffset(), encoded.remaining(), StandardCharsets.ISO_8859_1)
    }

    companion object {
        const val DEFAULT_MAX_OPS: Int = 1024
        const val CONTEXT_KEY: String = "storageOps"
        const val TRUNCATED_KEY: String = "storageOpsTruncated"
        private val BASE64 = Base64.getEncoder()
        private val log = LoggerFactory.getLogger(StorageOpCollector::class.java)
        private val warnedTypes = ConcurrentHashMap.newKeySet<String>()
    }
}
