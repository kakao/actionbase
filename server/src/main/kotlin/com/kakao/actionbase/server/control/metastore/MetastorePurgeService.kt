package com.kakao.actionbase.server.control.metastore

import com.kakao.actionbase.v2.engine.metastore.purge.MetastorePurge
import com.kakao.actionbase.v2.engine.metastore.purge.PurgeOutcome

import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime

import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * Runs a purge against one configured metastore.
 *
 * Stateless: nothing is remembered between calls. That is what forces the rows to reach the client
 * before anything is deleted, which in turn is what makes the document a client holds a usable
 * backup - there is no window where rows are gone and the only copy was lost in a dropped response.
 */
class MetastorePurgeService(
    private val registry: MetastoreRegistry,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    fun candidates(query: PurgeQuery): Mono<PurgeSet> =
        blocking {
            val bounded = query.bounded()
            val target = registry.target(bounded.metastore)
            val scan =
                registry.purge(target).scan(
                    service = bounded.service,
                    // The cutoff is compared against `update_ts`, which the database writes in its
                    // own zone. A control instance in a different zone shifts the window by the
                    // offset, which only matters at the edge of the grace period.
                    updatedBefore = LocalDateTime.now(clock).minusDays(bounded.olderThanDays),
                    maxRows = bounded.maxRows,
                    maxScan = bounded.maxScan,
                    cursor = bounded.cursor,
                )
            PurgeSet(
                metastore = target.url,
                table = target.table,
                service = bounded.service,
                generatedAt = Instant.now(clock),
                scanned = scan.scanned,
                nextCursor = scan.nextCursor,
                rows = scan.rows,
                undecodable = scan.undecodable,
            )
        }

    fun execute(set: PurgeSet): Mono<PurgeResult> = apply(set) { purge -> purge.delete(set.rows) }

    fun restore(set: PurgeSet): Mono<PurgeResult> = apply(set) { purge -> purge.restore(set.rows) }

    private fun apply(
        set: PurgeSet,
        action: (MetastorePurge) -> PurgeOutcome,
    ): Mono<PurgeResult> =
        blocking {
            // Resolved from the document's own coordinates, so a file taken from one metastore
            // cannot be applied to another and a target this instance does not serve is refused.
            // A document with no rows is not an error - it applies nothing and reports
            // `requested=0`, which is what lets a misaimed file be refused for naming a database
            // this instance does not serve rather than answered with an empty result.
            val target = registry.target(set.metastore, set.table)
            val outcome = action(registry.purge(target))
            PurgeResult(
                metastore = target.url,
                table = target.table,
                service = set.service,
                requested = outcome.requested,
                applied = outcome.applied,
                skipped = outcome.skipped,
            )
        }

    /**
     * JDBC blocks, and this runs on an event loop. The work is a handful of statements per request
     * against an operator-facing endpoint, so it belongs on the elastic pool rather than anywhere
     * it could stall request handling.
     */
    private fun <T : Any> blocking(work: () -> T): Mono<T> = Mono.fromCallable(work).subscribeOn(Schedulers.boundedElastic())
}
