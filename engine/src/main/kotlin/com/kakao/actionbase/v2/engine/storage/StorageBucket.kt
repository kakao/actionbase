package com.kakao.actionbase.v2.engine.storage

import com.kakao.actionbase.v2.engine.storage.result.GetResult
import com.kakao.actionbase.v2.engine.storage.result.ScanResult

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface StorageBucket {
    fun put(put: Put): Mono<Void>

    fun get(get: Get): Mono<GetResult>

    fun delete(delete: Delete): Mono<Void>

    fun scan(scan: Scan): Flux<ScanResult>

    fun increment(increment: Increment): Mono<Long>

    fun batch(operations: List<StorageOperation>): Mono<Void>
}
