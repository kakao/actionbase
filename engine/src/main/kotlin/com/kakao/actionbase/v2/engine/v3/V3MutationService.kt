package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.core.Constants
import com.kakao.actionbase.core.codec.ByteArrayBufferPool
import com.kakao.actionbase.core.edge.EdgeEvent
import com.kakao.actionbase.core.edge.MultiEdgeEvent
import com.kakao.actionbase.core.edge.MutationEvent
import com.kakao.actionbase.core.edge.mapper.EdgeCountRecordMapper
import com.kakao.actionbase.core.edge.mapper.EdgeGroupRecordMapper
import com.kakao.actionbase.core.edge.mapper.EdgeIndexRecordMapper
import com.kakao.actionbase.core.edge.mapper.EdgeLockRecordMapper
import com.kakao.actionbase.core.edge.mapper.EdgeRecordMapper
import com.kakao.actionbase.core.edge.mapper.EdgeStateRecordMapper
import com.kakao.actionbase.core.edge.mutation.EdgeMutationBuilder
import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest
import com.kakao.actionbase.core.edge.payload.EdgeMutationResponse
import com.kakao.actionbase.core.edge.payload.EdgeMutationStatus
import com.kakao.actionbase.core.edge.payload.MultiEdgeBulkMutationRequest
import com.kakao.actionbase.core.edge.payload.MultiEdgeMutationResponse
import com.kakao.actionbase.core.edge.payload.MultiEdgeMutationStatus
import com.kakao.actionbase.core.edge.payload.MutationStatus
import com.kakao.actionbase.core.state.Event
import com.kakao.actionbase.core.state.EventType
import com.kakao.actionbase.core.state.State
import com.kakao.actionbase.engine.context.RequestContext
import com.kakao.actionbase.engine.util.runEvenIfCancelled
import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.edge.TraceEdge
import com.kakao.actionbase.v2.core.metadata.Active
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.core.metadata.MutationMode
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.audit.Audit
import com.kakao.actionbase.v2.engine.cdc.CdcContext
import com.kakao.actionbase.v2.engine.compat.JavaKotlinTypeCompat
import com.kakao.actionbase.v2.engine.edge.HashEdge
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.label.EdgeOperationStatus
import com.kakao.actionbase.v2.engine.label.LockAcquisitionFailedException
import com.kakao.actionbase.v2.engine.label.hbase.HBaseIndexedLabel
import com.kakao.actionbase.v2.engine.metadata.MutationModeContext

import java.time.Duration

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

class V3MutationService(
    private val graph: Graph,
) {
    private val byteArrayBufferPool = ByteArrayBufferPool.create(graph.encoderPoolSize, Constants.Codec.DEFAULT_BUFFER_SIZE)

    private val encoder =
        EdgeRecordMapper(
            state = EdgeStateRecordMapper.create(byteArrayBufferPool),
            index = EdgeIndexRecordMapper.create(byteArrayBufferPool),
            count = EdgeCountRecordMapper.create(byteArrayBufferPool),
            lock = EdgeLockRecordMapper.create(byteArrayBufferPool),
            group = EdgeGroupRecordMapper.create(byteArrayBufferPool),
        )

    // -- public API

    fun mutateEdge(
        database: String,
        alias: String,
        request: EdgeBulkMutationRequest,
        lock: Boolean = true,
        sync: MutationMode? = null,
        requestContext: RequestContext = RequestContext.DEFAULT,
    ): Mono<EdgeMutationResponse> =
        Mono.fromCallable { resolveMutationContext(database, alias, sync, requestContext) }.flatMap { ctx ->
            executeMutation(
                ctx = ctx,
                mutations = request.mutations,
                mutate = { tb, key, events -> tb.mutateEdge(key, events, lock, encoder, tb.schema.codeToName) },
                stateToV2HashEdge = { state, key -> state.toV2HashEdge(key.first, key.second) },
                queuedStatus = { key, count -> EdgeMutationStatus.of(key, count, EdgeOperationStatus.QUEUED.name) },
                errorStatus = { key -> EdgeMutationStatus.of(key, 0, EdgeOperationStatus.ERROR.name) },
                toResponse = EdgeMutationResponse::from,
            )
        }

    fun mutateMultiEdge(
        database: String,
        alias: String,
        request: MultiEdgeBulkMutationRequest,
        lock: Boolean = true,
        sync: MutationMode? = null,
        requestContext: RequestContext = RequestContext.DEFAULT,
    ): Mono<MultiEdgeMutationResponse> =
        Mono.fromCallable { resolveMutationContext(database, alias, sync, requestContext) }.flatMap { ctx ->
            executeMutation(
                ctx = ctx,
                mutations = request.mutations,
                mutate = { tb, key, events -> tb.mutateMultiEdge(key, events, lock, encoder, tb.schema.codeToName) },
                stateToV2HashEdge = { state, _ -> state.toV2HashEdge(state.getMultiEdgeSource(), state.getMultiEdgeTarget()) },
                queuedStatus = { key, count -> MultiEdgeMutationStatus.of(key, count, EdgeOperationStatus.QUEUED.name) },
                errorStatus = { key -> MultiEdgeMutationStatus.of(key, 0, EdgeOperationStatus.ERROR.name) },
                toResponse = MultiEdgeMutationResponse::from,
            )
        }

    // -- context

    private data class MutationContext(
        val aliasEntityName: EntityName,
        val label: HBaseIndexedLabel,
        val mutationMode: MutationModeContext,
        val tableBinding: V3CompatibleTableBinding,
        val audit: Audit,
        val requestId: String,
    )

    private fun resolveMutationContext(
        database: String,
        alias: String,
        sync: MutationMode?,
        requestContext: RequestContext,
    ): MutationContext {
        val aliasEntityName = EntityName(database, alias)
        val label = graph.getLabel(aliasEntityName)
        if (label !is HBaseIndexedLabel) {
            throw UnsupportedOperationException("This Label (${label.entity.fullName}, ${label.javaClass}) is not indexed or not supported for edge mutation")
        }
        return MutationContext(
            aliasEntityName = aliasEntityName,
            label = label,
            mutationMode = MutationModeContext.of(label.entity.mode, sync),
            tableBinding = label.v3TableBinding,
            audit = Audit(requestContext.actor),
            requestId = requestContext.requestId,
        )
    }

    // -- pipeline

    private fun <E : MutationEvent<K>, K : Any, S : MutationStatus, R> executeMutation(
        ctx: MutationContext,
        mutations: List<MutationEvent.Source<E>>,
        mutate: (V3CompatibleTableBinding, K, List<Event>) -> Mono<S>,
        stateToV2HashEdge: (State, K) -> HashEdge?,
        queuedStatus: (K, Int) -> S,
        errorStatus: (K) -> S,
        toResponse: (List<S>) -> R,
    ): Mono<R> {
        val tb = ctx.tableBinding
        return Flux
            .fromIterable(mutations)
            .map { it.createEvent(tb.schema) }
            .flatMap { writeWal(ctx, it) }
            .groupBy { it.id }
            .flatMap { groupedFlux ->
                val key = groupedFlux.key()
                if (ctx.mutationMode.queue) {
                    groupedFlux.collectList().map { group -> queuedStatus(key, group.size) }
                } else {
                    mutateGroup(groupedFlux, ctx, tb, key, mutate, stateToV2HashEdge, errorStatus)
                }
            }.collectList()
            .map(toResponse)
            .timeout(Duration.ofMillis(graph.mutationRequestTimeout))
            .runEvenIfCancelled()
    }

    private fun <E : MutationEvent<*>> writeWal(
        ctx: MutationContext,
        event: E,
    ): Mono<E> =
        graph.wal
            .write(
                ctx.aliasEntityName,
                ctx.label.name,
                event.toV2TraceEdge(),
                event.event.type.toV2(),
                ctx.audit,
                ctx.requestId,
                ctx.mutationMode,
            ).thenReturn(event)

    private fun <E : MutationEvent<K>, K : Any, S : MutationStatus> mutateGroup(
        groupedFlux: Flux<E>,
        ctx: MutationContext,
        tb: V3CompatibleTableBinding,
        key: K,
        mutate: (V3CompatibleTableBinding, K, List<Event>) -> Mono<S>,
        stateToV2HashEdge: (State, K) -> HashEdge?,
        errorStatus: (K) -> S,
    ): Mono<S> =
        groupedFlux
            .collectList()
            .flatMap { group ->
                val sorted = group.sortedBy { it.event.version }
                val last = sorted.last()
                mutate(tb, key, sorted.map { it.event })
                    .doOnNext { status ->
                        writeCdc(ctx, last.toV2TraceEdge(), last.event.type, status.status, stateToV2HashEdge(status.before, key), stateToV2HashEdge(status.after, key), status.acc)
                    }.onErrorResume {
                        handleMutationError(it, ctx.label)
                        Mono.just(errorStatus(key))
                    }
            }.subscribeOn(Schedulers.boundedElastic())

    // -- side effects

    private fun writeCdc(
        ctx: MutationContext,
        edge: TraceEdge,
        lastEventType: EventType,
        status: String,
        beforeHashEdge: HashEdge?,
        afterHashEdge: HashEdge?,
        acc: Long,
    ) {
        val cdcMessage =
            CdcContext(
                label = ctx.label.entity.name,
                edge = edge,
                op = lastEventType.toV2(),
                status = EdgeOperationStatus.valueOf(status),
                before = beforeHashEdge,
                after = afterHashEdge,
                acc = acc,
                alias = if (ctx.aliasEntityName == ctx.label.entity.name) null else ctx.aliasEntityName,
                audit = ctx.audit,
                requestId = ctx.requestId,
            )
        graph.cdc
            .write(cdcMessage)
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe()
    }

    private fun handleMutationError(
        error: Throwable,
        label: HBaseIndexedLabel,
    ) {
        if (error is LockAcquisitionFailedException) {
            label
                .findStaleLockAndClear(error.lockEdge, graph.lockTimeout)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe()
        }
    }

    // -- v2 compat helpers

    private fun State.toV2HashEdge(
        source: Any?,
        target: Any?,
    ): HashEdge? =
        if ((source == null || target == null) || this == State.initial) {
            null
        } else {
            val active = if (this.active) Active.ACTIVE else Active.INACTIVE
            HashEdge(
                active = active,
                ts = this.version,
                src = source,
                tgt = target,
                props = JavaKotlinTypeCompat.wrap(this.properties.mapValues { it.value.value }),
            )
        }

    companion object {
        private const val DEFAULT_PRIMITIVE_VALUE = "0"

        private fun EventType.toV2(): EdgeOperation =
            when (this) {
                EventType.INSERT -> EdgeOperation.INSERT
                EventType.UPDATE -> EdgeOperation.UPDATE
                EventType.DELETE -> EdgeOperation.DELETE
            }

        private fun MutationEvent<*>.toV2TraceEdge(): TraceEdge =
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

        fun State.getMultiEdgeSource(): Any? = properties[EdgeMutationBuilder.MULTI_EDGE_SOURCE_FIELD_NAME]?.value

        fun State.getMultiEdgeTarget(): Any? = properties[EdgeMutationBuilder.MULTI_EDGE_TARGET_FIELD_NAME]?.value
    }
}
