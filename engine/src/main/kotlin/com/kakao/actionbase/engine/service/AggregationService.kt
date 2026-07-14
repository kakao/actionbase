package com.kakao.actionbase.engine.service

import com.kakao.actionbase.core.edge.Edge
import com.kakao.actionbase.core.edge.payload.AggregationItemPayload
import com.kakao.actionbase.core.edge.payload.AggregationResult
import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest.MutationItem
import com.kakao.actionbase.core.edge.payload.EdgePayload
import com.kakao.actionbase.core.edge.payload.MutationResult
import com.kakao.actionbase.core.metadata.AggregationMetadata
import com.kakao.actionbase.core.metadata.common.Direction
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.metadata.common.RankTarget
import com.kakao.actionbase.core.metadata.common.TopKTableNames
import com.kakao.actionbase.core.metadata.common.Topk
import com.kakao.actionbase.core.metadata.common.TopkScope
import com.kakao.actionbase.core.metadata.payload.AggregationType
import com.kakao.actionbase.core.metadata.payload.RefreshTableRef
import com.kakao.actionbase.core.state.EventType
import com.kakao.actionbase.engine.AggregationEngine
import com.kakao.actionbase.engine.QualifiedGroups

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

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

    fun getRefreshTables(): List<RefreshTableRef> =
        engine
            .getAllQualifiedGroups()
            .flatMap { it.groups }
            .flatMap { it.aggregations.topk }
            .map { it.table.refresh }
            .filter { it.isNotBlank() }
            .distinct()
            .map { fqn -> parseFqn(fqn).let { (database, table) -> RefreshTableRef(database, table) } }

    fun aggregate(
        type: AggregationType,
        items: List<AggregationItemPayload>,
    ): Mono<List<AggregationResult>> =
        Flux
            .fromIterable(items)
            .flatMapIterable { item -> createEvent(type, item) }
            .flatMap { event -> processAggregations(event) }
            .collectList()

    fun sweep(
        type: AggregationType,
        refreshDatabase: String,
        refreshTable: String,
        partition: Long,
        now: Long,
    ): Mono<List<AggregationResult>> = sweepPage(type, refreshDatabase, refreshTable, partition, now, offset = null)

    private fun sweepPage(
        type: AggregationType,
        refreshDatabase: String,
        refreshTable: String,
        partition: Long,
        now: Long,
        offset: String?,
    ): Mono<List<AggregationResult>> =
        queryService
            .scan(
                database = refreshDatabase,
                table = refreshTable,
                index = REFRESH_AT_INDEX,
                start = partition,
                direction = Direction.OUT,
                offset = offset,
                ranges = "refreshAt:lte:$now",
            ).flatMap { page ->
                Flux
                    .fromIterable(page.edges)
                    .flatMap { due -> sweepOne(type, refreshDatabase, refreshTable, due) }
                    .collectList()
                    .flatMap { results ->
                        if (!page.hasNext) {
                            Mono.just(results)
                        } else {
                            sweepPage(type, refreshDatabase, refreshTable, partition, now, offset = page.offset).map { results + it }
                        }
                    }
            }

    private fun sweepOne(
        type: AggregationType,
        refreshDatabase: String,
        refreshTable: String,
        due: EdgePayload,
    ): Mono<AggregationResult> {
        val payload = objectMapper.readValue(due.properties["payload"] as String, RefreshPayload::class.java)
        if (payload.type != type) return Mono.empty()

        val target =
            when (type) {
                AggregationType.TOPK -> payload.resolveAsTopkTarget()
            } ?: return Mono.empty()

        return aggregateTopk(target.event, target.direction, target.topk, writeRefreshOnSuccess = false)
            .flatMap { result ->
                mutationService
                    .mutate(
                        database = refreshDatabase,
                        alias = refreshTable,
                        unresolvedEvents =
                            listOf(
                                MutationItem(
                                    type = EventType.DELETE,
                                    edge = Edge(version = System.currentTimeMillis(), source = due.source, target = due.target),
                                ),
                            ),
                    ).thenReturn(result)
            }
    }

    private fun RefreshPayload.resolveAsTopkTarget(): ResolvedTopkTarget? {
        val tb = engine.getTableBinding(database = database, alias = table)
        if (tb.schema !is ModelSchema.Edge && tb.schema !is ModelSchema.MultiEdge) return null

        val group = tb.schema.groupsOrNull().orEmpty().firstOrNull { it.group == this.group } ?: return null
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
            event.group.aggregations.topk.flatMap { topk -> event.group.directionType.directions().map { it to topk } }

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
                TopKTableNames.GLOBAL_ENTITY
            } else if (topk.rankTarget == RankTarget.TARGET) {
                source
            } else {
                target
            }
        val ranges =
            topk.ranges.takeIf { it.isNotEmpty() }?.let {
                interpolate(template = it, source = source, target = target, properties = event.edge.properties)
            }

        return queryService
            .agg(
                database = database,
                table = table,
                group = event.group.group,
                start = listOf(directedSource),
                direction = direction,
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
                                            target = rankedValue,
                                            properties = mapOf("segment" to encodeSegment(ranges), "score" to score),
                                        ),
                                ),
                            ),
                    ).flatMap { results ->
                        val result = base.copy(status = if (results.any { it.status == "ERROR" }) "ERROR" else "SUCCESS")
                        if (result.status != "SUCCESS" || topk.refreshAfterMillis < 0 || !writeRefreshOnSuccess) {
                            Mono.just(result)
                        } else {
                            writeRefreshEntry(event, direction, topk, entity, rankedValue).map { result }
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
        rankedValue: String,
    ): Mono<List<MutationResult>> {
        val (refreshDatabase, refreshTable) = parseFqn(topk.table.refresh)
        val refreshAt = event.edge.version + topk.refreshAfterMillis
        val partition =
            TopKTableNames.refreshPartition(
                database = event.database,
                table = event.table,
                topk = topk.topk,
                direction = direction,
                entity = entity,
                target = rankedValue,
            )
        val refreshTarget =
            TopKTableNames.refreshTargetKey(
                database = event.database,
                table = event.table,
                topk = topk.topk,
                direction = direction,
                entity = entity,
                target = rankedValue,
                refreshAt = refreshAt,
            )
        val payload =
            RefreshPayload(
                type = event.type,
                database = event.database,
                table = event.table,
                group = event.group.group,
                topk = topk.topk,
                direction = direction,
                edge = event.edge,
            )

        return mutationService.mutate(
            database = refreshDatabase,
            alias = refreshTable,
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
        private const val REFRESH_AT_INDEX = "refresh_at_asc"
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

private data class RefreshPayload(
    val type: AggregationType,
    val database: String,
    val table: String,
    val group: String,
    val topk: String,
    val direction: Direction,
    val edge: EdgePayload,
)
