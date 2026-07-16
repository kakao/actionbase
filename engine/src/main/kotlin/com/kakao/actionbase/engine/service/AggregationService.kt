package com.kakao.actionbase.engine.service

import com.kakao.actionbase.core.edge.Edge
import com.kakao.actionbase.core.edge.payload.AggregationItemPayload
import com.kakao.actionbase.core.edge.payload.AggregationResult
import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest.MutationItem
import com.kakao.actionbase.core.edge.payload.EdgePayload
import com.kakao.actionbase.core.edge.payload.MutationResult
import com.kakao.actionbase.core.edge.payload.RefreshEntryPayload
import com.kakao.actionbase.core.metadata.QualifiedAggregations
import com.kakao.actionbase.core.metadata.common.AggregationConstants
import com.kakao.actionbase.core.metadata.common.AggregationType
import com.kakao.actionbase.core.metadata.common.Bucket
import com.kakao.actionbase.core.metadata.common.Direction
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

    // The caller owns partition assignment: a cron worker knows the fixed partition count
    // (AggregationConstants.TOPK_REFRESH_PARTITIONS) and asks for one partition at a time.
    fun getRefreshEntries(
        partition: Long,
        now: Long,
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
                ranges = "refreshAt:lte:$now",
            ).map { response -> response.edges.mapNotNull { edge -> edge.toRefreshEntryPayload() } }
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

    // The entry itself is the parsed refresh coordinate. Unresolvable entries (missing metadata,
    // inputs the coordinate can't express) are skipped and left in the refresh table, so the
    // next cycle retries them.
    private fun refreshOne(entry: RefreshEntryPayload): Mono<AggregationResult> {
        val tb = engine.getTableBinding(database = entry.database, alias = entry.table)
        if (tb.schema !is ModelSchema.Edge && tb.schema !is ModelSchema.MultiEdge) return Mono.empty()
        val (group, topk) =
            tb.schema
                .groupsOrNull()
                .orEmpty()
                .firstNotNullOfOrNull { group ->
                    group.aggregations.topk
                        .firstOrNull { it.topk == entry.topk }
                        ?.let { group to it }
                } ?: return Mono.empty()

        val entity = entry.entity
        val rankedValue = entry.rankedField

        // Reconstruct the edge endpoints the declared field refs pin down; a property-backed
        // ref carries no endpoint information.
        val edgeSource =
            when {
                topk.entity == AggregationConstants.SOURCE_FIELD -> entity
                topk.rankedField == AggregationConstants.SOURCE_FIELD -> rankedValue
                else -> null
            }
        val edgeTarget =
            when {
                topk.entity == AggregationConstants.TARGET_FIELD -> entity
                topk.rankedField == AggregationConstants.TARGET_FIELD -> rankedValue
                else -> null
            }
        val directedSource =
            (if (entry.direction == Direction.IN) edgeTarget else edgeSource) ?: return Mono.empty()
        val ranges =
            if (topk.entity == AggregationConstants.GLOBAL_ENTITY) {
                // A GLOBAL segment is the interpolated ranges verbatim — reuse it as-is.
                entry.segment
            } else {
                topk.ranges.takeIf { it.isNotEmpty() }?.let {
                    interpolate(template = it, source = edgeSource, target = edgeTarget, properties = emptyMap())
                }
            }
        // A placeholder that survived interpolation means the coordinate can't reproduce the
        // original AGG scope — skip rather than aggregate against a garbage predicate.
        if (ranges != null && PLACEHOLDER.containsMatchIn(ranges)) return Mono.empty()

        return aggregateTopk(
            database = entry.database,
            table = entry.table,
            group = group,
            topk = topk,
            direction = entry.direction,
            directedSource = directedSource,
            entity = entity,
            segment = entry.segment,
            rankedValue = rankedValue,
            ranges = ranges,
            reportSource = entity,
            reportTarget = rankedValue,
            writeRefresh = null,
        )
    }

    private fun deleteRefreshedEntries(entries: List<RefreshEntryPayload>): Mono<List<MutationResult>> =
        mutationService.mutate(
            database = AggregationConstants.TOPK_DATABASE,
            alias = AggregationConstants.TOPK_REFRESH_TABLE,
            unresolvedEvents =
                entries.map { entry ->
                    MutationItem(
                        type = EventType.DELETE,
                        edge =
                            Edge(
                                version = System.currentTimeMillis(),
                                source = entry.toRefreshSource(),
                                target = entry.toRefreshTarget(),
                            ),
                    )
                },
        )

    private fun RefreshEntryPayload.toRefreshSource(): Long =
        AggregationConstants.refreshSource(
            database = database,
            table = table,
            topk = topk,
            direction = direction,
            entity = entity,
            segment = segment,
            rankedField = rankedField,
        )

    private fun RefreshEntryPayload.toRefreshTarget(): String =
        AggregationConstants.refreshTarget(
            database = database,
            table = table,
            topk = topk,
            direction = direction,
            entity = entity,
            segment = segment,
            rankedField = rankedField,
            refreshAt = refreshAt,
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
        val source = event.edge.source.toString()
        val target = event.edge.target.toString()

        // The AGG query always scores `directedSource` (the row `direction` points the group at).
        // `entity`/`rankedValue` are independent of that — the topk declaration names the fields
        // they read ("whose ranking is it" vs. "what's being ranked"), not the direction chosen
        // to avoid a fat AGG row.
        val directedSource = if (direction == Direction.IN) target else source
        val entity =
            if (topk.entity == AggregationConstants.GLOBAL_ENTITY) {
                AggregationConstants.GLOBAL_ENTITY
            } else {
                resolveFieldRef(topk.entity, source, target, event.edge.properties)
            }
        val rankedValue = resolveFieldRef(topk.rankedField, source, target, event.edge.properties)
        if (entity == null || rankedValue == null) {
            // The event doesn't carry the declared entity/rankedField value — nothing to rank.
            return Mono.just(
                AggregationResult(
                    database = event.database,
                    table = event.table,
                    source = source,
                    target = target,
                    status = "SKIPPED",
                    error = null,
                ),
            )
        }
        val ranges =
            topk.ranges.takeIf { it.isNotEmpty() }?.let {
                interpolate(template = it, source = source, target = target, properties = event.edge.properties)
            }
        val segment = if (topk.entity == AggregationConstants.GLOBAL_ENTITY) ranges else null

        return aggregateTopk(
            database = event.database,
            table = event.table,
            group = event.group,
            topk = topk,
            direction = direction,
            directedSource = directedSource,
            entity = entity,
            segment = segment,
            rankedValue = rankedValue,
            ranges = ranges,
            reportSource = source,
            reportTarget = target,
            writeRefresh =
                if (writeRefreshOnSuccess && topk.refreshAfterMillis >= 0) {
                    { writeRefreshEntry(event, direction, topk, entity, segment, rankedValue) }
                } else {
                    null
                },
        )
    }

    private fun aggregateTopk(
        database: String,
        table: String,
        group: Group,
        topk: Topk,
        direction: Direction,
        directedSource: String,
        entity: String,
        segment: String?,
        rankedValue: String,
        ranges: String?,
        reportSource: String,
        reportTarget: String,
        writeRefresh: (() -> Mono<List<MutationResult>>)?,
    ): Mono<AggregationResult> {
        val base =
            AggregationResult(
                database = database,
                table = table,
                source = reportSource,
                target = reportTarget,
                status = "SKIPPED",
                error = null,
            )
        val (scoreDatabase, scoreTable) = parseFqn(topk.table.score)

        return queryService
            .agg(
                database = database,
                table = table,
                group = group.group,
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
                        if (result.status != "SUCCESS" || writeRefresh == null) {
                            Mono.just(result)
                        } else {
                            writeRefresh().map { result }
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
                rankedField = rankedValue,
            )
        val refreshTarget =
            AggregationConstants.refreshTarget(
                database = event.database,
                table = event.table,
                topk = topk.topk,
                direction = direction,
                entity = entity,
                segment = segment,
                rankedField = rankedValue,
                refreshAt = refreshAt,
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
                                properties = mapOf("refreshAt" to refreshAt),
                            ),
                    ),
                ),
        )
    }

    private fun resolveFieldRef(
        ref: String,
        source: String,
        target: String,
        properties: Map<String, Any?>,
    ): String? =
        when (ref) {
            AggregationConstants.SOURCE_FIELD -> source
            AggregationConstants.TARGET_FIELD -> target
            else -> properties[ref]?.toString()
        }

    private fun interpolate(
        template: String,
        source: String?,
        target: String?,
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

    private fun EdgePayload.toRefreshEntryPayload(): RefreshEntryPayload? =
        AggregationConstants.parseRefreshTarget(target.toString())?.let {
            RefreshEntryPayload(
                database = it.database,
                table = it.table,
                topk = it.topk,
                direction = it.direction,
                entity = it.entity,
                segment = it.segment,
                rankedField = it.rankedField,
                refreshAt = it.refreshAt,
            )
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
    val edge: EdgePayload,
    val group: Group,
)
