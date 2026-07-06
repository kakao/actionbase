package com.kakao.actionbase.engine.service

import com.kakao.actionbase.v2.core.metadata.Direction as V2Direction

import com.kakao.actionbase.core.edge.Edge
import com.kakao.actionbase.core.edge.payload.AggregationItemPayload
import com.kakao.actionbase.core.edge.payload.AggregationResult
import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest.MutationItem
import com.kakao.actionbase.core.metadata.AggregationMetadata
import com.kakao.actionbase.core.metadata.common.Aggregations
import com.kakao.actionbase.core.metadata.common.Direction
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.metadata.common.TopKTableNames
import com.kakao.actionbase.core.metadata.common.Topk
import com.kakao.actionbase.core.metadata.payload.AggregationType
import com.kakao.actionbase.core.state.EventType
import com.kakao.actionbase.engine.AggregationEngine
import com.kakao.actionbase.v2.engine.v3.V3TableDescriptor

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class AggregationService(
    private val queryService: QueryService,
    private val mutationService: MutationService,
    private val engine: AggregationEngine,
) {
    fun getAggregations(): List<AggregationMetadata> = engine.getAllTables().map { it.toMetadata() }

    fun aggregate(
        type: AggregationType,
        items: List<AggregationItemPayload>,
    ): Mono<List<AggregationResult>> =
        Flux
            .fromIterable(items)
            .flatMapIterable { item -> createEvent(item) }
            .flatMap { event -> processAggregations(event, type) }
            .collectList()

    private fun V3TableDescriptor.toMetadata(): AggregationMetadata {
        val aggregations = schema.groupsOrNull().orEmpty().mapNotNull { it.aggregations }

        return AggregationMetadata(
            database = database,
            table = table,
            aggregations = aggregations,
        )
    }

    private fun ModelSchema.groupsOrNull(): List<Group>? =
        when (this) {
            is ModelSchema.Edge -> groups
            is ModelSchema.MultiEdge -> groups
            else -> null
        }

    private fun createEvent(item: AggregationItemPayload): List<EdgeAggregationEvent> {
        val tb = engine.getTableBinding(database = item.database, alias = item.table)

        require(tb.schema is ModelSchema.Edge || tb.schema is ModelSchema.MultiEdge) {
            "Aggregation is only supported for Edge and MultiEdge tables."
        }

        val groups = tb.schema.groupsOrNull().orEmpty()

        return groups
            .filter { it.aggregations != null }
            .map { group ->
                EdgeAggregationEvent(
                    database = item.database,
                    table = item.table,
                    source = item.edge.source.toString(),
                    target = item.edge.target.toString(),
                    properties = item.edge.properties,
                    direction = group.directionType,
                    group = group,
                    aggregations = group.aggregations!!,
                )
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
                aggregateTopk(
                    database = item.database,
                    table = item.table,
                    source = item.source,
                    target = item.target,
                    properties = item.properties,
                    direction = direction,
                    group = item.group,
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
        val scoreFqn = topk.table?.score ?: return Mono.just(base)
        val (scoreDatabase, scoreTable) = parseFqn(scoreFqn)
        val start = if (direction == Direction.IN) target else source
        val resolvedRanges = topk.ranges?.let { interpolate(template = it, source, target, properties) }

        return queryService
            .agg(
                database = database,
                table = table,
                group = group.group,
                start = listOf(start),
                direction = V2Direction.valueOf(direction.name),
                ranges = resolvedRanges,
            ).flatMap { response ->
                val score = response.groups.firstOrNull()?.value ?: 0L

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
                                            version = System.currentTimeMillis(),
                                            source = TopKTableNames.scoreSourceKey(entity = source, topk.topk),
                                            target = target,
                                            properties = mapOf("score" to score),
                                        ),
                                ),
                            ),
                    ).map { results ->
                        base.copy(status = if (results.any { it.status == "ERROR" }) "ERROR" else "SUCCESS")
                    }
            }.onErrorResume { err ->
                Mono.just(base.copy(status = "ERROR", error = err.message))
            }
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
    val database: String,
    val table: String,
    val source: String,
    val target: String,
    val properties: Map<String, Any?>,
    val direction: DirectionType,
    val group: Group,
    val aggregations: Aggregations,
)
