package com.kakao.actionbase.engine.storage

import com.kakao.actionbase.core.storage.StorageOp

import java.util.concurrent.CopyOnWriteArrayList

class StorageOpCollector(
    private val maxOps: Int = DEFAULT_MAX_OPS,
) {
    private val ops = CopyOnWriteArrayList<StorageOp>()

    @Volatile
    private var truncated = false

    @Suppress("UNUSED_PARAMETER")
    fun collect(
        request: Any,
        table: String,
    ) {
        if (ops.size >= maxOps) {
            truncated = true
            return
        }
        // No-op until backend interpretation is added.
    }

    fun collectAll(
        requests: Collection<Any>,
        table: String,
    ) {
        requests.forEach { collect(it, table) }
    }

    fun snapshot(): List<StorageOp> = ops.toList()

    fun isTruncated(): Boolean = truncated

    companion object {
        const val DEFAULT_MAX_OPS: Int = 1024
    }
}
