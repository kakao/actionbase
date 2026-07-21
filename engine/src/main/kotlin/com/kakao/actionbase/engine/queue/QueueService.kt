package com.kakao.actionbase.engine.queue

import com.kakao.actionbase.core.edge.Edge
import com.kakao.actionbase.core.edge.MutationKey
import com.kakao.actionbase.core.edge.payload.DataFrameEdgePayload
import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest
import com.kakao.actionbase.core.edge.payload.MutationResult
import com.kakao.actionbase.core.java.util.ULID
import com.kakao.actionbase.core.state.EventType
import com.kakao.actionbase.engine.metadata.MutationMode
import com.kakao.actionbase.engine.service.MutationService
import com.kakao.actionbase.engine.service.QueryService
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName

import java.time.Duration
import java.util.Random

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine

import reactor.core.publisher.Mono

/**
 * Runtime queue operations over the backing immutable edge table — the engine-level queue feature:
 * - enqueue is an append (INSERT with no lock, since an append has no read-modify-write conflict).
 *   The `id` (edge target) is a server-assigned ULID; `seq` orders the partition; `value` is stored
 *   as an opaque JSON blob.
 * - poll is a single-partition range scan over the `seq` index, resuming forward from `offset`, with
 *   an optional `until` upper bound for refresh / delay queues.
 *
 * Queue DDL (create / enable / disable / delete) is orchestrated at the server layer over the v3
 * table API; this service only reads the queue's partition count from the backing table's comment.
 */
class QueueService(
    private val graph: Graph,
    private val mutationService: MutationService,
    private val queryService: QueryService,
) {
    private val mapper = jacksonObjectMapper()
    private val random = Random()

    // Partition count per queue. Read from Graph's in-memory label registry and decoded from the
    // comment marker on a miss, then held for a minute — so the hot path neither re-reads the
    // metastore nor re-parses JSON. Expire-after-write (not -access) bounds staleness so a
    // re-partitioned queue is picked up within ~1 minute, since the queue size can change.
    private val partitionCountCache: Cache<EntityName, Int> =
        Caffeine
            .newBuilder()
            .maximumSize(PARTITION_CACHE_SIZE)
            .expireAfterWrite(Duration.ofMinutes(1))
            .build()

    fun enqueue(
        namespace: String,
        queue: String,
        request: EnqueueRequest,
    ): Mono<EnqueueResponse> =
        partitions(namespace, queue).flatMap { partitions ->
            val items =
                request.messages.map { message ->
                    val partition = PartitionHasher.partition(message.key, partitions)
                    EdgeBulkMutationRequest.MutationItem(
                        type = EventType.INSERT,
                        edge =
                            Edge(
                                version = message.seq,
                                source = partition.toLong(),
                                target = ULID.random(random),
                                properties =
                                    mapOf(
                                        QueueSchema.SEQ_FIELD to message.seq,
                                        QueueSchema.VALUE_FIELD to mapper.writeValueAsString(message.value),
                                    ),
                            ),
                    )
                }
            mutationService
                .mutate(namespace, queue, items, acquireLock = false, syncMode = MutationMode.SYNC)
                .map { results -> results.toEnqueueResponse() }
        }

    fun poll(
        namespace: String,
        queue: String,
        partition: Int,
        limit: Int,
        offset: Long?,
        until: Long?,
    ): Mono<PollResponse> {
        require(limit in 1..MAX_POLL_LIMIT) { "limit must be in 1..$MAX_POLL_LIMIT, got $limit" }
        return partitions(namespace, queue).flatMap { partitions ->
            require(partition in 0 until partitions) { "partition must be in 0..${partitions - 1}, got $partition" }
            queryService
                .scan(
                    namespace,
                    queue,
                    QueueSchema.SEQ_INDEX,
                    start = partition.toLong(),
                    direction = Direction.OUT,
                    limit = limit,
                    ranges = rangePredicate(offset, until),
                ).map { page -> page.toPollResponse(partition, offset) }
        }
    }

    /**
     * The queue's partition count. On a cache miss it reads Graph's in-memory label registry
     * (refreshed by the metastore reload) and decodes the comment marker; the result is cached for a
     * minute (see [partitionCountCache]). A queue a reload has not picked up yet is not visible here.
     */
    fun partitions(
        namespace: String,
        queue: String,
    ): Mono<Int> {
        val name = EntityName(namespace, queue)
        return Mono.fromCallable {
            partitionCountCache.get(name) {
                val comment = graph.getLabel(it).entity.desc
                (
                    QueueDescriptorCodec.decode(comment)
                        ?: throw IllegalArgumentException("`$namespace.$queue` is not a queue/v1 table")
                ).partitions
            }
        }
    }

    // offset is an exclusive lower bound; until an inclusive upper bound. Two predicates on the same
    // index field collapse in the scan, so a bounded poll uses a single `bt` range.
    private fun rangePredicate(
        offset: Long?,
        until: Long?,
    ): String? {
        val field = QueueSchema.SEQ_FIELD
        return when {
            offset != null && until != null -> "$field:bt:${offset + 1},$until"
            offset != null -> "$field:gt:$offset"
            until != null -> "$field:lte:$until"
            else -> null
        }
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

    private fun DataFrameEdgePayload.toPollResponse(
        partition: Int,
        offset: Long?,
    ): PollResponse {
        val messages =
            edges.map { edge ->
                PolledMessage(
                    partition = partition,
                    id = edge.target.toString(),
                    seq = (edge.properties[QueueSchema.SEQ_FIELD] as Number).toLong(),
                    value = decodeValue(edge.properties[QueueSchema.VALUE_FIELD]),
                )
            }
        // Carry the cursor forward: the max seq seen, or the prior offset when the partition drains.
        val nextOffset = messages.maxOfOrNull { it.seq } ?: offset
        return PollResponse(messages = messages, offset = nextOffset, hasNext = hasNext)
    }

    private fun decodeValue(raw: Any?): Any? {
        val json = raw as? String ?: return raw
        return runCatching { mapper.readValue(json, Any::class.java) }.getOrDefault(json)
    }

    private companion object {
        // Per-partition fetch bound.
        const val MAX_POLL_LIMIT = 1000

        // Upper bound on distinct queues held in the partition-count cache.
        const val PARTITION_CACHE_SIZE = 100_000L
    }
}
