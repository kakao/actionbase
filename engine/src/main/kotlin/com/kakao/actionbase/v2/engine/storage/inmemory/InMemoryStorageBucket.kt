package com.kakao.actionbase.v2.engine.storage.inmemory

import com.kakao.actionbase.v2.engine.storage.StorageBucket
import com.kakao.actionbase.v2.engine.storage.StorageOperation

import java.util.concurrent.ConcurrentNavigableMap
import java.util.concurrent.ConcurrentSkipListMap

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

// ... other imports ...

class InMemoryStorageBucket(
    private val name: String,
    private val map: ConcurrentNavigableMap<ByteArray, ByteArray> = ConcurrentSkipListMap(compareBy { it.contentToString() }),
) : StorageBucket {
    // ... existing methods ...

    override fun batch(operations: List<StorageOperation>): Mono<Void> {
        return Flux
            .fromIterable(operations)
            .flatMap { operation ->
                when (operation) {
                    is StorageOperation.PutOp -> put(operation.put)
                    is StorageOperation.DeleteOp -> delete(operation.delete)
                    is StorageOperation.IncrementOp -> increment(operation.increment).then() // Convert Mono<Long> to Mono<Void>
                }
            }.then() // waits for all operations to complete
    }

    // ... existing private helper functions ...
}
