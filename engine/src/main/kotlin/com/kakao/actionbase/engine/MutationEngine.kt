package com.kakao.actionbase.engine

import com.kakao.actionbase.core.edge.MutationEvent
import com.kakao.actionbase.core.state.State
import com.kakao.actionbase.engine.binding.TableBinding
import com.kakao.actionbase.engine.metadata.MutationMode

import reactor.core.publisher.Mono

/**
 * Mutation engine abstraction used by the V3 Mutation path.
 * Performs mutations without direct references to V2 internals (Graph, Label, Wal, Cdc).
 */
interface MutationEngine {
    /**
     * Resolves a table binding from a database/alias pair.
     */
    fun getTableBinding(
        database: String,
        alias: String,
    ): TableBinding

    fun writeWal(
        ctx: MutationContext,
        event: MutationEvent,
    ): Mono<Void>

    /**
     * Writes CDC event. Fire-and-forget — implementations subscribe internally.
     */
    fun writeCdc(
        ctx: MutationContext,
        events: List<MutationEvent>,
        status: String,
        before: State,
        after: State,
        acc: Long,
    )

    /** Mutation request timeout (millis). */
    val mutationRequestTimeout: Long

    /** System-level mutation mode override from configuration. */
    val systemMutationMode: MutationMode?
}
