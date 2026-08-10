package com.kakao.actionbase.v2.engine.metastore.purge

import java.time.LocalDateTime

/**
 * One metastore row, as it sits in the table.
 *
 * A row is its `k` and `v`. The audit columns ride along because a restore has to write the row
 * back as it was, not as it would be if inserted today. There is no primary key here on purpose:
 * the id is a cursor for paging a scan, not part of what identifies a row to delete.
 */
data class PurgeRow(
    val k: String,
    val v: String,
    val createdAt: LocalDateTime,
    val createdBy: String,
    val modifiedAt: LocalDateTime,
    val modifiedBy: String,
    val updateTs: LocalDateTime,
)

/**
 * A row the scan refused to read. It is reported rather than skipped silently, and it is never
 * deleted - the purge does not remove what it could not understand.
 */
data class UndecodableKey(
    val k: String,
    val reason: String,
)

/**
 * One page of a scan.
 *
 * [scanned] is how many rows were walked to find [rows], which is what tells an operator how dense
 * the tombstones are. [nextCursor] is null once the table is exhausted.
 */
data class PurgeScan(
    val rows: List<PurgeRow>,
    val undecodable: List<UndecodableKey>,
    val scanned: Int,
    val nextCursor: Long?,
)
