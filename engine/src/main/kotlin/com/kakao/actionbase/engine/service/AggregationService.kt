package com.kakao.actionbase.engine.service

import com.kakao.actionbase.v2.core.metadata.Direction as V2Direction

import com.kakao.actionbase.core.edge.Edge
import com.kakao.actionbase.core.edge.payload.AggregationItemPayload
import com.kakao.actionbase.core.edge.payload.AggregationResult
import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest.MutationItem
import com.kakao.actionbase.core.metadata.QualifiedAggregations
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
import com.kakao.actionbase.engine.queue.QueueService

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class AggregationService(
    private val queryService: QueryService,
    private val mutationService: MutationService,
    private val queueService: QueueService,
    private val engine: AggregationEngine,
) {
    fun getAggregations(type: AggregationType? = null): List<QualifiedAggregations> = engine.getListWithAggregations(type)

    fun aggregate(
        type: AggregationType,
        items: List<AggregationItemPayload>,
    ): Mono<List<AggregationResult>> =
        Flux
            .fromIterable(items)
            .flatMapIterable { item -> createEvent(type, item) }
            .flatMap { event -> processAggregations(event, type) }
            .collectList()

    private fun createEvent(
        type: AggregationType,
        item: AggregationItemPayload,
    ): List<EdgeAggregationEvent> =
        when (type) {
            AggregationType.TOPK -> createTopkEvent(item)
        }

    private fun createTopkEvent(item: AggregationItemPayload): List<EdgeAggregationEvent> {
        val database = item.database
        val table = item.table

        val tb = engine.getTableBinding(database = database, alias = table)

        require(tb.schema is ModelSchema.Edge || tb.schema is ModelSchema.MultiEdge) {
            "Aggregation is only supported for Edge and MultiEdge tables."
        }

        return tb.schema.groups
            .filter { it.aggregations.topk.isNotEmpty() }
            .map { group ->
                EdgeAggregationEvent.of(type = AggregationType.TOPK, database = item.database, table = item.table, item, group)
            }
    }

    private fun processAggregations(
        event: EdgeAggregationEvent,
        type: AggregationType,
    ): Flux<AggregationResult> {
        val aggregations = event.aggregations
        return when (type) {
            AggregationType.TOPK -> processTopk(event, topks = aggregations.topk)
        }
    }

    private fun processTopk(
        item: EdgeAggregationEvent,
        topks: List<Topk>,
    ): Flux<AggregationResult> {
        val directionTopkPairs = topks.flatMap { topk -> item.direction.directions().map { it to topk } }

        return Flux
            .fromIterable(directionTopkPairs)
            .flatMap { (direction, topk) ->
                val ranges =
                    topk.ranges.takeIf { it.isNotEmpty() }?.let {
                        interpolate(template = it, source = item.source, target = item.target, properties = item.properties)
                    }

                aggregateTopk(
                    database = item.database,
                    table = item.table,
                    source = item.source,
                    target = item.target,
                    properties = item.properties,
                    direction = direction,
                    group = item.group,
                    ranges = ranges,
                    topk = topk,
                )
            }
    }

    private fun aggregateTopk(
        database: String,
        table: String,
        source: String,
        target: String,
        properties: Map<String, Any?>,
        direction: Direction,
        group: Group,
        ranges: String?,
        topk: Topk,
    ): Mono<AggregationResult> {
        val base =
            AggregationResult(
                database = database,
                table = table,
                source = source,
                target = target,
                status = "SKIPPED",
                error = null,
            )
        val (rankDatabase, rankTable) = parseFqn(topk.rank)

        // The entity whose edges are aggregated: IN ranks by target, OUT by source.
        val directedSource = if (direction == Direction.IN) target else source
        // Per-entity stores that entity; global collapses every entity into a single sentinel row.
        val entity = if (topk.entity == AggregationConstants.Topk.GLOBAL_ENTITY) AggregationConstants.Topk.GLOBAL_ENTITY else directedSource
        val topkDimensionValue = resolveField(topk.dimension, source, target, properties)
        val dimensionValues =
            group.fields
                .filter { it.bucket == null && !matchesDimension(it.name, topk.dimension) }
                .map { resolveField(it.name, source, target, properties) }

        return queryService
            .agg(
                database = database,
                table = table,
                group = group.group,
                start = listOf(directedSource),
                direction = V2Direction.valueOf(direction.name),
                ranges = ranges,
            ).flatMap { response ->
                val metric = response.groups.firstOrNull()?.value ?: 0L

                val version = System.currentTimeMillis()
                mutationService
                    .mutate(
                        database = rankDatabase,
                        alias = rankTable,
                        unresolvedEvents =
                            listOf(
                                MutationItem(
                                    type = EventType.INSERT,
                                    edge =
                                        Edge(
                                            version = version,
                                            source = AggregationConstants.Topk.rankSource(topk = topk.topk, entity = entity, dimensionValues = dimensionValues),
                                            target = topkDimensionValue,
                                            properties = mapOf("metric" to metric),
                                        ),
                                ),
                            ),
                    ).flatMap { rankResults ->
                        val rankStatus = if (rankResults.any { it.status == "ERROR" }) "ERROR" else "SUCCESS"
                        if (rankStatus == "ERROR" || topk.refreshAfterMillis <= 0) {
                            return@flatMap Mono.just(base.copy(status = rankStatus))
                        }

                        val refreshAt = version + topk.refreshAfterMillis

                        val message =
                            EnqueueMessage(
                                key =
                                    AggregationConstants.Topk.refreshKey(
                                        database = database,
                                        table = table,
                                        topk = topk.topk,
                                        entity = entity,
                                        topkDimensionValue = topkDimensionValue,
                                        dimensionValues = dimensionValues,
                                    ),
                                seq = refreshAt,
                                value =
                                    TopkRefreshMessage.of(
                                        type = AggregationType.TOPK,
                                        database = database,
                                        table = table,
                                        topk = topk.topk,
                                        source = source,
                                        target = target,
                                        direction = direction.name,
                                        ranges = ranges ?: "",
                                        entity = entity,
                                        topkDimensionValue = topkDimensionValue,
                                        dimensionValues = dimensionValues.joinToString("|"),
                                        refreshAt = refreshAt,
                                    ),
                            )

                        queueService
                            .enqueue(
                                namespace = AggregationConstants.Topk.DATABASE,
                                queue = AggregationConstants.Topk.REFRESH_TABLE,
                                request = EnqueueRequest(messages = listOf(message)),
                            ).map { refreshResults ->
                                base.copy(
                                    status = if (refreshResults.results.any { it.status == "ERROR" }) "ERROR" else "SUCCESS",
                                )
                            }
                    }
            }.onErrorResume { err ->
                Mono.just(base.copy(status = "ERROR", error = err.message))
            }
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
