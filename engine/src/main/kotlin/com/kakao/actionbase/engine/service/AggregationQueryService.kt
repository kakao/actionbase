package com.kakao.actionbase.engine.service

import com.kakao.actionbase.core.edge.payload.DataFrameEdgePayload
import com.kakao.actionbase.engine.AggregationEngine
import com.kakao.actionbase.v2.engine.sql.ScanFilter

import reactor.core.publisher.Mono

class AggregationQueryService(
    private val queryService: QueryService,
    private val engine: AggregationEngine,
) {
    // TODO(topk-port): reimplement against the new rank-table model
    //   (rank = topk.rank, rankSource(topk, entity, dimensionValues), metric-ordered scan).
    //   The old score-table implementation is preserved on `feature/per-entity-top-k-query`.
    fun topk(
        database: String,
        table: String,
        topk: String,
        entity: String,
        limit: Int = ScanFilter.defaultLimit,
        offset: String? = null,
    ): Mono<DataFrameEdgePayload> = TODO("port topk query to the new rank-table model")
}
