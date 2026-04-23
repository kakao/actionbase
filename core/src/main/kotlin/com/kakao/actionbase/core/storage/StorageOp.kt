package com.kakao.actionbase.core.storage

sealed class StorageOp {
    abstract val table: String
    abstract val rowHex: String

    data class Put(
        override val table: String,
        override val rowHex: String,
        val cells: List<Cell>,
    ) : StorageOp()

    data class Delete(
        override val table: String,
        override val rowHex: String,
        val cells: List<Cell>,
    ) : StorageOp()

    data class Increment(
        override val table: String,
        override val rowHex: String,
        val deltas: List<Delta>,
    ) : StorageOp()

    data class Cell(
        val familyHex: String,
        val qualifierHex: String,
        val valueHex: String?,
    )

    data class Delta(
        val familyHex: String,
        val qualifierHex: String,
        val delta: Long,
    )
}
