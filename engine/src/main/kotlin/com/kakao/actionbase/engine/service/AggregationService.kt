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
import com.kakao.actionbase.engine.QualifiedGroups

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class AggregationService(
    private val queryService: QueryService,
    private val mutationService: MutationService,
    private val engine: AggregationEngine,
) {
    fun getAggregations(): List<AggregationMetadata> = engine.getAllQualifiedGroups().map { it.toMetadata() }

    fun aggregate(
        type: AggregationType,
        items: List<AggregationItemPayload>,
    ): Mono<List<AggregationResult>> =
        Flux
            .fromIterable(items)
            .flatMapIterable { item -> createEvent(type, item) }
            .flatMap { event -> processAggregations(event, type) }
            .collectList()

    private fun QualifiedGroups.toMetadata(): AggregationMetadata =
        AggregationMetadata(
            database = database,
            table = table,
            aggregations = groups.map { it.aggregations },
        )

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
        val isExpire = item.database == TopKTableNames.EXPIRE_TABLE_DATABASE && item.table == TopKTableNames.EXPIRE_TABLE_NAME

        val (database, table) =
            if (isExpire) {
                require(item.edge.properties.containsKey("table")) {
                    "table property is required for expire edges"
                }
                parseFqn(item.edge.properties["table"] as String)
            } else {
                item.database to item.table
            }

        val tb = engine.getTableBinding(database = database, alias = table)

        require(tb.schema is ModelSchema.Edge || tb.schema is ModelSchema.MultiEdge) {
            "Aggregation is only supported for Edge and MultiEdge tables."
        }

        val groups = tb.schema.groupsOrNull().orEmpty()

        return groups
            .filter { it.aggregations.topk.isNotEmpty() }
            .map { group ->
                if (isExpire) {
                    EdgeAggregationEvent.of(type = AggregationType.TOPK, database, table, properties = item.edge.properties, group)
                } else {
                    EdgeAggregationEvent.of(type = AggregationType.TOPK, database = item.database, table = item.table, item, group)
                }
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
                    if (item.isExpire) {
                        item.properties["ranges"]?.toString()
                    } else {
                        topk.ranges.takeIf { it.isNotEmpty() }?.let {
                            interpolate(template = it, source = item.source, target = item.target, properties = item.properties)
                        }
                    }

                aggregateTopk(
                    database = item.database,
                    table = item.table,
                    source = item.source,
                    target = item.target,
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

        val directedSource = if (direction == Direction.IN) target else source
        val directedTarget = if (direction == Direction.IN) source else target

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
                                            source = TopKTableNames.scoreSourceKey(entity = directedSource, topk.topk),
                                            target = directedTarget,
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
    val type: AggregationType,
    val isExpire: Boolean = false,
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

        fun of(
            type: AggregationType,
            database: String,
            table: String,
            properties: Map<String, Any?>,
            group: Group,
        ): EdgeAggregationEvent =
            EdgeAggregationEvent(
                type = type,
                isExpire = true,
                database = database,
                table = table,
                source = checkNotNull(properties["source"]?.toString()) { "`source` property is required for expire events" },
                target = checkNotNull(properties["target"]?.toString()) { "`target` property is required for expire events" },
                properties = properties,
                direction =
                    checkNotNull(properties["direction"]?.toString()) { "`direction` property is required for expire events" }
                        .let { DirectionType.valueOf(it) },
                group = group,
                aggregations = group.aggregations,
            )
    }
}
