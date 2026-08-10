package com.kakao.actionbase.v2.engine.metastore.purge

import java.sql.Connection
import java.time.LocalDateTime

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Deletes tombstoned rows from a metastore table, and puts them back.
 *
 * Standalone by design: it shares no code with `Graph`, `JdbcHashLabel` or anything else that
 * serves the metastore. It opens a connection, reads columns, and reads two fields out of the
 * encoded ones. Deleting rows out from under the running metastore is not an ability the serving
 * path should grow, and a tool that dies with JDBC should not be entangled with what is being
 * retired around it.
 */
class MetastorePurge(
    val target: MetastoreTarget,
    connections: () -> Connection,
) {
    private val table = PurgeTable(target.table)

    // Opening a Database does not open a connection - Exposed asks for one per transaction - so
    // this is safe to build at startup for a metastore that may be unreachable.
    private val database: Database = Database.connect(getNewConnection = connections)

    /**
     * Walks the table by primary key and collects rows of [service] that are inactive and settled
     * before [updatedBefore].
     *
     * Two limits, because there are two costs. [maxRows] caps what comes back, and [maxScan] caps
     * how far the walk goes when tombstones are sparse - without it a table of live rows would be
     * read end to end to fill one page. Either limit leaves a `nextCursor` to resume from, and a
     * null one means the table ran out.
     *
     * The age filter is a `WHERE` clause on an indexed column, so rows too young to purge are never
     * read. Being inactive is not, which is the whole problem this tool exists for.
     */
    fun scan(
        service: String,
        updatedBefore: LocalDateTime,
        maxRows: Int,
        maxScan: Int,
        cursor: Long = 0,
    ): PurgeScan {
        require(maxRows > 0) { "maxRows must be positive, got $maxRows" }
        require(maxScan >= maxRows) { "maxScan ($maxScan) must be at least maxRows ($maxRows)" }

        val found = mutableListOf<PurgeRow>()
        val undecodable = mutableListOf<UndecodableKey>()
        var scanned = 0
        var cursorNow = cursor
        var exhausted = false

        transaction(database) {
            while (found.size < maxRows && scanned < maxScan && !exhausted) {
                val page = page(cursorNow, updatedBefore, minOf(PAGE_SIZE, maxScan - scanned))
                exhausted = page.isEmpty()
                // Stops on the row that fills the page rather than draining it, so the cursor is
                // the last row actually accounted for. Advancing past rows that were fetched but
                // never returned would skip them on resume.
                for ((id, row) in page) {
                    cursorNow = id
                    scanned++
                    when (val verdict = verdict(row, service)) {
                        Verdict.Purge -> found += row
                        Verdict.Keep -> Unit
                        is Verdict.Unreadable -> undecodable += UndecodableKey(row.k, verdict.reason)
                    }
                    if (found.size >= maxRows) break
                }
            }
        }

        return PurgeScan(
            rows = found,
            undecodable = undecodable,
            scanned = scanned,
            nextCursor = cursorNow.takeUnless { exhausted },
        )
    }

    /**
     * Deletes exactly the rows it was given, matching on `k` and `v` together.
     *
     * Matching the value is what makes this safe to hand a file saved earlier: a tombstone that was
     * recreated since holds different bytes and is reported as `CHANGED` rather than destroyed, and
     * a row a previous call already removed is `ABSENT`, so repeating a request that was answered
     * but never seen changes nothing.
     *
     * The two predicates are one expression on purpose. Splitting them into separate statements
     * inside the lambda would leave only the last as the predicate, and the delete would match on
     * value alone.
     */
    fun delete(rows: List<PurgeRow>): PurgeOutcome =
        transaction(database) {
            val unmatched = mutableListOf<String>()
            var applied = 0
            rows.forEach { row ->
                val deleted = table.deleteWhere { (table.k eq row.k) and (table.v eq row.v) }
                if (deleted > 0) applied++ else unmatched += row.k
            }
            PurgeOutcome(requested = rows.size, applied = applied, skipped = classify(unmatched))
        }

    /** One query tells `CHANGED` from `ABSENT`, and it only runs when something did not match. */
    private fun classify(unmatched: List<String>): List<SkippedRow> {
        if (unmatched.isEmpty()) return emptyList()
        val stillThere = table.select { table.k inList unmatched }.mapTo(mutableSetOf()) { it[table.k] }
        return unmatched.map { SkippedRow(it, if (it in stillThere) SkipReason.CHANGED else SkipReason.ABSENT) }
    }

    /**
     * Writes rows back, and only where the key is free.
     *
     * A key that is occupied is left as it is: whatever holds it now was created after the purge,
     * and restoring an old tombstone over live metadata would be a worse outcome than a restore
     * that reports it could not finish.
     */
    fun restore(rows: List<PurgeRow>): PurgeOutcome =
        transaction(database) {
            val occupied = table.select { table.k inList rows.map { it.k } }.mapTo(mutableSetOf()) { it[table.k] }
            val (present, free) = rows.partition { it.k in occupied }
            free.forEach { row ->
                table.insert {
                    it[k] = row.k
                    it[v] = row.v
                    it[createdAt] = row.createdAt
                    it[createdBy] = row.createdBy
                    it[modifiedAt] = row.modifiedAt
                    it[modifiedBy] = row.modifiedBy
                    it[updateTs] = row.updateTs
                }
            }
            PurgeOutcome(
                requested = rows.size,
                applied = free.size,
                skipped = present.map { SkippedRow(it.k, SkipReason.PRESENT) },
            )
        }

    private fun page(
        after: Long,
        updatedBefore: LocalDateTime,
        limit: Int,
    ): List<Pair<Long, PurgeRow>> =
        table
            .select { (table.id greater after) and (table.updateTs less updatedBefore) }
            .orderBy(table.id to SortOrder.ASC)
            .limit(limit)
            .map { it[table.id] to it.toPurgeRow() }

    /**
     * A row that cannot be read is reported, never purged. The purge does not remove what it could
     * not understand, and an operator who sees the key can decide what it was.
     */
    private fun verdict(
        row: PurgeRow,
        service: String,
    ): Verdict =
        runCatching {
            when {
                PurgeFields.isActive(row.v) -> Verdict.Keep
                PurgeFields.serviceOf(row.k) != service -> Verdict.Keep
                else -> Verdict.Purge
            }
        }.getOrElse { Verdict.Unreadable(it.message ?: it::class.java.simpleName) }

    private fun ResultRow.toPurgeRow() =
        PurgeRow(
            k = this[table.k],
            v = this[table.v],
            createdAt = this[table.createdAt],
            createdBy = this[table.createdBy],
            modifiedAt = this[table.modifiedAt],
            modifiedBy = this[table.modifiedBy],
            updateTs = this[table.updateTs],
        )

    private sealed interface Verdict {
        data object Purge : Verdict

        data object Keep : Verdict

        data class Unreadable(
            val reason: String,
        ) : Verdict
    }

    private companion object {
        const val PAGE_SIZE = 1_000
    }
}
