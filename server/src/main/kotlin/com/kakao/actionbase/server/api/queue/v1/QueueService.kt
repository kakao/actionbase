package com.kakao.actionbase.server.api.queue.v1

import com.kakao.actionbase.core.edge.Edge
import com.kakao.actionbase.core.edge.MutationKey
import com.kakao.actionbase.core.edge.payload.DataFrameEdgePayload
import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest
import com.kakao.actionbase.core.edge.payload.MutationResult
import com.kakao.actionbase.core.state.EventType
import com.kakao.actionbase.engine.metadata.MutationMode
import com.kakao.actionbase.engine.service.MutationService
import com.kakao.actionbase.engine.service.QueryService
import com.kakao.actionbase.server.api.graph.v3.metadata.V3CompatService
import com.kakao.actionbase.v2.core.metadata.Direction

import org.springframework.stereotype.Service

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Runtime queue operations over the backing immutable edge table:
 * - enqueue is an append (INSERT with no lock, since an append has no read-modify-write conflict);
 * - poll fans out a per-partition index scan across a shard's partitions, merges by `orderBy`, and
 *   returns a forward-only cursor to resume from.
 */
@Service
class QueueService(
    private val compat: V3CompatService,
    private val mutationService: MutationService,
    private val queryService: QueryService,
) {
    fun enqueue(
        database: String,
        queue: String,
        request: EnqueueRequest,
    ): Mono<EnqueueResponse> =
        queueMeta(database, queue).flatMap { meta ->
            val items =
                request.messages.map { message ->
                    val partition = PartitionHasher.partition(message.key, meta.partitionCount)
                    EdgeBulkMutationRequest.MutationItem(
                        type = EventType.INSERT,
                        edge =
                            Edge(
                                version = message.orderBy,
                                source = partition.toLong(),
                                target = message.id,
                                properties = message.payload + (meta.orderBy to message.orderBy),
                            ),
                    )
                }
            mutationService
                .mutate(database, queue, items, acquireLock = false, syncMode = MutationMode.SYNC)
                .map { results -> results.toEnqueueResponse() }
        }

    fun poll(
        database: String,
        queue: String,
        shard: Shard,
        limit: Int,
        cursor: String?,
    ): Mono<PollResponse> =
        queueMeta(database, queue).flatMap { meta ->
            val partitions = shard.partitionsFor(meta.partitionCount)
            val resumeFrom = QueueCursor.decode(cursor)
            Flux
                .fromIterable(partitions)
                .flatMap({ partition ->
                    queryService
                        .scan(
                            database,
                            queue,
                            QueueAdminService.ORDER_INDEX,
                            start = partition.toLong(),
                            direction = Direction.OUT,
                            limit = limit,
                            // Resume strictly after the last consumed order value; forward-only, no re-reads.
                            ranges = resumeFrom.positionOf(partition)?.let { "${meta.orderBy}:gt:$it" },
                        ).map { page -> partition to page }
                }, POLL_CONCURRENCY)
                .collectList()
                .map { pages -> pages.toPollResponse(meta.orderBy, resumeFrom) }
        }

    private fun queueMeta(
        database: String,
        queue: String,
    ): Mono<QueueMeta> =
        compat.getTable(database, queue).map { descriptor ->
            QueueDescriptorCodec.decode(descriptor.comment)
                ?: throw IllegalArgumentException("`$database.$queue` is not a queue/v1 table")
        }

    private fun List<MutationResult>.toEnqueueResponse(): EnqueueResponse {
        val results =
            map { result ->
                val key = result.key as MutationKey.SourceTarget
                EnqueueResult(
                    partition = (key.source as Long).toInt(),
                    id = key.target.toString(),
                    status = result.status,
                )
            }
        return EnqueueResponse(accepted = results.count { it.status == "CREATED" }, results = results)
    }

    private fun List<Pair<Int, DataFrameEdgePayload>>.toPollResponse(
        orderBy: String,
        resumeFrom: QueueCursor,
    ): PollResponse {
        val messages =
            flatMap { (partition, page) ->
                page.edges.map { edge ->
                    PolledMessage(
                        partition = partition,
                        id = edge.target.toString(),
                        orderBy = (edge.properties[orderBy] as Number).toLong(),
                        payload = edge.properties.filterKeys { it != orderBy },
                    )
                }
            }.sortedBy { it.orderBy }
        // Carry every prior position forward, advancing the partitions that yielded messages.
        val positions = resumeFrom.positions.toMutableMap()
        messages.forEach { message -> positions.merge(message.partition, message.orderBy, ::maxOf) }
        val hasNext = any { (_, page) -> page.hasNext }
        return PollResponse(
            messages = messages,
            cursor = if (positions.isEmpty()) null else QueueCursor(positions).encode(),
            hasNext = hasNext,
        )
    }

    private companion object {
        const val POLL_CONCURRENCY = 8
    }
}
