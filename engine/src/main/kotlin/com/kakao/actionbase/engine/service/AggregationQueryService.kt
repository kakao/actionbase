package com.kakao.actionbase.engine.service

import com.kakao.actionbase.v2.core.metadata.Direction as V2Direction

import com.kakao.actionbase.core.edge.payload.DataFrameEdgePayload
import com.kakao.actionbase.core.metadata.common.AggregationConstants
import com.kakao.actionbase.engine.AggregationEngine
import com.kakao.actionbase.v2.engine.sql.ScanFilter

import reactor.core.publisher.Mono

class AggregationQueryService(
    private val queryService: QueryService,
    private val engine: AggregationEngine,
) {
    /**
     * Reads back a materialized top-K ranking. Rank rows live in `topk.rank` keyed by
     * `topk | entity | dimensionValues...` with the ranked value as the target and `metric` as a
     * property; scanning the `metric_desc` index from that composite prefix yields the ranking in
     * descending metric order.
     */
    fun topk(
        database: String,
        table: String,
        topk: String,
        entity: String,
        dimensionValues: List<String> = emptyList(),
        limit: Int = ScanFilter.defaultLimit,
        offset: String? = null,
    ): Mono<DataFrameEdgePayload> {
        val tb = engine.getTableBinding(database = database, alias = table)
        val topkConfig =
            tb.schema.topkByName[topk]
                ?: throw IllegalArgumentException("Unknown topk `$topk` for $database.$table.")

        val (rankDatabase, rankTable) = parseFqn(topkConfig.rank)

        return queryService.scan(
            database = rankDatabase,
            table = rankTable,
            index = AggregationConstants.Topk.RANK_INDEX,
            start = AggregationConstants.Topk.rankSource(topk = topk, entity = entity, dimensionValues = dimensionValues),
            direction = V2Direction.OUT,
            limit = limit,
            offset = offset,
        )
    }
}
