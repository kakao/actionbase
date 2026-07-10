package com.kakao.actionbase.engine.service

import com.kakao.actionbase.v2.core.metadata.Direction as V2Direction

import com.kakao.actionbase.core.edge.Edge
import com.kakao.actionbase.core.edge.payload.AggregationItemPayload
import com.kakao.actionbase.core.edge.payload.AggregationResult
import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest.MutationItem
import com.kakao.actionbase.core.edge.payload.EdgePayload
import com.kakao.actionbase.core.edge.payload.MutationResult
import com.kakao.actionbase.core.metadata.AggregationMetadata
import com.kakao.actionbase.core.metadata.common.Aggregations
import com.kakao.actionbase.core.metadata.common.Direction
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.metadata.common.TopKTableNames
import com.kakao.actionbase.core.metadata.common.Topk
import com.kakao.actionbase.core.metadata.common.TopkScope
import com.kakao.actionbase.core.metadata.payload.AggregationType
import com.kakao.actionbase.core.state.EventType
import com.kakao.actionbase.engine.AggregationEngine
import com.kakao.actionbase.engine.QualifiedGroups
import com.kakao.actionbase.v2.engine.util.objectMapper

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class AggregationService(
    private val queryService: QueryService,
    private val mutationService: MutationService,
    private val engine: AggregationEngine,
) {
    fun getAggregations(): List<AggregationMetadata> = engine.getAllQualifiedGroups().map { it.toMetadata() }

    fun aggregate(
        type: AggregationType,
        items: List<AggregationItemPayload>,
        isExpire: Boolean = false,
    ): Mono<List<AggregationResult>> =
        Flux
            .fromIterable(items)
            .flatMapIterable { item -> createEvent(type, item, isExpire) }
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
        isExpire: Boolean,
    ): List<EdgeAggregationEvent> =
        when (type) {
            AggregationType.TOPK -> createTopkEvent(item, isExpire)
        }

    private fun createTopkEvent(
        item: AggregationItemPayload,
        isExpire: Boolean,
    ): List<EdgeAggregationEvent> {
        val tb = engine.getTableBinding(database = item.database, alias = item.table)

        require(tb.schema is ModelSchema.Edge || tb.schema is ModelSchema.MultiEdge) {
            "Aggregation is only supported for Edge and MultiEdge tables."
        }

        val groups = tb.schema.groupsOrNull().orEmpty()

        // When item.topk is set, only that named topk is re-aggregated (used to refresh a single
        // expired topk without recomputing its siblings declared on the same group).
        return groups
            .mapNotNull { group ->
                val topks =
                    if (item.topk != null) group.aggregations.topk.filter { it.topk == item.topk } else group.aggregations.topk
                if (topks.isEmpty()) null else group.copy(aggregations = group.aggregations.copy(topk = topks))
            }.map { group -> EdgeAggregationEvent.of(type = AggregationType.TOPK, item, group, isExpire) }
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
                    item = item,
                    direction = direction,
                    ranges = ranges,
                    topk = topk,
                )
            }
    }

    private fun aggregateTopk(
        item: EdgeAggregationEvent,
        direction: Direction,
        ranges: String?,
        topk: Topk,
    ): Mono<AggregationResult> {
        val database = item.database
        val table = item.table
        val source = item.source
        val target = item.target
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
        val entity = if (topk.scope == TopkScope.GLOBAL) TopKTableNames.GLOBAL_ENTITY else directedSource

        return queryService
            .agg(
                database = database,
                table = table,
                group = item.group.group,
                start = listOf(directedSource),
                direction = V2Direction.valueOf(direction.name),
                ranges = ranges,
            ).flatMap { response ->
                val score = response.groups.firstOrNull()?.value?.toDouble() ?: 0.0
                val scoreSource =
                    TopKTableNames.scoreSourceKey(
                        database = database,
                        table = table,
                        topk = topk.topk,
                        direction = direction,
                        entity = entity,
                    )

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
                                            source = scoreSource,
                                            target = directedTarget,
                                            properties = mapOf("segment" to encodeSegment(ranges), "score" to score),
                                        ),
                                ),
                            ),
                    ).flatMap { results ->
                        val result = base.copy(status = if (results.any { it.status == "ERROR" }) "ERROR" else "SUCCESS")
                        if (result.status != "SUCCESS" || topk.expireAfterMillis < 0 || item.isExpire) {
                            Mono.just(result)
                        } else {
                            writeExpireEntry(item, direction, entity, topk).map { result }
                        }
                    }
            }.onErrorResume { err ->
                Mono.just(base.copy(status = "ERROR", error = err.message))
            }
    }

    // Records when this topk must be re-aggregated. Exactly one expire row exists per
    // (database, table, topk, direction, entity): the stable target key makes a later replay
    // upsert this row (extending expiredAt) instead of leaving a second, stale expire row behind.
    // The row keeps the original AggregationItemPayload in `properties.payload` so the expire
    // sweeper can re-trigger the aggregation without knowing anything about this group's schema.
    private fun writeExpireEntry(
        item: EdgeAggregationEvent,
        direction: Direction,
        entity: String,
        topk: Topk,
    ): Mono<List<MutationResult>> {
        val (expireDatabase, expireTable) = parseFqn(topk.table.expire)
        val expiredAt = System.currentTimeMillis() + topk.expireAfterMillis
        val partition =
            TopKTableNames.expirePartition(
                database = item.database,
                table = item.table,
                topk = topk.topk,
                direction = direction,
                entity = entity,
            )
        val expireTarget =
            TopKTableNames.expireTargetKey(
                database = item.database,
                table = item.table,
                topk = topk.topk,
                direction = direction,
                entity = entity,
            )
        val payload =
            AggregationItemPayload(
                database = item.database,
                table = item.table,
                edge = item.edge,
                topk = topk.topk,
            )

        return mutationService.mutate(
            database = expireDatabase,
            alias = expireTable,
            unresolvedEvents =
                listOf(
                    MutationItem(
                        type = EventType.INSERT,
                        edge =
                            Edge(
                                version = System.currentTimeMillis(),
                                source = partition,
                                target = expireTarget,
                                properties =
                                    mapOf(
                                        "expiredAt" to expiredAt,
                                        "payload" to objectMapper.writeValueAsString(payload),
                                    ),
                            ),
                    ),
                ),
        )
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

    private fun encodeSegment(ranges: String?): String? = ranges?.let { URLEncoder.encode(it, StandardCharsets.UTF_8) }

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
    val edge: EdgePayload,
    val direction: DirectionType,
    val group: Group,
    val aggregations: Aggregations,
    val isExpire: Boolean = false,
) {
    companion object {
        fun of(
            type: AggregationType,
            item: AggregationItemPayload,
            group: Group,
            isExpire: Boolean = false,
        ): EdgeAggregationEvent =
            EdgeAggregationEvent(
                type = type,
                database = item.database,
                table = item.table,
                source = item.edge.source.toString(),
                target = item.edge.target.toString(),
                properties = item.edge.properties,
                edge = item.edge,
                direction = group.directionType,
                group = group,
                aggregations = group.aggregations,
                isExpire = isExpire,
            )
    }
}
