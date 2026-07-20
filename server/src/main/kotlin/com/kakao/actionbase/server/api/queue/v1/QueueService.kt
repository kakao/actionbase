package com.kakao.actionbase.server.api.queue.v1

import com.kakao.actionbase.core.edge.Edge
import com.kakao.actionbase.core.edge.MutationKey
import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest
import com.kakao.actionbase.core.edge.payload.MutationResult
import com.kakao.actionbase.core.state.EventType
import com.kakao.actionbase.engine.metadata.MutationMode
import com.kakao.actionbase.engine.service.MutationService
import com.kakao.actionbase.server.api.graph.v3.metadata.V3CompatService

import org.springframework.stereotype.Service

import reactor.core.publisher.Mono

/**
 * Runtime queue operations over the backing immutable edge table: enqueue is an append (INSERT with
 * no lock, since an append has no read-modify-write conflict).
 */
@Service
class QueueService(
    private val compat: V3CompatService,
    private val mutationService: MutationService,
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
}
