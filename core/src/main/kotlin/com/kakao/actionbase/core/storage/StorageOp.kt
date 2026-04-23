package com.kakao.actionbase.core.storage

sealed class StorageOp {
    abstract val kind: String
    abstract val table: String

    data class Put(
        override val table: String,
        val row: String,
        val cells: List<Cell>,
    ) : StorageOp() {
        override val kind: String = "Put"
    }

    data class Delete(
        override val table: String,
        val row: String,
        val cells: List<Cell>,
    ) : StorageOp() {
        override val kind: String = "Delete"
    }

    data class Increment(
        override val table: String,
        val row: String,
        val deltas: List<Delta>,
    ) : StorageOp() {
        override val kind: String = "Increment"
    }

    data class Unknown(
        override val table: String,
        val type: String,
    ) : StorageOp() {
        override val kind: String = "Unknown"
    }

    data class Cell(
        val family: String,
        val qualifier: String,
        val value: String?,
        val type: String,
    )

    data class Delta(
        val family: String,
        val qualifier: String,
        val delta: Long,
    )
}
