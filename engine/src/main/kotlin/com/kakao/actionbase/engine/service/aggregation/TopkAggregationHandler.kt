package com.kakao.actionbase.engine.service.aggregation

import com.kakao.actionbase.v2.core.metadata.Direction as V2Direction

import com.kakao.actionbase.core.edge.Edge
import com.kakao.actionbase.core.edge.payload.AggregationItemPayload
import com.kakao.actionbase.core.edge.payload.AggregationResult
import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest.MutationItem
import com.kakao.actionbase.core.edge.payload.MutationResult
import com.kakao.actionbase.core.metadata.common.AggregationConstants
import com.kakao.actionbase.core.metadata.common.AggregationType
import com.kakao.actionbase.core.metadata.common.Aggregations
import com.kakao.actionbase.core.metadata.common.Bucket
import com.kakao.actionbase.core.metadata.common.Direction
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.metadata.common.Topk
import com.kakao.actionbase.core.state.EventType
import com.kakao.actionbase.engine.AggregationEngine
import com.kakao.actionbase.engine.queue.EnqueueMessage
import com.kakao.actionbase.engine.queue.EnqueueRequest
import com.kakao.actionbase.engine.queue.EnqueueResponse
import com.kakao.actionbase.engine.queue.QueueService
import com.kakao.actionbase.engine.service.MutationService
import com.kakao.actionbase.engine.service.QueryService
import com.kakao.actionbase.v2.engine.sql.WherePredicate
import com.kakao.actionbase.v2.engine.util.getLogger

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class TopkAggregationHandler(
    private val queryService: QueryService,
    private val mutationService: MutationService,
    private val queueService: QueueService,
    private val engine: AggregationEngine,
) : AggregationHandler {
    private val logger = getLogger()

    override val type: AggregationType = AggregationType.TOPK

    // Flatten every (event, direction, topk) into one fan-out. Per-item cardinality is schema-bounded
    // (a handful of topks × directions), so concurrency is capped once at the service, not here.
    override fun aggregate(item: AggregationItemPayload): Flux<AggregationResult> =
        Flux
            .fromIterable(
                createTopkEvent(item).flatMap { event ->
                    event.aggregations.topk.flatMap { topk ->
                        event.direction.directions().map { direction -> Triple(event, direction, topk) }
                    }
                },
            ).flatMap { (event, direction, topk) -> processTopk(event, direction, topk) }

    private fun createTopkEvent(item: AggregationItemPayload): List<EdgeAggregationEvent> {
        val tb = engine.getTableBinding(database = item.database, alias = item.table)

        require(tb.schema is ModelSchema.Edge || tb.schema is ModelSchema.MultiEdge) {
            "Aggregation is only supported for Edge and MultiEdge tables."
        }

        return tb.schema.groups
            .filter { it.aggregations.topk.isNotEmpty() }
            .map { group ->
                EdgeAggregationEvent.of(type = AggregationType.TOPK, database = item.database, table = tb.table, item, group)
            }
    }

    private fun processTopk(
        event: EdgeAggregationEvent,
        direction: Direction,
        topk: Topk,
    ): Mono<AggregationResult> {
        fun result(
            status: String,
            error: String? = null,
        ) = AggregationResult(
            database = event.database,
            table = event.table,
            source = event.source,
            target = event.target,
            status = status,
            error = error,
        )

        val inputs = RankingInputs.from(event, direction, topk)
        val (rankDatabase, rankTable) = parseFqn(topk.rank)
        val ranking =
            Ranking(
                database = rankDatabase,
                table = rankTable,
                topk = topk.topk,
                entity = inputs.entity,
                topkDimensionValue = inputs.topkDimensionValue,
                dimensionValues = inputs.dimensionValues,
                properties = inputs.properties,
            )

        return writeRank(
            sourceDatabase = event.database,
            sourceTable = event.table,
            group = event.group.group,
            start = inputs.directedSource,
            direction = direction,
            ranges = inputs.ranges,
            ranking = ranking,
        ).flatMap { results ->
            val status = if (results.any { it.status == ERROR }) ERROR else SUCCESS
            if (status == ERROR || topk.refreshAfterMillis <= 0 || !isSlidingWindow(event, inputs.ranges)) {
                return@flatMap Mono.just(result(status = status))
            }

            writeRefresh(
                event,
                ranking,
                direction,
                inputs.ranges,
                refreshAfterMillis = topk.refreshAfterMillis,
                refreshQueue = topk.refreshQueue,
            ).map { result(status = if (it.results.any { r -> r.status == ERROR }) ERROR else SUCCESS) }
        }.onErrorResume { err ->
            logger.error("topk aggregate failed for {}.{} topk={}", event.database, event.table, topk.topk, err)
            Mono.just(result(ERROR, err.message))
        }
    }

    /**
     * Runs the aggregation query for one ranking and writes its rank row.
     * Used by the aggregate flow, which then enqueues a refresh message
     * (which recomputes from an already-resolved [Ranking]).
     */
    private fun writeRank(
        sourceDatabase: String,
        sourceTable: String,
        group: String,
        start: String,
        direction: Direction,
        ranges: String?,
        ranking: Ranking,
    ): Mono<List<MutationResult>> =
        queryService
            .agg(
                database = sourceDatabase,
                table = sourceTable,
                group = group,
                start = listOf(start),
                direction = V2Direction.valueOf(direction.name),
                ranges = ranges,
            ).flatMap { response ->
                val metric = response.groups.firstOrNull()?.value ?: 0L

                mutationService
                    .mutate(
                        database = ranking.database,
                        alias = ranking.table,
                        unresolvedEvents =
                            listOf(
                                MutationItem(
                                    type = EventType.INSERT,
                                    edge =
                                        Edge(
                                            version = System.currentTimeMillis(),
                                            source =
                                                AggregationConstants.Topk.rankSource(
                                                    database = sourceDatabase,
                                                    table = sourceTable,
                                                    topk = ranking.topk,
                                                    entity = ranking.entity,
                                                    dimensionValues = ranking.dimensionValues,
                                                ),
                                            target = ranking.topkDimensionValue,
                                            properties =
                                                buildMap {
                                                    put(AggregationConstants.Topk.METRIC, metric)
                                                    if (ranking.properties.isNotEmpty()) {
                                                        put(
                                                            AggregationConstants.Topk.ADDITIONAL_PROPERTIES,
                                                            MAPPER.writeValueAsString(ranking.properties),
                                                        )
                                                    }
                                                },
                                        ),
                                ),
                            ),
                    )
            }

    private fun writeRefresh(
        event: EdgeAggregationEvent,
        ranking: Ranking,
        direction: Direction,
        ranges: String?,
        refreshAfterMillis: Long,
        refreshQueue: String,
    ): Mono<EnqueueResponse> {
        val refreshAt = refreshAt(event, refreshAfterMillis)
        val message =
            EnqueueMessage(
                key =
                    AggregationConstants.Topk.refreshKey(
                        database = event.database,
                        table = event.table,
                        topk = ranking.topk,
                        entity = ranking.entity,
                        topkDimensionValue = ranking.topkDimensionValue,
                        dimensionValues = ranking.dimensionValues,
                    ),
                seq = refreshAt,
                value =
                    TopkRefreshMessage.of(
                        type = AggregationType.TOPK,
                        database = event.database,
                        table = event.table,
                        topk = ranking.topk,
                        source = event.source,
                        target = event.target,
                        direction = direction.name,
                        ranges = ranges ?: "",
                        entity = ranking.entity,
                        topkDimensionValue = ranking.topkDimensionValue,
                        dimensionValues = AggregationConstants.Topk.joinValues(ranking.dimensionValues),
                        properties = ranking.properties,
                        refreshAt = refreshAt,
                    ),
            )

        return queueService.enqueue(
            namespace = AggregationConstants.Topk.DATABASE,
            queue = refreshQueue,
            request = EnqueueRequest(messages = listOf(message)),
        )
    }

    /**
     * The instant this event leaves the window. `now + window` misses it: the event is stored under a bucket,
     * the window's bounds are read at the same precision, so the window only moves at bucket boundaries — a
     * refresh scheduled off the raw event time fires while the event is still inside, changes nothing, and is
     * consumed. Counting from the event's bucket drops the same remainder the write dropped, and the extra
     * bucket accounts for the lower bound being truncated rather than raised.
     */
    private fun refreshAt(
        event: EdgeAggregationEvent,
        refreshAfterMillis: Long,
    ): Long {
        val bucketed = event.dateBucket()
        val start = bucketed?.let { (field, bucket) -> bucket.startOf(event.properties[field.name]) }

        return if (start == null) {
            System.currentTimeMillis() + refreshAfterMillis
        } else {
            start.toEpochMilli() + refreshAfterMillis + bucketed.second.interval().toMillis()
        }
    }

    private fun isSlidingWindow(
        event: EdgeAggregationEvent,
        ranges: String?,
    ): Boolean {
        val bucket = event.dateBucket()?.second ?: return false
        val bound =
            ranges
                ?.let { WherePredicate.parse(it) }
                ?.filterIsInstance<WherePredicate.Between>()
                ?.firstOrNull { it.key == bucket.name }
                ?.from
                ?: return false

        return bucket.isRelative(bound)
    }

    private fun EdgeAggregationEvent.dateBucket(): Pair<Group.Field, Bucket.Date>? = group.fields.firstNotNullOfOrNull { field -> (field.bucket as? Bucket.Date)?.let { field to it } }

    private fun parseFqn(fqn: String): Pair<String, String> {
        val dot = fqn.indexOf('.')
        require(dot > 0 && dot < fqn.lastIndex) {
            "rank table must be a fully-qualified `database.table`, got: $fqn"
        }
        return fqn.substring(0, dot) to fqn.substring(dot + 1)
    }

    private companion object {
        private val MAPPER = jacksonObjectMapper()

        const val SUCCESS = "SUCCESS"
        const val ERROR = "ERROR"
        const val SKIPPED = "SKIPPED"
    }
}

private data class Ranking(
    val database: String,
    val table: String,
    val topk: String,
    val entity: String,
    val topkDimensionValue: String,
    val dimensionValues: List<String>,
    val properties: Map<String, String> = emptyMap(),
)

internal data class RankingInputs(
    val directedSource: String,
    val entity: String,
    val topkDimensionValue: String,
    val dimensionValues: List<String>,
    val properties: Map<String, String>,
    val ranges: String?,
) {
    companion object {
        private val PLACEHOLDER = Regex("""\{([a-zA-Z_][a-zA-Z0-9_]*)}""")

        fun from(
            event: EdgeAggregationEvent,
            direction: Direction,
            topk: Topk,
        ): RankingInputs {
            val directedSource = if (direction == Direction.IN) event.target else event.source
            return RankingInputs(
                directedSource = directedSource,
                entity = if (topk.entity == AggregationConstants.Topk.GLOBAL_ENTITY) AggregationConstants.Topk.GLOBAL_ENTITY else directedSource,
                topkDimensionValue = fieldValue(topk.dimension, event),
                dimensionValues = event.group.dimensionFields(topk).map { fieldValue(it.name, event) },
                properties = topk.additionalProperties.associateWith { fieldValue(it, event) },
                ranges = topk.ranges.takeIf { it.isNotEmpty() }?.let { interpolate(it, event) },
            )
        }

        private fun fieldValue(
            name: String,
            event: EdgeAggregationEvent,
        ): String =
            when (name) {
                "source", "_source" -> event.source
                "target", "_target" -> event.target
                else -> event.properties[name]?.toString().orEmpty()
            }

        private fun interpolate(
            template: String,
            event: EdgeAggregationEvent,
        ): String =
            PLACEHOLDER.replace(template) { match ->
                val value =
                    when (val key = match.groupValues[1]) {
                        "source", "_source" -> event.source
                        "target", "_target" -> event.target
                        else -> event.properties[key]?.toString()
                    }
                value?.let { Regex.escapeReplacement(it) } ?: Regex.escapeReplacement(match.value)
            }
    }
}

data class EdgeAggregationEvent(
    val type: AggregationType,
    val database: String,
    val table: String,
    val source: String,
    val target: String,
    val properties: Map<String, Any?>,
    val direction: DirectionType,
    val group: Group,
    val aggregations: Aggregations,
) {
    companion object {
        fun of(
            type: AggregationType,
            database: String,
            table: String,
            item: AggregationItemPayload,
            group: Group,
        ): EdgeAggregationEvent =
            EdgeAggregationEvent(
                type = type,
                database = database,
                table = table,
                source = item.edge.source.toString(),
                target = item.edge.target.toString(),
                properties = item.edge.properties,
                direction = group.directionType,
                group = group,
                aggregations = group.aggregations,
            )
    }
}

data class TopkRefreshMessage(
    val type: AggregationType,
    val item: RefreshItem,
) {
    data class RefreshItem(
        val database: String,
        val table: String,
        val topk: String,
        val source: String,
        val target: String,
        val direction: String,
        val ranges: String,
        val entity: String,
        val topkDimensionValue: String,
        val dimensionValues: String,
        val properties: Map<String, String>,
        val refreshAt: Long,
    )

    companion object {
        fun of(
            type: AggregationType,
            database: String,
            table: String,
            topk: String,
            source: String,
            target: String,
            direction: String,
            ranges: String,
            entity: String,
            topkDimensionValue: String,
            dimensionValues: String,
            properties: Map<String, String>,
            refreshAt: Long,
        ): TopkRefreshMessage =
            TopkRefreshMessage(
                type,
                item =
                    RefreshItem(
                        database = database,
                        table = table,
                        topk = topk,
                        source = source,
                        target = target,
                        direction = direction,
                        ranges = ranges,
                        entity = entity,
                        topkDimensionValue = topkDimensionValue,
                        dimensionValues = dimensionValues,
                        properties = properties,
                        refreshAt = refreshAt,
                    ),
            )
    }
}
