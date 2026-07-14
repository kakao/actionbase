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
import com.kakao.actionbase.core.metadata.common.TopKTableNames
import com.kakao.actionbase.core.metadata.common.Topk
import com.kakao.actionbase.core.metadata.common.TopkScope
import com.kakao.actionbase.core.metadata.payload.AggregationType
import com.kakao.actionbase.core.metadata.payload.ExpireTableRef
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

    // Distinct physical expire tables declared across every topk. An external sweeper scans this
    // list — not the topk metadata itself — so it never needs to know which topk/group/table an
    // expire row originated from; that context already travels with the row (see writeExpireEntry).
    fun getExpireTables(): List<ExpireTableRef> =
        engine
            .getAllQualifiedGroups()
            .flatMap { it.groups }
            .flatMap { it.aggregations.topk }
            .map { it.table.expire }
            .filter { it.isNotBlank() }
            .distinct()
            .map { fqn -> parseFqn(fqn).let { (database, table) -> ExpireTableRef(database, table) } }

    // A) Original-CDC path: re-aggregate every topk declared on the matching groups and, when a
    // topk configures expiry, record when this event must be re-checked (see writeExpireEntry).
    fun aggregate(
        type: AggregationType,
        items: List<AggregationItemPayload>,
    ): Mono<List<AggregationResult>> =
        Flux
            .fromIterable(items)
            .flatMapIterable { item -> createEvent(type, item) }
            .flatMap { event -> processAggregations(event, type) }
            .collectList()

    // B) Sweep path: scan one fixed expire-table partition for rows whose expiredAt has passed,
    // re-aggregate each directly from its stored payload (no CDC round-trip, no public endpoint
    // call), and delete the row once its score has been refreshed. Never re-writes an expire row
    // for the item it just processed. Safe to call repeatedly/concurrently for the same partition:
    // a row another call already deleted is simply absent from the next scan.
    fun sweep(
        expireDatabase: String,
        expireTable: String,
        partition: Long,
        now: Long,
    ): Mono<List<AggregationResult>> = sweepPage(expireDatabase, expireTable, partition, now, offset = null)

    // Pages through the scan since one partition can hold more expired rows than a single scan
    // page returns. Deleting each row as it's swept means a later page never re-sees an earlier
    // one, so paging by the scan's own offset (rather than re-scanning from the start) is safe.
    private fun sweepPage(
        expireDatabase: String,
        expireTable: String,
        partition: Long,
        now: Long,
        offset: String?,
    ): Mono<List<AggregationResult>> =
        queryService
            .scan(
                database = expireDatabase,
                table = expireTable,
                index = EXPIRED_AT_INDEX,
                start = partition,
                direction = Direction.OUT,
                offset = offset,
                ranges = "expiredAt:lte:$now",
            ).flatMap { page ->
                Flux
                    .fromIterable(page.edges)
                    .flatMap { expired -> sweepOne(expireDatabase, expireTable, expired) }
                    .collectList()
                    .flatMap { results ->
                        if (!page.hasNext) {
                            Mono.just(results)
                        } else {
                            sweepPage(expireDatabase, expireTable, partition, now, offset = page.offset).map { results + it }
                        }
                    }
            }

    private fun sweepOne(
        expireDatabase: String,
        expireTable: String,
        expired: EdgePayload,
    ): Mono<AggregationResult> {
        val payload = objectMapper.readValue(expired.properties["payload"] as String, ExpirePayload::class.java)
        val event = toEvent(payload) ?: return Mono.empty()

        return aggregateTopk(event, writeExpireOnSuccess = false)
            .flatMap { result ->
                mutationService
                    .mutate(
                        database = expireDatabase,
                        alias = expireTable,
                        unresolvedEvents =
                            listOf(
                                MutationItem(
                                    type = EventType.DELETE,
                                    edge = Edge(version = System.currentTimeMillis(), source = expired.source, target = expired.target),
                                ),
                            ),
                    ).thenReturn(result)
            }
    }

    // Resolves a sweep payload back to the exact (group, topk) it was written for. The payload
    // travels with a group/topk name rather than the schema itself, so this stays correct even if
    // the schema's group ordering or other topks change between the write and the sweep.
    private fun toEvent(payload: ExpirePayload): EdgeAggregationEvent? {
        val tb = engine.getTableBinding(database = payload.database, alias = payload.table)
        if (tb.schema !is ModelSchema.Edge && tb.schema !is ModelSchema.MultiEdge) return null

        val group = tb.schema.groupsOrNull().orEmpty().firstOrNull { it.group == payload.group } ?: return null
        val topk = group.aggregations.topk.firstOrNull { it.topk == payload.topk } ?: return null

        return EdgeAggregationEvent(
            database = payload.database,
            table = payload.table,
            edge = payload.edge,
            group = group,
            direction = payload.direction,
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

        return groups.flatMap { group ->
            group.aggregations.topk.flatMap { topk ->
                group.directionType.directions().map { direction ->
                    EdgeAggregationEvent(
                        database = item.database,
                        table = item.table,
                        edge = item.edge,
                        group = group,
                        direction = direction,
                        topk = topk,
                    )
                }
            }
        }
    }

    private fun processAggregations(
        event: EdgeAggregationEvent,
        type: AggregationType,
    ): Mono<AggregationResult> =
        when (type) {
            AggregationType.TOPK -> processTopk(event, writeExpireOnSuccess = true)
        }

    private fun processTopk(
        event: EdgeAggregationEvent,
        writeExpireOnSuccess: Boolean,
    ): Mono<AggregationResult> = aggregateTopk(event, writeExpireOnSuccess)

    private fun aggregateTopk(
        event: EdgeAggregationEvent,
        writeExpireOnSuccess: Boolean,
    ): Mono<AggregationResult> {
        val database = event.database
        val table = event.table
        val source = event.edge.source.toString()
        val target = event.edge.target.toString()
        val direction = event.direction
        val topk = event.topk
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
                                            target = directedTarget,
                                            properties = mapOf("segment" to encodeSegment(ranges), "score" to score),
                                        ),
                                ),
                            ),
                    ).flatMap { results ->
                        val result = base.copy(status = if (results.any { it.status == "ERROR" }) "ERROR" else "SUCCESS")
                        if (result.status != "SUCCESS" || topk.expireAfterMillis < 0 || !writeExpireOnSuccess) {
                            Mono.just(result)
                        } else {
                            writeExpireEntry(event, entity, directedTarget).map { result }
                        }
                    }
            }.onErrorResume { err ->
                Mono.just(base.copy(status = "ERROR", error = err.message))
            }
    }

    // Records when this event must be re-checked against the sliding window. One row is written
    // per contributing event (keyed by database/table/topk/direction/entity/target/expiredAt), so
    // an event that arrives later never rewrites — or resets the clock on — an earlier event's
    // expiry. The row keeps enough of the original event (group/topk name + the raw edge) in
    // `properties.payload` so sweep can re-trigger this exact aggregation without needing a CDC.
    private fun writeExpireEntry(
        event: EdgeAggregationEvent,
        entity: String,
        directedTarget: String,
    ): Mono<List<MutationResult>> {
        val topk = event.topk
        val (expireDatabase, expireTable) = parseFqn(topk.table.expire)
        val expiredAt = event.edge.version + topk.expireAfterMillis
        val partition =
            TopKTableNames.expirePartition(
                database = event.database,
                table = event.table,
                topk = topk.topk,
                direction = event.direction,
                entity = entity,
                target = directedTarget,
            )
        val expireTarget =
            TopKTableNames.expireTargetKey(
                database = event.database,
                table = event.table,
                topk = topk.topk,
                direction = event.direction,
                entity = entity,
                target = directedTarget,
                expiredAt = expiredAt,
            )
        val payload =
            ExpirePayload(
                database = event.database,
                table = event.table,
                group = event.group.group,
                topk = topk.topk,
                direction = event.direction,
                edge = event.edge,
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
        private const val EXPIRED_AT_INDEX = "expired_at_asc"
    }
}

// One (group, topk, direction) worth of work against a single source edge. Built either straight
// from an incoming CDC item (one event per topk × direction the matching groups declare) or
// reconstructed from a stored expire payload during sweep.
data class EdgeAggregationEvent(
    val database: String,
    val table: String,
    val edge: EdgePayload,
    val group: Group,
    val direction: Direction,
    val topk: Topk,
)

// What an expire row remembers about the event that created it, so sweep can redo exactly this
// aggregation without needing the schema, a CDC, or a public API call.
private data class ExpirePayload(
    val database: String,
    val table: String,
    val group: String,
    val topk: String,
    val direction: Direction,
    val edge: EdgePayload,
)
