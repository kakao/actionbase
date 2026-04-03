package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.v2.core.metadata.MutationMode as V2MutationMode

import com.kakao.actionbase.core.edge.MutationEvent
import com.kakao.actionbase.core.state.State
import com.kakao.actionbase.engine.MutationContext
import com.kakao.actionbase.engine.MutationEngine
import com.kakao.actionbase.engine.QueryEngine
import com.kakao.actionbase.engine.binding.TableBinding
import com.kakao.actionbase.engine.metadata.MutationMode
import com.kakao.actionbase.engine.query.ActionbaseQuery
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.label.hbase.HBaseIndexedLabel
import com.kakao.actionbase.v2.engine.label.nil.NilLabel
import com.kakao.actionbase.v2.engine.sql.DataFrame

import reactor.core.publisher.Mono

/**
 * Engine implementation that wraps Graph (V2 engine).
 * Encapsulates V2 internal calls (label resolution, WAL, CDC, lock error handling).
 */
class V2BackedEngine(
    private val graph: Graph,
) : MutationEngine,
    QueryEngine {
    override fun getTableBinding(
        database: String,
        alias: String,
    ): TableBinding {
        val label = graph.getLabel(EntityName(database, alias))
        if (label is NilLabel) {
            return NilTableBinding(V3TableDescriptor.create(label.entity))
        }
        if (label !is HBaseIndexedLabel) {
            throw UnsupportedOperationException(
                "This Label (${label.entity.fullName}, ${label.javaClass}) is not indexed or not supported for edge mutation",
            )
        }
        return label.tableBinding
    }

    override fun query(request: ActionbaseQuery): Mono<Map<String, DataFrame>> = graph.query(request)

    private val messaging = V2BackedMessageBinding(wal = graph.wal, cdc = graph.cdc)

    override fun writeWal(
        ctx: MutationContext,
        event: MutationEvent,
    ): Mono<Void> = messaging.writeWal(ctx, event)

    override fun writeCdc(
        ctx: MutationContext,
        events: List<MutationEvent>,
        status: String,
        before: State,
        after: State,
        acc: Long,
    ) = messaging.writeCdc(ctx, events, status, before, after, acc)

    override val mutationRequestTimeout: Long
        get() = graph.mutationRequestTimeout

    override val systemMutationMode: MutationMode?
        get() = graph.systemMutationMode?.toV3()

    companion object {
        private fun V2MutationMode.toV3(): MutationMode = MutationMode.valueOf(name)
    }
}
