package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.core.Constants
import com.kakao.actionbase.core.codec.ByteArrayBufferPool
import com.kakao.actionbase.core.edge.EdgeEvent
import com.kakao.actionbase.core.edge.MultiEdgeEvent
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
import com.kakao.actionbase.core.metadata.common.ModelSchema
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

    fun mutateEdge(
        database: String,
        alias: String,
        request: EdgeBulkMutationRequest,
        lock: Boolean = true,
        sync: MutationMode? = null,
        requestContext: RequestContext = RequestContext.DEFAULT,
    ): Mono<EdgeMutationResponse> {
        val aliasEntityName = EntityName(database, alias)
        val label = graph.getLabel(aliasEntityName)
        if (label !is HBaseIndexedLabel) {
            return Mono.error(UnsupportedOperationException("This Label (${label.entity.fullName}, ${label.javaClass}) is not indexed or not supported for edge mutation"))
        }

        val mutationMode = MutationModeContext.of(label.entity.mode, sync)

        val tableBinding = label.v3TableBinding
        val audit = Audit(requestContext.actor)
        val requestId = requestContext.requestId

        return Flux
            .fromIterable(request.mutations)
            .map { it.createEvent(tableBinding.schema as ModelSchema.Edge) } // ensureType
            .flatMap { edgeEvent ->
                val edge = edgeEvent.toTraceEdge()
                val operation = edgeEvent.event.type.toV2()
                graph.wal
                    .write(aliasEntityName, label.name, edge, operation, audit, requestId, mutationMode)
                    .thenReturn(edgeEvent)
            }.groupBy { it.source to it.target }
            .flatMap { groupedFlux ->
                val key = groupedFlux.key()
                if (mutationMode.queue) {
                    groupedFlux
                        .collectList()
                        .flatMap { group ->
                            Mono.just(
                                EdgeMutationStatus(
                                    source = key.first,
                                    target = key.second,
                                    count = group.size,
                                    status = EdgeOperationStatus.QUEUED.name,
                                    before = State.initial,
                                    after = State.initial,
                                    acc = 0,
                                ),
                            )
                        }
                } else {
                    groupedFlux
                        .collectList()
                        .flatMap { group ->
                            val sortedGroup = group.sortedBy { it.event.version }
                            tableBinding
                                .mutateEdge(key, sortedGroup.map { it.event }, lock, encoder, tableBinding.schema.codeToName)
                                .doOnNext { status ->
                                    val last = sortedGroup.last()
                                    val edge = last.toTraceEdge()
                                    val cdcMessage =
                                        CdcContext(
                                            label = label.entity.name,
                                            edge = edge,
                                            op = last.event.type.toV2(),
                                            status = EdgeOperationStatus.valueOf(status.status),
                                            before = status.before.toHashEdge(key.first, key.second),
                                            after = status.after.toHashEdge(key.first, key.second),
                                            acc = status.acc,
                                            alias = if (aliasEntityName == label.entity.name) null else aliasEntityName,
                                            audit = Audit(requestContext.actor),
                                            requestId = requestContext.requestId,
                                        )
                                    graph.cdc
                                        .write(cdcMessage)
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .subscribe()
                                }.onErrorResume {
                                    if (it is LockAcquisitionFailedException) {
                                        label
                                            .findStaleLockAndClear(it.lockEdge, graph.lockTimeout)
                                            .subscribeOn(Schedulers.boundedElastic())
                                            .subscribe()
                                    }
                                    Mono.just(
                                        EdgeMutationStatus(
                                            source = key.first,
                                            target = key.second,
                                            count = 0,
                                            status = EdgeOperationStatus.ERROR.name,
                                            before = State.initial,
                                            after = State.initial,
                                            acc = 0,
                                        ),
                                    )
                                }
                        }.subscribeOn(Schedulers.boundedElastic())
                }
            }.collectList()
            .map {
                EdgeMutationResponse(
                    it
                        .map { item ->
                            EdgeMutationResponse.Item(
                                source = item.source,
                                target = item.target,
                                count = item.count,
                                status = item.status,
                            )
                        }.sortedBy { item -> "${item.source}:${item.target}" },
                )
            }.timeout(Duration.ofMillis(graph.mutationRequestTimeout))
            .runEvenIfCancelled()
    }

    fun mutateMultiEdge(
        database: String,
        alias: String,
        request: MultiEdgeBulkMutationRequest,
        lock: Boolean = true,
        sync: MutationMode? = null,
        requestContext: RequestContext = RequestContext.DEFAULT,
    ): Mono<MultiEdgeMutationResponse> {
        val aliasEntityName = EntityName(database, alias)
        val label = graph.getLabel(aliasEntityName)
        if (label !is HBaseIndexedLabel) {
            return Mono.error(UnsupportedOperationException("This Label (${label.entity.fullName}) is not indexed or not supported for edge mutation"))
        }
        val mutationMode = MutationModeContext.of(label.entity.mode, sync)

        val tableBinding = label.v3TableBinding
        val audit = Audit(requestContext.actor)
        val requestId = requestContext.requestId

        return Flux
            .fromIterable(request.mutations)
            .map { it.createEvent(tableBinding.schema as ModelSchema.MultiEdge) } // ensureType
            .flatMap { multiEdgeEvent ->
                val edge = multiEdgeEvent.toTraceEdge()
                val operation = multiEdgeEvent.event.type.toV2()
                graph.wal
                    .write(aliasEntityName, label.name, edge, operation, audit, requestId, mutationMode)
                    .thenReturn(multiEdgeEvent)
            }.groupBy { it.id }
            .flatMap { groupedFlux ->
                val key = groupedFlux.key()
                if (mutationMode.queue) {
                    groupedFlux
                        .collectList()
                        .flatMap { group ->
                            Mono.just(
                                MultiEdgeMutationStatus(
                                    id = key,
                                    count = group.size,
                                    status = EdgeOperationStatus.QUEUED.name,
                                    before = State.initial,
                                    after = State.initial,
                                    acc = 0,
                                ),
                            )
                        }
                } else {
                    groupedFlux
                        .collectList()
                        .flatMap { group ->
                            val sortedGroup = group.sortedBy { it.event.version }
                            tableBinding
                                .mutateMultiEdge(key, sortedGroup.map { it.event }, lock, encoder, tableBinding.schema.codeToName)
                                .doOnNext { status ->
                                    val last = sortedGroup.last()
                                    val edge = last.toTraceEdge()
                                    val cdcMessage =
                                        CdcContext(
                                            label = label.entity.name,
                                            edge = edge,
                                            op = last.event.type.toV2(),
                                            status = EdgeOperationStatus.valueOf(status.status),
                                            before = status.before.toHashEdge(source = status.before.getMultiEdgeSource(), target = status.before.getMultiEdgeTarget()),
                                            after = status.after.toHashEdge(source = status.after.getMultiEdgeSource(), target = status.after.getMultiEdgeTarget()),
                                            acc = status.acc,
                                            alias = if (aliasEntityName == label.entity.name) null else aliasEntityName,
                                            audit = Audit(requestContext.actor),
                                            requestId = requestContext.requestId,
                                        )
                                    graph.cdc
                                        .write(cdcMessage)
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .subscribe()
                                }
                        }.subscribeOn(Schedulers.boundedElastic())
                }
            }.collectList()
            .map {
                MultiEdgeMutationResponse(
                    it
                        .map { item ->
                            MultiEdgeMutationResponse.Item(
                                id = item.id,
                                count = item.count,
                                status = item.status,
                            )
                        }.sortedBy { it.toString() },
                )
            }.timeout(Duration.ofMillis(graph.mutationRequestTimeout))
            .runEvenIfCancelled()
    }

    private fun State.toHashEdge(
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

        private fun MultiEdgeEvent.toTraceEdge(): TraceEdge =
            Edge(
                this.event.version,
                this.event.properties["_source"] ?: DEFAULT_PRIMITIVE_VALUE,
                this.event.properties["_target"] ?: DEFAULT_PRIMITIVE_VALUE,
                this.event.properties - "_source" - "_target" + mapOf("_id" to this.id),
            ).withTraceId(this.event.id)

        private fun EdgeEvent.toTraceEdge(): TraceEdge =
            Edge(
                this.event.version,
                this.source,
                this.target,
                this.event.properties,
            ).withTraceId(this.event.id)

        fun State.getMultiEdgeSource(): Any? = properties[EdgeMutationBuilder.MULTI_EDGE_SOURCE_FIELD_NAME]?.value

        fun State.getMultiEdgeTarget(): Any? = properties[EdgeMutationBuilder.MULTI_EDGE_TARGET_FIELD_NAME]?.value
    }
}
