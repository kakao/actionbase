package com.kakao.actionbase.engine.service

import com.kakao.actionbase.core.edge.Edge
import com.kakao.actionbase.core.edge.payload.AggregationItemPayload
import com.kakao.actionbase.core.edge.payload.AggregationResult
import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest.MutationItem
import com.kakao.actionbase.core.edge.payload.EdgePayload
import com.kakao.actionbase.core.edge.payload.MutationResult
import com.kakao.actionbase.core.edge.payload.RefreshAggregationPayload
import com.kakao.actionbase.core.edge.payload.RefreshEntryPayload
import com.kakao.actionbase.core.metadata.QualifiedAggregations
import com.kakao.actionbase.core.metadata.common.AggregationConstants
import com.kakao.actionbase.core.metadata.common.AggregationType
import com.kakao.actionbase.core.metadata.common.Bucket
import com.kakao.actionbase.core.metadata.common.Direction
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.metadata.common.RankTarget
import com.kakao.actionbase.core.metadata.common.Topk
import com.kakao.actionbase.core.metadata.common.TopkScope
import com.kakao.actionbase.core.state.EventType
import com.kakao.actionbase.engine.AggregationEngine

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class AggregationService(
    private val queryService: QueryService,
    private val mutationService: MutationService,
    private val engine: AggregationEngine,
) {
    fun getAggregations(type: AggregationType? = null): List<QualifiedAggregations> = engine.getListWithAggregations(type)

    // The caller owns partition assignment: a cron worker knows the fixed partition count
    // (AggregationConstants.TOPK_REFRESH_PARTITIONS) and asks for one partition at a time.
    fun getRefreshEntries(
        partition: Long,
        refreshAtLte: Long,
        limit: Int,
    ): Mono<List<RefreshEntryPayload>> {
        require(partition in 0 until AggregationConstants.TOPK_REFRESH_PARTITIONS) {
            "partition must be in [0, ${AggregationConstants.TOPK_REFRESH_PARTITIONS}), got: $partition"
        }
        return queryService
            .scan(
                database = AggregationConstants.TOPK_DATABASE,
                table = AggregationConstants.TOPK_REFRESH_TABLE,
                index = "refresh_at_asc",
                start = partition,
                direction = Direction.OUT,
                limit = limit,
                ranges = "refreshAt:lte:$refreshAtLte",
            ).map { response -> response.edges.map { edge -> edge.toRefreshEntryPayload() } }
    }

    fun aggregate(
        type: AggregationType,
        items: List<AggregationItemPayload>,
    ): Mono<List<AggregationResult>> =
        Flux
            .fromIterable(items)
            .flatMapIterable { item -> createEvent(type, item) }
            .flatMap { event -> processAggregations(event) }
            .collectList()

    fun refresh(entries: List<RefreshEntryPayload>): Mono<List<AggregationResult>> =
        Flux
            .fromIterable(entries)
            .flatMap { entry -> refreshOne(entry).map { result -> entry to result } }
            .collectList()
            .flatMap { refreshed ->
                if (refreshed.isEmpty()) {
                    Mono.just(emptyList())
                } else {
                    deleteRefreshedEntries(refreshed.map { it.first })
                        .thenReturn(refreshed.map { it.second })
                }
            }

    private fun refreshOne(entry: RefreshEntryPayload): Mono<AggregationResult> {
        val payload = entry.aggregation

        val target =
            when (payload.type) {
                AggregationType.TOPK -> payload.resolveAsTopkTarget()
            } ?: return Mono.empty()

        return aggregateTopk(target.event, target.direction, target.topk, writeRefreshOnSuccess = false)
    }

    private fun deleteRefreshedEntries(entries: List<RefreshEntryPayload>): Mono<List<MutationResult>> =
        mutationService.mutate(
            database = AggregationConstants.TOPK_DATABASE,
            alias = AggregationConstants.TOPK_REFRESH_TABLE,
            unresolvedEvents =
                entries.map { entry ->
                    MutationItem(
                        type = EventType.DELETE,
                        edge = Edge(version = System.currentTimeMillis(), source = entry.partition, target = entry.key),
                    )
                },
        )

    private fun RefreshAggregationPayload.resolveAsTopkTarget(): ResolvedTopkTarget? {
        val tb = engine.getTableBinding(database = database, alias = table)
        if (tb.schema !is ModelSchema.Edge && tb.schema !is ModelSchema.MultiEdge) return null

        val group =
            tb.schema
                .groupsOrNull()
                .orEmpty()
                .firstOrNull { it.group == this.group } ?: return null
        val topk = group.aggregations.topk.firstOrNull { it.topk == this.topk } ?: return null

        return ResolvedTopkTarget(
            event =
                EdgeAggregationEvent(
                    type = AggregationType.TOPK,
                    database = database,
                    table = table,
                    edge = edge,
                    group = group,
                ),
            direction = direction,
            topk = topk,
        )
    }

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
            AggregationType.TOPK -> createTopkEvents(item)
        }

    private fun createTopkEvents(item: AggregationItemPayload): List<EdgeAggregationEvent> {
        val tb = engine.getTableBinding(database = item.database, alias = item.table)

        require(tb.schema is ModelSchema.Edge || tb.schema is ModelSchema.MultiEdge) {
            "Aggregation is only supported for Edge and MultiEdge tables."
        }

        val groups = tb.schema.groupsOrNull().orEmpty()

        return groups.map { group ->
            EdgeAggregationEvent(
                type = AggregationType.TOPK,
                database = item.database,
                table = item.table,
                edge = item.edge,
                group = group,
            )
        }
    }

    private fun processAggregations(event: EdgeAggregationEvent): Flux<AggregationResult> =
        when (event.type) {
            AggregationType.TOPK -> processTopk(event, writeRefreshOnSuccess = true)
        }

    private fun processTopk(
        event: EdgeAggregationEvent,
        writeRefreshOnSuccess: Boolean,
    ): Flux<AggregationResult> {
        val directionTopkPairs =
            event.group.aggregations.topk
                .flatMap { topk ->
                    event.group.directionType
                        .directions()
                        .map { it to topk }
                }

        return Flux
            .fromIterable(directionTopkPairs)
            .flatMap { (direction, topk) -> aggregateTopk(event, direction, topk, writeRefreshOnSuccess) }
    }

    private fun aggregateTopk(
        event: EdgeAggregationEvent,
        direction: Direction,
        topk: Topk,
        writeRefreshOnSuccess: Boolean,
    ): Mono<AggregationResult> {
        val database = event.database
        val table = event.table
        val source = event.edge.source.toString()
        val target = event.edge.target.toString()
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

        // The AGG query always scores `directedSource` (the row `direction` points the group at).
        // `rankedValue`/`entity` are independent of that — they read the raw edge endpoints
        // directly, because which endpoint is "who" vs. "what's being ranked" is a property of
        // the topk declaration, not of the direction chosen to avoid a fat AGG row.
        val directedSource = if (direction == Direction.IN) target else source
        val rankedValue = if (topk.rankTarget == RankTarget.TARGET) target else source
        val entity =
            if (topk.scope == TopkScope.GLOBAL) {
                AggregationConstants.GLOBAL_ENTITY
            } else if (topk.rankTarget == RankTarget.TARGET) {
                source
            } else {
                target
            }
        val ranges =
            topk.ranges.takeIf { it.isNotEmpty() }?.let {
                interpolate(template = it, source = source, target = target, properties = event.edge.properties)
            }
        val segment = if (topk.scope == TopkScope.GLOBAL) ranges else null

        return queryService
            .agg(
                database = database,
                table = table,
                group = event.group.group,
                start = listOf(directedSource),
                direction = direction,
                ranges = ranges,
            ).flatMap { response ->
                val score =
                    response.groups
                        .firstOrNull()
                        ?.value
                        ?.toDouble() ?: 0.0
                val scoreSource =
                    AggregationConstants.scoreSource(
                        database = database,
                        table = table,
                        topk = topk.topk,
                        direction = direction,
                        entity = entity,
                        segment = segment,
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
                                            target = rankedValue,
                                            properties = mapOf("score" to score),
                                        ),
                                ),
                            ),
                    ).flatMap { results ->
                        val result = base.copy(status = if (results.any { it.status == "ERROR" }) "ERROR" else "SUCCESS")
                        if (result.status != "SUCCESS" || topk.refreshAfterMillis < 0 || !writeRefreshOnSuccess) {
                            Mono.just(result)
                        } else {
                            writeRefreshEntry(event, direction, topk, entity, segment, rankedValue).map { result }
                        }
                    }
            }.onErrorResume { err ->
                Mono.just(base.copy(status = "ERROR", error = err.message))
            }
    }

    private fun writeRefreshEntry(
        event: EdgeAggregationEvent,
        direction: Direction,
        topk: Topk,
        entity: String,
        segment: String?,
        rankedValue: String,
    ): Mono<List<MutationResult>> {
        val refreshAt = edgeVersionMillis(event.group, event.edge.version) + topk.refreshAfterMillis
        val partition =
            AggregationConstants.refreshSource(
                database = event.database,
                table = event.table,
                topk = topk.topk,
                direction = direction,
                entity = entity,
                segment = segment,
                target = rankedValue,
            )
        val refreshTarget =
            AggregationConstants.refreshTarget(
                database = event.database,
                table = event.table,
                topk = topk.topk,
                direction = direction,
                entity = entity,
                segment = segment,
                target = rankedValue,
                refreshAt = refreshAt,
            )
        val payload =
            RefreshAggregationPayload(
                type = event.type,
                database = event.database,
                table = event.table,
                group = event.group.group,
                topk = topk.topk,
                direction = direction,
                edge = event.edge,
            )

        return mutationService.mutate(
            database = AggregationConstants.TOPK_DATABASE,
            alias = AggregationConstants.TOPK_REFRESH_TABLE,
            unresolvedEvents =
                listOf(
                    MutationItem(
                        type = EventType.INSERT,
                        edge =
                            Edge(
                                version = System.currentTimeMillis(),
                                source = partition,
                                target = refreshTarget,
                                properties =
                                    mapOf(
                                        "refreshAt" to refreshAt,
                                        "payload" to objectMapper.writeValueAsString(payload),
                                    ),
                            ),
                    ),
                ),
        )
    }

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

    private fun edgeVersionMillis(
        group: Group,
        version: Long,
    ): Long =
        when (versionUnit(group)) {
            Bucket.ValueUnit.SECOND -> Math.multiplyExact(version, 1_000L)
            Bucket.ValueUnit.MILLISECOND -> version
            Bucket.ValueUnit.MICROSECOND -> Math.floorDiv(version, 1_000L)
            Bucket.ValueUnit.NANOSECOND -> Math.floorDiv(version, 1_000_000L)
        }

    private fun versionUnit(group: Group): Bucket.ValueUnit =
        group.fields
            .firstNotNullOfOrNull { field ->
                if (field.name == "version") {
                    (field.bucket as? Bucket.Date)?.unit
                } else {
                    null
                }
            } ?: Bucket.ValueUnit.MILLISECOND

    private fun EdgePayload.toRefreshEntryPayload(): RefreshEntryPayload =
        RefreshEntryPayload(
            partition = source.toString().toLong(),
            key = target.toString(),
            aggregation = objectMapper.readValue(properties["payload"] as String),
        )

    private fun parseFqn(fqn: String): Pair<String, String> {
        val dot = fqn.indexOf('.')
        require(dot > 0 && dot < fqn.lastIndex) {
            "score table must be a fully-qualified `database.table`, got: $fqn"
        }
        return fqn.substring(0, dot) to fqn.substring(dot + 1)
    }

    private companion object {
        private val PLACEHOLDER = Regex("""\{([a-zA-Z_][a-zA-Z0-9_]*)}""")
        private val objectMapper = jacksonObjectMapper()
    }
}

data class EdgeAggregationEvent(
    val type: AggregationType,
    val database: String,
    val table: String,
    val edge: EdgePayload,
    val group: Group,
)

private data class ResolvedTopkTarget(
    val event: EdgeAggregationEvent,
    val direction: Direction,
    val topk: Topk,
)
