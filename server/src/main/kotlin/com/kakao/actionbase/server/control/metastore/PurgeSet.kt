package com.kakao.actionbase.server.control.metastore

import com.kakao.actionbase.v2.engine.metastore.purge.PurgeRow
import com.kakao.actionbase.v2.engine.metastore.purge.SkippedRow
import com.kakao.actionbase.v2.engine.metastore.purge.UndecodableKey

import java.time.Instant

/** What `candidates` was asked for. `metastore` is a configured name, not a url. */
data class PurgeQuery(
    val metastore: String,
    val service: String,
    val olderThanDays: Long = DEFAULT_OLDER_THAN_DAYS,
    val maxRows: Int = DEFAULT_MAX_ROWS,
    val maxScan: Int = DEFAULT_MAX_SCAN,
    val cursor: Long = 0,
) {
    companion object {
        const val DEFAULT_OLDER_THAN_DAYS = 30L
        const val DEFAULT_MAX_ROWS = 500
        const val DEFAULT_MAX_SCAN = 50_000
    }
}

/**
 * One page of a purge, and the only document the three endpoints exchange.
 *
 * `candidates` returns this, and `execute` and `restore` accept it back unchanged. There is no
 * reduced projection for either, so a client saves a response and later posts it as it stands - and
 * because the rows reach the client before anything is deleted, the saved document is the backup.
 *
 * `metastore` and `table` are resolved coordinates rather than the name that was asked for: a file
 * read months later should say which database it came from, and both are checked against the
 * configured list before anything is opened.
 *
 * `scanned`, `nextCursor` and `undecodable` are informational coming back in. They travel with the
 * document so an operator's record of what was skipped survives next to the backup.
 */
data class PurgeSet(
    val metastore: String,
    val table: String,
    val service: String,
    val generatedAt: Instant,
    val scanned: Int,
    val nextCursor: Long?,
    val rows: List<PurgeRow>,
    val undecodable: List<UndecodableKey>,
)

/** What `execute` or `restore` did. */
data class PurgeResult(
    val metastore: String,
    val table: String,
    val service: String,
    val requested: Int,
    val applied: Int,
    val skipped: List<SkippedRow>,
)
