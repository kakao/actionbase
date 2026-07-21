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
 * Engine-level queue runtime over the immutable edge table: enqueue appends (lock-free INSERT,
 * server-assigned ULID id), poll range-scans one partition by `seq`. DDL lives in the server layer.
 */
class QueueService(
    private val graph: Graph,
    private val mutationService: MutationService,
    private val queryService: QueryService,
) {
    private val mapper = jacksonObjectMapper()
    private val random = Random()

    // Expire-after-write (not -access) so a queue resize is picked up within a minute.
    private val numPartitionsCache: Cache<EntityName, Int> =
        Caffeine
            .newBuilder()
            .maximumSize(NUM_PARTITIONS_CACHE_SIZE)
            .expireAfterWrite(Duration.ofMinutes(1))
            .build()

    fun enqueue(
        namespace: String,
        queue: String,
        request: EnqueueRequest,
    ): Mono<EnqueueResponse> =
        getNumPartitions(namespace, queue).flatMap { numPartitions ->
            val items =
                request.messages.map { message ->
                    val partition = PartitionHasher.partition(message.key, numPartitions)
                    EdgeBulkMutationRequest.MutationItem(
                        type = EventType.INSERT,
                        edge =
                            Edge(
                                version = message.seq,
                                source = partition.toLong(),
                                target = ULID.random(random),
                                properties =
                                    mapOf(
                                        QueueSchema.SEQ to message.seq,
                                        QueueSchema.VALUE to mapper.writeValueAsString(message.value),
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
        return getNumPartitions(namespace, queue).flatMap { numPartitions ->
            require(partition in 0 until numPartitions) { "partition must be in 0..${numPartitions - 1}, got $partition" }
            queryService
                .scan(
                    namespace,
                    queue,
                    QueueSchema.SEQ,
                    start = partition.toLong(),
                    direction = Direction.OUT,
                    limit = limit,
                    ranges = rangePredicate(offset, until),
                ).map { page -> page.toPollResponse(partition, offset) }
        }
    }

    /** Partition count; a cache miss reads Graph's in-memory registry and decodes the comment marker. */
    fun getNumPartitions(
        namespace: String,
        queue: String,
    ): Mono<Int> {
        val name = EntityName(namespace, queue)
        return Mono.fromCallable {
            numPartitionsCache.get(name) {
                val comment = graph.getLabel(it).entity.desc
                (
                    QueueMetadataCodec.decode(comment)
                        ?: throw IllegalArgumentException("`$namespace.$queue` is not a queue/v1 table")
                ).numPartitions
            }
        }
    }

    // offset is exclusive, until inclusive; same-field predicates collapse, so a bounded poll uses `bt`.
    private fun rangePredicate(
        offset: Long?,
        until: Long?,
    ): String? {
        val field = QueueSchema.SEQ
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
                    seq =
                        (edge.properties[QueueSchema.SEQ] as? Number)?.toLong()
                            ?: error("queue edge `${edge.target}` has no numeric `${QueueSchema.SEQ}`"),
                    value = decodeValue(edge.properties[QueueSchema.VALUE]),
                )
            }
        val nextOffset = messages.maxOfOrNull { it.seq } ?: offset
        return PollResponse(messages = messages, offset = nextOffset, hasNext = hasNext)
    }

    private fun decodeValue(raw: Any?): Any? {
        val json = raw as? String ?: return raw
        return runCatching { mapper.readValue(json, Any::class.java) }.getOrDefault(json)
    }

    private companion object {
        const val MAX_POLL_LIMIT = 1000
        const val NUM_PARTITIONS_CACHE_SIZE = 100_000L
    }
}
