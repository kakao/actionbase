package com.kakao.actionbase.v2.engine.metastore.purge

/** Why a row named in a request was not acted on. */
enum class SkipReason {
    /** The key is there but holds different bytes: the row came back to life since it was listed. */
    CHANGED,

    /** The key is gone, which is what a repeated `execute` sees. */
    ABSENT,

    /** A restore found the key already occupied, and will not write over what is there now. */
    PRESENT,
}

data class SkippedRow(
    val k: String,
    val reason: SkipReason,
)

/**
 * What a delete or a restore actually did.
 *
 * [requested] and [applied] differ exactly when rows were skipped, and every skipped row says why -
 * an operator facing a partial result needs to know whether something changed underneath them or
 * whether they are looking at a retry that had nothing left to do.
 */
data class PurgeOutcome(
    val requested: Int,
    val applied: Int,
    val skipped: List<SkippedRow>,
)
