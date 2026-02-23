package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.v2.core.metadata.MutationMode as V2MutationMode
import com.kakao.actionbase.v2.engine.audit.Audit as V2Audit
import com.kakao.actionbase.v2.engine.metadata.MutationModeContext as V2MutationModeContext

import com.kakao.actionbase.core.edge.EdgeEvent
import com.kakao.actionbase.core.edge.MultiEdgeEvent
import com.kakao.actionbase.core.edge.MutationEvent
import com.kakao.actionbase.core.edge.mutation.EdgeMutationBuilder
import com.kakao.actionbase.core.state.EventType
import com.kakao.actionbase.core.state.State
import com.kakao.actionbase.engine.MutationContext
import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.edge.TraceEdge
import com.kakao.actionbase.v2.core.metadata.Active
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.engine.cdc.Cdc
import com.kakao.actionbase.v2.engine.cdc.CdcContext
import com.kakao.actionbase.v2.engine.compat.JavaKotlinTypeCompat
import com.kakao.actionbase.v2.engine.edge.HashEdge
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.label.EdgeOperationStatus
import com.kakao.actionbase.v2.engine.wal.Wal

import org.slf4j.LoggerFactory

import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

class V2BackedMessageBinding(
    private val wal: Wal,
    private val cdc: Cdc,
) {
    fun writeWal(
        ctx: MutationContext,
        event: MutationEvent,
    ): Mono<Void> =
        wal.write(
            EntityName(ctx.database, ctx.alias),
            EntityName(ctx.database, ctx.table),
            event.toV2TraceEdge(),
            event.event.type.toV2(),
            ctx.audit.toV2(),
            ctx.requestId,
            ctx.mutationMode.toV2(),
        )

    fun writeCdc(
        ctx: MutationContext,
        events: List<MutationEvent>,
        status: String,
        before: State,
        after: State,
        acc: Long,
    ) {
        val last = events.last()
        val cdcContext =
            CdcContext(
                label = EntityName(ctx.database, ctx.table),
                edge = last.toV2TraceEdge(),
                op = last.event.type.toV2(),
                status = EdgeOperationStatus.valueOf(status),
                before = stateToHashEdge(before, last),
                after = stateToHashEdge(after, last),
                acc = acc,
                alias = if (ctx.alias == ctx.table) null else EntityName(ctx.database, ctx.alias),
                audit = ctx.audit.toV2(),
                requestId = ctx.requestId,
            )
        cdc
            .write(cdcContext)
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe({}, { log.error("CDC write failed for {}/{}", ctx.database, ctx.table, it) })
    }

    companion object {
        private val log = LoggerFactory.getLogger(V2BackedMessageBinding::class.java)
        private const val DEFAULT_PRIMITIVE_VALUE = "0"

        private fun com.kakao.actionbase.engine.Audit.toV2(): V2Audit = V2Audit(actor)

        private fun com.kakao.actionbase.engine.metadata.MutationModeContext.toV2(): V2MutationModeContext =
            V2MutationModeContext(
                V2MutationMode.valueOf(label.name),
                request?.let { V2MutationMode.valueOf(it.name) },
                queue,
            )

        private fun stateToHashEdge(
            state: State,
            event: MutationEvent,
        ): HashEdge? {
            if (state == State.initial) return null
            val (source, target) =
                when (event) {
                    is EdgeEvent -> event.source to event.target
                    is MultiEdgeEvent ->
                        state.properties[EdgeMutationBuilder.MULTI_EDGE_SOURCE_FIELD_NAME]?.value to
                            state.properties[EdgeMutationBuilder.MULTI_EDGE_TARGET_FIELD_NAME]?.value
                }
            if (source == null || target == null) return null
            val active = if (state.active) Active.ACTIVE else Active.INACTIVE
            return HashEdge(
                active = active,
                ts = state.version,
                src = source,
                tgt = target,
                props = JavaKotlinTypeCompat.wrap(state.properties.mapValues { it.value.value }),
            )
        }

        private fun EventType.toV2(): EdgeOperation =
            when (this) {
                EventType.INSERT -> EdgeOperation.INSERT
                EventType.UPDATE -> EdgeOperation.UPDATE
                EventType.DELETE -> EdgeOperation.DELETE
            }

        internal fun MutationEvent.toV2TraceEdge(): TraceEdge =
            when (this) {
                is EdgeEvent ->
                    Edge(
                        event.version,
                        source,
                        target,
                        event.properties,
                    )
                is MultiEdgeEvent ->
                    Edge(
                        event.version,
                        event.properties["_source"] ?: DEFAULT_PRIMITIVE_VALUE,
                        event.properties["_target"] ?: DEFAULT_PRIMITIVE_VALUE,
                        event.properties - "_source" - "_target" + mapOf("_id" to id),
                    )
            }.withTraceId(event.id)
    }
}
