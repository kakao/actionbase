package com.kakao.actionbase.engine.service.aggregation

import com.kakao.actionbase.v2.core.metadata.Direction as V2Direction

import com.kakao.actionbase.core.edge.Edge
import com.kakao.actionbase.core.edge.payload.AggregationItemPayload
import com.kakao.actionbase.core.edge.payload.AggregationResult
import com.kakao.actionbase.core.edge.payload.AggregationSweepResult
import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest.MutationItem
import com.kakao.actionbase.core.edge.payload.MutationResult
import com.kakao.actionbase.core.edge.payload.SweepItemPayload
import com.kakao.actionbase.core.edge.payload.TopkSweepItem
import com.kakao.actionbase.core.metadata.common.AggregationConstants
import com.kakao.actionbase.core.metadata.common.AggregationType
import com.kakao.actionbase.core.metadata.common.Aggregations
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
import com.kakao.actionbase.v2.engine.util.getLogger

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

    override fun aggregate(item: AggregationItemPayload): Flux<AggregationResult> =
        Flux
            .fromIterable(createTopkEvent(item))
            .flatMap { event -> processTopk(event, topks = event.aggregations.topk) }

    override fun sweep(item: SweepItemPayload): Mono<AggregationSweepResult> {
        require(item is TopkSweepItem) { "TopkAggregationHandler handles TopkSweepItem, got ${item::class.simpleName}" }

        fun result(
            status: String,
            error: String? = null,
        ) = AggregationSweepResult(
            database = item.database,
            table = item.table,
            topk = item.topk,
            entity = item.entity,
            status = status,
            error = error,
        )

        val group =
            engine
                .getTableBinding(database = item.database, alias = item.table)
                .schema
                .groupsOrNull()
                .orEmpty()
                .firstOrNull { g -> g.aggregations.topk.any { it.topk == item.topk } }
                ?: return Mono.just(result(SKIPPED))

        val topk = group.aggregations.topk.first { it.topk == item.topk }
        val direction = Direction.valueOf(item.direction)
        val directedSource = if (direction == Direction.IN) item.target else item.source
        val dimensionValues = item.dimensionValues.split("|").filter { it.isNotEmpty() }
        val (rankDatabase, rankTable) = parseFqn(topk.rank)

        return writeRank(
            sourceDatabase = item.database,
            sourceTable = item.table,
            group = group.group,
            start = directedSource,
            direction = direction,
            ranges = item.ranges.takeIf { it.isNotEmpty() },
            ranking =
                Ranking(
                    database = rankDatabase,
                    table = rankTable,
                    topk = item.topk,
                    entity = item.entity,
                    topkDimensionValue = item.topkDimensionValue,
                    dimensionValues = dimensionValues,
                ),
        ).map { results ->
            result(status = if (results.any { it.status == ERROR }) ERROR else SUCCESS)
        }.onErrorResume { err ->
            logger.error("topk sweep failed for {}.{} topk={}", item.database, item.table, item.topk, err)
            Mono.just(result(status = ERROR, error = err.message))
        }
    }

    private fun ModelSchema.groupsOrNull(): List<Group>? =
        when (this) {
            is ModelSchema.Edge -> groups
            is ModelSchema.MultiEdge -> groups
            else -> null
        }

    private fun createTopkEvent(item: AggregationItemPayload): List<EdgeAggregationEvent> {
        val tb = engine.getTableBinding(database = item.database, alias = item.table)

        require(tb.schema is ModelSchema.Edge || tb.schema is ModelSchema.MultiEdge) {
            "Aggregation is only supported for Edge and MultiEdge tables."
        }

        return tb.schema
            .groupsOrNull()
            .orEmpty()
            .filter { it.aggregations.topk.isNotEmpty() }
            .map { group ->
                EdgeAggregationEvent.of(type = AggregationType.TOPK, database = item.database, table = item.table, item, group)
            }
    }

    private fun processTopk(
        event: EdgeAggregationEvent,
        topks: List<Topk>,
    ): Flux<AggregationResult> {
        val directionTopkPairs = topks.flatMap { topk -> event.direction.directions().map { it to topk } }

        return Flux
            .fromIterable(directionTopkPairs)
            .flatMap { (direction, topk) ->
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

                val ranges =
                    topk.ranges.takeIf { it.isNotEmpty() }?.let {
                        interpolate(template = it, source = event.source, target = event.target, properties = event.properties)
                    }

                val directedSource = if (direction == Direction.IN) event.target else event.source
                val entity = if (topk.entity == AggregationConstants.Topk.GLOBAL_ENTITY) AggregationConstants.Topk.GLOBAL_ENTITY else directedSource
                val topkDimensionValue = resolveField(topk.dimension, event.source, event.target, event.properties)
                val dimensionValues =
                    event.group.fields
                        .filter { it.bucket == null && !matchesDimension(it.name, topk.dimension) }
                        .map { resolveField(it.name, event.source, event.target, event.properties) }
                val (rankDatabase, rankTable) = parseFqn(topk.rank)
                val ranking =
                    Ranking(
                        database = rankDatabase,
                        table = rankTable,
                        topk = topk.topk,
                        entity = entity,
                        topkDimensionValue = topkDimensionValue,
                        dimensionValues = dimensionValues,
                    )

                writeRank(
                    sourceDatabase = event.database,
                    sourceTable = event.table,
                    group = event.group.group,
                    start = directedSource,
                    direction = direction,
                    ranges = ranges,
                    ranking = ranking,
                ).flatMap { results ->
                    val status = if (results.any { it.status == ERROR }) ERROR else SUCCESS
                    if (status == ERROR || topk.refreshAfterMillis <= 0) {
                        return@flatMap Mono.just(result(status = status))
                    }

                    writeRefresh(event, ranking, direction, ranges, refreshAfterMillis = topk.refreshAfterMillis)
                        .map { result(status = if (it.results.any { r -> r.status == ERROR }) ERROR else SUCCESS) }
                }.onErrorResume { err ->
                    logger.error("topk aggregate failed for {}.{} topk={}", event.database, event.table, topk.topk, err)
                    Mono.just(result(ERROR, err.message))
                }
            }
    }

    /**
     * Runs the aggregation query for one ranking and writes its rank row.
     * Shared by the aggregate flow (which then enqueues a refresh message) and the sweep flow
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
                                            source = AggregationConstants.Topk.rankSource(topk = ranking.topk, entity = ranking.entity, dimensionValues = ranking.dimensionValues),
                                            target = ranking.topkDimensionValue,
                                            properties = mapOf("metric" to metric),
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
    ): Mono<EnqueueResponse> {
        val refreshAt = System.currentTimeMillis() + refreshAfterMillis
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
                        dimensionValues = ranking.dimensionValues.joinToString("|"),
                        refreshAt = refreshAt,
                    ),
            )

        return queueService.enqueue(
            namespace = AggregationConstants.Topk.DATABASE,
            queue = AggregationConstants.Topk.REFRESH_TABLE,
            request = EnqueueRequest(messages = listOf(message)),
        )
    }

    /**
     * Resolves a dimension field name against the current edge.
     *   - `source` / `_source` and `target` / `_target` resolve to the edge endpoints.
     *   - any other name reads from `properties`.
     */
    private fun resolveField(
        name: String,
        source: String,
        target: String,
        properties: Map<String, Any?>,
    ): String =
        when (name) {
            "source", "_source" -> source
            "target", "_target" -> target
            else -> properties[name]?.toString().orEmpty()
        }

    private fun matchesDimension(
        fieldName: String,
        dimension: String,
    ): Boolean = fieldName.removePrefix("_") == dimension.removePrefix("_")

    /**
     * Replaces `{name}` placeholders in a ranges template.
     *   - `{source}` / `{target}` resolve to the edge endpoints.
     *   - `{prop}` resolves to `properties[prop]` when present, otherwise the placeholder is kept.
     */
    private fun interpolate(
        template: String,
        source: String,
        target: String,
        properties: Map<String, Any?>,
    ): String =
        PLACEHOLDER.replace(template) { match ->
            val value =
                when (val key = match.groupValues[1]) {
                    "source", "_source" -> source
                    "target", "_target" -> target
                    else -> properties[key]?.toString()
                }
            value?.let { Regex.escapeReplacement(it) } ?: Regex.escapeReplacement(match.value)
        }

    private fun parseFqn(fqn: String): Pair<String, String> {
        val dot = fqn.indexOf('.')
        require(dot > 0 && dot < fqn.lastIndex) {
            "rank table must be a fully-qualified `database.table`, got: $fqn"
        }
        return fqn.substring(0, dot) to fqn.substring(dot + 1)
    }

    private companion object {
        private val PLACEHOLDER = Regex("""\{([a-zA-Z_][a-zA-Z0-9_]*)}""")

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
)

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

/** The refresh queue message body: `{type, item}`, enqueued when a top-K opts into `refreshAfterMillis`. */
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
                        refreshAt = refreshAt,
                    ),
            )
    }
}
