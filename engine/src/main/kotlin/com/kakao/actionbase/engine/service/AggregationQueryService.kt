package com.kakao.actionbase.engine.service

import com.kakao.actionbase.core.edge.payload.DataFrameEdgePayload
import com.kakao.actionbase.engine.AggregationEngine
import com.kakao.actionbase.v2.engine.sql.ScanFilter

import reactor.core.publisher.Mono

class AggregationQueryService(
    private val queryService: QueryService,
    private val engine: AggregationEngine,
) {
    fun topk(
        database: String,
        table: String,
        topk: String,
        entity: String? = null,
        dimensionValues: Map<String, String> = emptyMap(),
        limit: Int = ScanFilter.defaultLimit,
        offset: String? = null,
    ): Mono<DataFrameEdgePayload> {
        val tb = engine.getTableBinding(database = database, alias = table)
        val rank =
            RankScan.from(
                schema = tb.schema,
                database = database,
                table = tb.table,
                topk = topk,
                entity = entity,
                dimensionValues = dimensionValues,
            )

        return queryService.scan(
            database = rank.database,
            table = rank.table,
            index = rank.index,
            start = rank.start,
            direction = rank.direction,
            limit = limit,
            offset = offset,
        )
    }
}
