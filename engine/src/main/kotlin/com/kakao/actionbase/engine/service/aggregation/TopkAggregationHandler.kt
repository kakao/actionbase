package com.kakao.actionbase.engine.service.aggregation

import com.kakao.actionbase.v2.core.metadata.Direction as V2Direction

import com.kakao.actionbase.core.edge.Edge
import com.kakao.actionbase.core.edge.payload.AggregationItemPayload
import com.kakao.actionbase.core.edge.payload.AggregationResult
import com.kakao.actionbase.core.edge.payload.AggregationSweepResult
import com.kakao.actionbase.core.edge.payload.AggregationSweepTarget
import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest.MutationItem
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
import com.kakao.actionbase.engine.service.MutationService
import com.kakao.actionbase.engine.service.QueryService

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class TopkAggregationHandler(
    private val queryService: QueryService,
    private val mutationService: MutationService,
    private val engine: AggregationEngine,
) : AggregationHandler {
    override val type: AggregationType = AggregationType.TOPK

    override fun aggregate(item: AggregationItemPayload): Flux<AggregationResult> =
        Flux
            .fromIterable(createTopkEvent(item))
            .flatMap { event -> processTopk(event, topks = event.aggregations.topk) }

    override fun sweep(target: AggregationSweepTarget): Mono<AggregationSweepResult> {
        val base =
            AggregationSweepResult(
                database = target.database,
                table = target.table,
                topk = target.topk,
                entity = target.entity,
                status = "SKIPPED",
                error = null,
            )

        return Mono
            .defer {
                val group =
                    engine
                        .getTableBinding(database = target.database, alias = target.table)
                        .schema
                        .groupsOrNull()
                        .orEmpty()
                        .firstOrNull { g -> g.aggregations.topk.any { it.topk == target.topk } }
                        ?: return@defer Mono.just(base)

                val topk = group.aggregations.topk.first { it.topk == target.topk }
                val direction = Direction.valueOf(target.direction)
                val directedSource = if (direction == Direction.IN) target.target else target.source
                val dimensionValues = target.dimensionValues.split("|").filter { it.isNotEmpty() }
                val rankKey =
                    RankKey(
                        topk = target.topk,
                        entity = target.entity,
                        topkDimensionValue = target.topkDimensionValue,
                        dimensionValues = dimensionValues,
                    )

                aggregateAndPutRank(
                    database = target.database,
                    table = target.table,
                    group = group.group,
                    directedSource = directedSource,
                    direction = direction,
                    ranges = target.ranges.takeIf { it.isNotEmpty() },
                    rankFqn = topk.rank,
                    rankKey = rankKey,
                ).map { outcome -> base.copy(status = outcome.status) }
            }.onErrorResume { err ->
                Mono.just(base.copy(status = "ERROR", error = err.message))
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
        item: EdgeAggregationEvent,
        topks: List<Topk>,
    ): Flux<AggregationResult> {
        val directionTopkPairs = topks.flatMap { topk -> item.direction.directions().map { it to topk } }

        return Flux
            .fromIterable(directionTopkPairs)
            .flatMap { (direction, topk) ->
                val base =
                    AggregationResult(
                        database = item.database,
                        table = item.table,
                        source = item.source,
                        target = item.target,
                        status = "SKIPPED",
                        error = null,
                    )

                val ranges =
                    topk.ranges.takeIf { it.isNotEmpty() }?.let {
                        interpolate(template = it, source = item.source, target = item.target, properties = item.properties)
                    }

                // The entity whose edges are aggregated: IN ranks by target, OUT by source.
                val directedSource = if (direction == Direction.IN) item.target else item.source
                // Per-entity stores that entity; global collapses every entity into a single sentinel row.
                val entity = if (topk.entity == AggregationConstants.GLOBAL_ENTITY) AggregationConstants.GLOBAL_ENTITY else directedSource
                val topkDimensionValue = resolveField(topk.dimension, item.source, item.target, item.properties)
                val dimensionValues =
                    item.group.fields
                        .filter { it.bucket == null && !matchesDimension(it.name, topk.dimension) }
                        .map { resolveField(it.name, item.source, item.target, item.properties) }
                val rankKey = RankKey(topk = topk.topk, entity = entity, topkDimensionValue = topkDimensionValue, dimensionValues = dimensionValues)

                aggregateAndPutRank(
                    database = item.database,
                    table = item.table,
                    group = item.group.group,
                    directedSource = directedSource,
                    direction = direction,
                    ranges = ranges,
                    rankFqn = topk.rank,
                    rankKey = rankKey,
                ).flatMap { outcome ->
                    if (outcome.status == "ERROR" || topk.refreshAfterMillis <= 0) {
                        return@flatMap Mono.just(base.copy(status = outcome.status))
                    }

                    val refreshAt = outcome.version + topk.refreshAfterMillis

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
                                                source =
                                                    AggregationConstants.refreshSource(
                                                        database = item.database,
                                                        table = item.table,
                                                        topk = topk.topk,
                                                        entity = entity,
                                                        topkDimensionValue = topkDimensionValue,
                                                        dimensionValues = dimensionValues,
                                                    ),
                                                target = refreshAt.toString(),
                                                properties =
                                                    mapOf(
                                                        "refreshAt" to refreshAt,
                                                        "database" to item.database,
                                                        "table" to item.table,
                                                        "topk" to topk.topk,
                                                        "source" to item.source,
                                                        "target" to item.target,
                                                        "direction" to direction.name,
                                                        "ranges" to ranges,
                                                        "entity" to entity,
                                                        "topkDimensionValue" to topkDimensionValue,
                                                        "dimensionValues" to dimensionValues.joinToString("|"),
                                                    ),
                                            ),
                                    ),
                                ),
                        ).map { refreshResults ->
                            base.copy(status = if (refreshResults.any { it.status == "ERROR" }) "ERROR" else "SUCCESS")
                        }
                }.onErrorResume { err ->
                    Mono.just(base.copy(status = "ERROR", error = err.message))
                }
            }
    }

    /**
     * Runs the aggregation query for one ranking and writes its rank row.
     * Shared by the aggregate flow (which then appends a refresh row) and the sweep flow
     * (which recomputes from an already-resolved [RankKey]).
     */
    private fun aggregateAndPutRank(
        database: String,
        table: String,
        group: String,
        directedSource: String,
        direction: Direction,
        ranges: String?,
        rankFqn: String,
        rankKey: RankKey,
    ): Mono<RankPutOutcome> {
        val (rankDatabase, rankTable) = parseFqn(rankFqn)

        return queryService
            .agg(
                database = database,
                table = table,
                group = group,
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
                                            source = AggregationConstants.rankSource(topk = rankKey.topk, entity = rankKey.entity, dimensionValues = rankKey.dimensionValues),
                                            target = rankKey.topkDimensionValue,
                                            properties = mapOf("metric" to metric),
                                        ),
                                ),
                            ),
                    ).map { rankResults ->
                        RankPutOutcome(
                            version = version,
                            metric = metric,
                            status = if (rankResults.any { it.status == "ERROR" }) "ERROR" else "SUCCESS",
                        )
                    }
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

private data class RankKey(
    val topk: String,
    val entity: String,
    val topkDimensionValue: String,
    val dimensionValues: List<String>,
)

private data class RankPutOutcome(
    val version: Long,
    val metric: Long,
    val status: String,
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
