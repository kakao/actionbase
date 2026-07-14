package com.kakao.actionbase.engine.service

import com.kakao.actionbase.v2.core.metadata.Direction as V2Direction

import com.kakao.actionbase.core.edge.Edge
import com.kakao.actionbase.core.edge.payload.AggregationExpireItemPayload
import com.kakao.actionbase.core.edge.payload.AggregationExpireResult
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

import java.time.Clock

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class AggregationService(
    private val queryService: QueryService,
    private val mutationService: MutationService,
    private val engine: AggregationEngine,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun getAggregations(type: AggregationType? = null): List<QualifiedAggregations> = engine.getListWithAggregations(type)

    fun aggregate(
        type: AggregationType,
        items: List<AggregationItemPayload>,
    ): Mono<List<AggregationResult>> =
        Flux
            .fromIterable(items)
            .flatMapIterable { it.createEvent(type) }
            .flatMap { event -> processAggregations(event, type) }
            .collectList()

    fun expires(items: List<AggregationExpireItemPayload>): Mono<List<AggregationExpireResult>> =
        Flux
            .fromIterable(
                items
                    .groupBy { Triple(it.database, it.table, it.edge.source.toString()) }
                    .map { (key, group) ->
                        AggregationExpireEvent(
                            database = key.first,
                            table = key.second,
                            source = key.third,
                            expiresAt = group.maxOf { (it.edge.properties["expiresAt"] as Number).toLong() },
                        )
                    },
            ).flatMap { processExpire(it) }
            .collectList()

    fun AggregationItemPayload.createEvent(type: AggregationType): List<EdgeAggregationEvent> =
        when (type) {
            AggregationType.TOPK -> createTopkEvent(this)
        }

    private fun createTopkEvent(item: AggregationItemPayload): List<EdgeAggregationEvent> {
        val isExpire = item.database == AggregationConstants.TOPK_DATABASE && item.table == AggregationConstants.TOPK_EXPIRE_TABLE

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
        val (scoreDatabase, scoreTable) = topk.scoreFqn

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

                val version = clock.millis()
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
                        if (scoreStatus == "ERROR" || topk.expireAfterMillis <= 0) {
                            return@flatMap Mono.just(base.copy(status = scoreStatus))
                        }

                        val expiresAt = version + topk.expireAfterMillis

                        mutationService
                            .mutate(
                                database = AggregationConstants.TOPK_DATABASE,
                                alias = AggregationConstants.TOPK_EXPIRE_TABLE,
                                unresolvedEvents =
                                    listOf(
                                        MutationItem(
                                            type = EventType.INSERT,
                                            edge =
                                                Edge(
                                                    version = clock.millis(),
                                                    source = AggregationConstants.expireSource(table = "$database.$table", topk = topk.topk, entity = directedSource, target = directedTarget),
                                                    target = AggregationConstants.expireTarget(table = "$database.$table", topk = topk.topk, entity = directedSource, target = directedTarget, expiresAt = expiresAt),
                                                    properties =
                                                        mapOf(
                                                            "expiresAt" to expiresAt,
                                                            "table" to "$database.$table",
                                                            "topk" to topk.topk,
                                                            "source" to source,
                                                            "target" to target,
                                                            "direction" to direction.name,
                                                            "ranges" to ranges,
                                                            "processed" to false,
                                                        ),
                                                ),
                                        ),
                                    ),
                            ).map { expireResults ->
                                base.copy(
                                    status = if (expireResults.any { it.status == "ERROR" }) "ERROR" else "SUCCESS",
                                )
                            }
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

    private fun processExpire(event: AggregationExpireEvent): Mono<AggregationExpireResult> {
        val base =
            AggregationExpireResult(
                database = event.database,
                table = event.table,
                source = event.source,
                status = "SKIPPED",
                error = null,
            )

        return queryService
            .scan(
                database = event.database,
                table = event.table,
                index = AggregationConstants.TOPK_EXPIRE_TABLE_INDEX,
                start = event.source,
                direction = V2Direction.OUT,
                ranges = "expiresAt:lte:${event.expiresAt}",
            ).map {
                val version = clock.millis()

                it.edges.map { edge ->
                    Edge(
                        version = version,
                        source = edge.source.toString(),
                        target = edge.target.toString(),
                        properties = edge.properties,
                    )
                }
            }.flatMap { edge ->
                mutationService
                    .mutate(
                        database = event.database,
                        alias = event.table,
                        unresolvedEvents = edge.map { edge -> MutationItem(type = EventType.DELETE, edge = edge) },
                    ).map { response ->
                        base.copy(status = if (response.any { it.status == "ERROR" }) "ERROR" else "SUCCESS")
                    }
            }.onErrorResume { err ->
                Mono.just(base.copy(status = "ERROR", error = err.message))
            }
    }

    companion object {
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

data class AggregationExpireEvent(
    val database: String,
    val table: String,
    val source: String,
    val expiresAt: Long,
)
