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

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class AggregationService(
    private val queryService: QueryService,
    private val mutationService: MutationService,
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

    private fun ModelSchema.groupsOrNull(): List<Group>? =
        when (this) {
            is ModelSchema.Edge -> groups
            is ModelSchema.MultiEdge -> groups
            else -> null
        }

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

        val groups = tb.schema.groupsOrNull().orEmpty()

        return groups
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
        val (scoreDatabase, scoreTable) = parseFqn(topk.table.score)

        val (directedSource, directedTarget) = getSourceTargetPair(source, direction, target, group, properties)

        return queryService
            .agg(
                database = database,
                table = table,
                group = group.group,
                start = listOf(directedSource),
                direction = V2Direction.valueOf(direction.name),
                ranges = ranges,
            ).flatMap { response ->
                val score = response.groups.firstOrNull()?.value ?: 0L

                val version = System.currentTimeMillis()
                mutationService
                    .mutate(
                        database = scoreDatabase,
                        alias = scoreTable,
                        unresolvedEvents =
                            listOf(
                                MutationItem(
                                    type = EventType.INSERT,
                                    edge =
                                        Edge(
                                            version = version,
                                            source = AggregationConstants.scoreSource(entity = directedSource, topk.topk),
                                            target = directedTarget,
                                            properties = mapOf("score" to score),
                                        ),
                                ),
                            ),
                    ).flatMap { scoreResults ->
                        val scoreStatus = if (scoreResults.any { it.status == "ERROR" }) "ERROR" else "SUCCESS"
                        if (scoreStatus == "ERROR" || topk.refreshAfterMillis <= 0) {
                            return@flatMap Mono.just(base.copy(status = scoreStatus))
                        }

                        val refreshAt = version + topk.refreshAfterMillis

                        mutationService
                            .mutate(
                                database = AggregationConstants.TOPK_DATABASE,
                                alias = AggregationConstants.TOPK_REFRESH_TABLE,
                                unresolvedEvents =
                                    listOf(
                                        MutationItem(
                                            type = EventType.INSERT,
                                            edge =
                                                Edge(
                                                    version = System.currentTimeMillis(),
                                                    source = AggregationConstants.refreshSource(table = "$database.$table", topk = topk.topk, entity = directedSource, target = directedTarget),
                                                    target = AggregationConstants.refreshTarget(table = "$database.$table", topk = topk.topk, entity = directedSource, target = directedTarget, refreshAt = refreshAt),
                                                    properties =
                                                        mapOf(
                                                            "refreshAt" to refreshAt,
                                                            "table" to "$database.$table",
                                                            "topk" to topk.topk,
                                                            "directedSource" to directedSource,
                                                            "directedTarget" to directedTarget,
                                                            "direction" to direction.name,
                                                            "ranges" to ranges,
                                                            "processed" to false,
                                                        ),
                                                ),
                                        ),
                                    ),
                            ).map { refreshResults ->
                                base.copy(
                                    status = if (refreshResults.any { it.status == "ERROR" }) "ERROR" else "SUCCESS",
                                )
                            }
                    }
            }.onErrorResume { err ->
                Mono.just(base.copy(status = "ERROR", error = err.message))
            }
    }

    private fun getSourceTargetPair(
        source: String,
        direction: Direction,
        target: String,
        group: Group,
        properties: Map<String, Any?>,
    ): Pair<String, String> {
        val directedSource = if (direction == Direction.IN) target else source
        val directedTarget =
            scoreTargetOf(
                group = group,
                source = source,
                target = target,
                properties = properties,
                fallback = if (direction == Direction.IN) source else target,
            )

        return directedSource to directedTarget
    }

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

    /**
     * Segments the score row's `target` by joining the group's non-bucket fields with `|`.
     * Bucket fields are consumed by `ranges`. `_source` / `_target` resolve to the edge
     * endpoints; other names read from `properties`. Falls back to [fallback] when the group
     * has no non-bucket field.
     */
    private fun scoreTargetOf(
        group: Group,
        source: String,
        target: String,
        properties: Map<String, Any?>,
        fallback: String,
    ): String {
        val bucketless = group.fields.filter { it.bucket == null }
        if (bucketless.isEmpty()) return fallback
        return bucketless.joinToString("|") { field ->
            when (field.name) {
                "_source" -> source
                "_target" -> target
                else -> properties[field.name]?.toString().orEmpty()
            }
        }
    }

    private fun parseFqn(fqn: String): Pair<String, String> {
        val dot = fqn.indexOf('.')
        require(dot > 0 && dot < fqn.lastIndex) {
            "score table must be a fully-qualified `database.table`, got: $fqn"
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
