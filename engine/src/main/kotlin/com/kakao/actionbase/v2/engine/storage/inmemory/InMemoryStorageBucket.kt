package com.kakao.actionbase.v2.engine.storage.inmemory

import com.kakao.actionbase.v2.engine.storage.Delete
import com.kakao.actionbase.v2.engine.storage.Get
import com.kakao.actionbase.v2.engine.storage.Increment
import com.kakao.actionbase.v2.engine.storage.Put
import com.kakao.actionbase.v2.engine.storage.Scan
import com.kakao.actionbase.v2.engine.storage.StorageBucket
import com.kakao.actionbase.v2.engine.storage.result.GetResult
import com.kakao.actionbase.v2.engine.storage.result.ScanResult

import java.util.concurrent.ConcurrentNavigableMap
import java.util.concurrent.ConcurrentSkipListMap

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class InMemoryStorageBucket(
    private val name: String,
    private val map: ConcurrentNavigableMap<ByteArray, ByteArray> = ConcurrentSkipListMap(compareBy { it.contentToString() }),
) : StorageBucket {
    override fun put(put: Put): Mono<Void> =
        Mono
            .fromCallable {
                map[put.key] = put.value
            }.then()

    override fun get(get: Get): Mono<GetResult> =
        Mono.fromCallable {
            map[get.key]?.let {
                GetResult.Found(it)
            } ?: GetResult.NotFound
        }

    override fun delete(delete: Delete): Mono<Void> =
        Mono
            .fromCallable {
                map.remove(delete.key)
            }.then()

    override fun scan(scan: Scan): Flux<ScanResult> =
        Flux
            .fromIterable(map.entries)
            .filter { it.key.startsWith(scan.prefix) }
            .map { ScanResult.Data(it.key, it.value) }
            .take(scan.limit.toLong())

    override fun increment(increment: Increment): Mono<Long> =
        Mono.fromCallable {
            map
                .compute(increment.key) { _, value ->
                    val longValue = value?.let { bytesToLong(it) } ?: 0L
                    longToBytes(longValue + increment.amount)
                }!!
                .let {
                    bytesToLong(it)
                }
        }

    private fun bytesToLong(bytes: ByteArray): Long {
        var result = 0L
        for (i in 0..7) {
            result = result shl 8
            result = result or (bytes[i].toLong() and 0xFF)
        }
        return result
    }

    private fun longToBytes(long: Long): ByteArray {
        val result = ByteArray(8)
        var l = long
        for (i in 7 downTo 0) {
            result[i] = (l and 0xFF).toByte()
            l = l shr 8
        }
        return result
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (prefix.size > this.size) {
            return false
        }
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) {
                return false
            }
        }
        return true
    }
}
