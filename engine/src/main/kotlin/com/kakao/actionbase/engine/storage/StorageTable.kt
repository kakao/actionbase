package com.kakao.actionbase.engine.storage

import com.kakao.actionbase.core.storage.HBaseRecord
import com.kakao.actionbase.core.storage.MutationRequest

import reactor.core.publisher.Mono

interface StorageTable {
    fun get(key: ByteArray): Mono<ByteArray?>

    fun get(keys: List<ByteArray>): Mono<List<HBaseRecord>>

    fun put(
        key: ByteArray,
        value: ByteArray,
    ): Mono<Void>

    fun delete(key: ByteArray): Mono<Void>

    fun scan(
        prefix: ByteArray,
        limit: Int,
        start: ByteArray?,
        stop: ByteArray?,
    ): Mono<List<HBaseRecord>>

    fun increment(
        key: ByteArray,
        delta: Long,
    ): Mono<Long>

    fun batch(requests: List<MutationRequest>): Mono<Void>

    fun exists(key: ByteArray): Mono<Boolean>

    fun setIfNotExists(
        key: ByteArray,
        value: ByteArray,
    ): Mono<Boolean>

    fun deleteIfEquals(
        key: ByteArray,
        expectedValue: ByteArray,
    ): Mono<Boolean>
}
