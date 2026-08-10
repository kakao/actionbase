package com.kakao.actionbase.server.control.metastore

import com.kakao.actionbase.v2.engine.metastore.purge.MetastorePurge
import com.kakao.actionbase.v2.engine.metastore.purge.MetastoreTarget
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
            val target = registry.target(query.metastore)
            val scan =
                registry.purge(target).scan(
                    service = query.service,
                    updatedBefore = LocalDateTime.now(clock).minusDays(query.olderThanDays),
                    maxRows = query.maxRows,
                    maxScan = query.maxScan,
                    cursor = query.cursor,
                )
            PurgeSet(
                metastore = target.url,
                table = target.table,
                service = query.service,
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
            val target = resolve(set)
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

    private fun resolve(set: PurgeSet): MetastoreTarget {
        require(set.rows.isNotEmpty()) { "purge set has no rows: nothing to apply" }
        return registry.target(set.metastore, set.table)
    }

    /**
     * JDBC blocks, and this runs on an event loop. The work is a handful of statements per request
     * against an operator-facing endpoint, so it belongs on the elastic pool rather than anywhere
     * it could stall request handling.
     */
    private fun <T : Any> blocking(work: () -> T): Mono<T> = Mono.fromCallable(work).subscribeOn(Schedulers.boundedElastic())
}
