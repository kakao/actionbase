package com.kakao.actionbase.v2.engine.storage

sealed interface StorageOperation {
    data class PutOp(
        val put: Put,
    ) : StorageOperation

    data class DeleteOp(
        val delete: Delete,
    ) : StorageOperation

    data class IncrementOp(
        val increment: Increment,
    ) : StorageOperation
}
