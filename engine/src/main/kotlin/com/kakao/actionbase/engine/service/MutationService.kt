package com.kakao.actionbase.engine.service

import com.kakao.actionbase.core.edge.MutationEvent
import com.kakao.actionbase.core.edge.MutationKey
import com.kakao.actionbase.core.edge.UnresolvedEvent
import com.kakao.actionbase.core.edge.payload.MutationResult
import com.kakao.actionbase.core.state.EventType
import com.kakao.actionbase.core.state.transit
import com.kakao.actionbase.engine.Audit
import com.kakao.actionbase.engine.MutationContext
import com.kakao.actionbase.engine.MutationEngine
import com.kakao.actionbase.engine.binding.TableBinding
import com.kakao.actionbase.engine.context.RequestContext
import com.kakao.actionbase.engine.metadata.MutationMode
import com.kakao.actionbase.engine.metadata.MutationModeContext
import com.kakao.actionbase.engine.storage.StorageOpCollector
import com.kakao.actionbase.engine.util.component1
import com.kakao.actionbase.engine.util.component2
import com.kakao.actionbase.engine.util.runEvenIfCancelled

import java.time.Duration

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class MutationService(
    private val engine: MutationEngine,
) {
    fun mutate(
        database: String,
        alias: String,
        unresolvedEvents: List<UnresolvedEvent>,
        acquireLock: Boolean = true,
        syncMode: MutationMode? = null,
        forceSyncMode: Boolean = false,
        requestContext: RequestContext = RequestContext.DEFAULT,
    ): Mono<List<MutationResult>> =
        Mono
            .fromCallable {
                val tb = engine.getTableBinding(database, alias)
                val ctx =
                    MutationContext(
                        database = database,
                        alias = alias,
                        table = tb.table,
                        mutationMode = MutationModeContext.of(tb.mutationMode, syncMode, engine.systemMutationMode, forceSyncMode),
                        audit = Audit(requestContext.actor),
                        requestId = requestContext.requestId,
                    )
                ctx to tb
            }.flatMap { (ctx, tb) ->
                Flux
                    .fromIterable(unresolvedEvents)
                    .map { it.createEvent(tb.schema) }
                    .flatMap { event -> engine.writeWal(ctx, event).thenReturn(event) }
                    .groupBy { it.key }
                    .flatMap { (key, groupMono) ->
                        if (ctx.mutationMode.queue) {
                            groupMono.map { group -> MutationResult.of(key, group.size, queuedStatus(ctx, group)) }
                        } else {
                            groupMono.flatMap { group ->
                                val sorted = group.sortedBy { it.event.version }
                                readModifyWrite(tb, key, sorted, acquireLock, requestContext.newCollector())
                                    .doOnNext { result ->
                                        engine.writeCdc(ctx, sorted, result.status, result.before, result.after, result.acc)
                                    }.onErrorResume {
                                        tb.handleMutationError(it)
                                        Mono.just(MutationResult.of(key, 0, ERROR))
                                    }
                            }
                        }
                    }.collectList()
                    .timeout(Duration.ofMillis(engine.mutationRequestTimeout))
                    .runEvenIfCancelled()
            }

    /**
     * For `system=ASYNC + label=SYNC` (no force), return the SYNC-shaped status derived from
     * the highest-version event's EventType so clients keep their contract; mutations are
     * still queued via the WAL. Intent-based, never `IDLE`. Otherwise `QUEUED`.
     */
    private fun queuedStatus(
        ctx: MutationContext,
        group: List<MutationEvent>,
    ): String {
        val mode = ctx.mutationMode
        if (mode.force || mode.system != MutationMode.ASYNC || mode.label != MutationMode.SYNC) {
            return QUEUED
        }
        val last = group.sortedBy { it.event.version }.last()
        return when (last.event.type) {
            EventType.INSERT -> "CREATED"
            EventType.UPDATE -> "UPDATED"
            EventType.DELETE -> "DELETED"
        }
    }

    private fun readModifyWrite(
        tb: TableBinding,
        key: MutationKey,
        sorted: List<MutationEvent>,
        acquireLock: Boolean,
        collector: StorageOpCollector?,
    ): Mono<MutationResult> {
        val rwm = {
            tb
                .read(key)
                .flatMap { state ->
                    val after = sorted.fold(state) { acc, m -> acc.transit(m.event, tb.schema) }
                    tb.write(key, state, after, collector)
                }.map { summary ->
                    val context = collector?.toContextMap()
                    MutationResult(key, sorted.size, summary.status, summary.before, summary.after, summary.acc, context)
                }
        }
        return if (acquireLock) tb.withLock(key, rwm) else rwm()
    }

    private companion object {
        const val QUEUED = "QUEUED"
        const val ERROR = "ERROR"
    }
}
