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
    fun topk(
        database: String,
        table: String,
        topk: String,
        entity: String,
        limit: Int = ScanFilter.defaultLimit,
        offset: String? = null,
    ): Mono<DataFrameEdgePayload> {
        val tb = engine.getTableBinding(database = database, alias = table)
        val topkConfig =
            tb.schema
                .groupsOrNull()
                .orEmpty()
                .flatMap { it.aggregations.topk }
                .firstOrNull { it.topk == topk }
                ?: throw IllegalArgumentException("Unknown topk `$topk` for $database.$table.")

        val (scoreDatabase, scoreTable) = parseFqn(fqn = topkConfig.table.score)

        return queryService.scan(
            database = scoreDatabase,
            table = scoreTable,
            index = AggregationConstants.TOPK_SCORE_TABLE_INDEX,
            start = AggregationConstants.scoreSource(entity = entity, topk = topk),
            direction = V2Direction.OUT,
            limit = limit,
            offset = offset,
        )
    }
}
