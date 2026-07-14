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
    ): Mono<DataFrameEdgePayload> =
        Mono
            .fromCallable {
                val tb = engine.getTableBinding(database = database, alias = table)
                val topkConfig =
                    tb.schema.topkByName[topk]
                        ?: throw IllegalArgumentException("Unknown topk `$topk` for $database.$table.")

                topkConfig.scoreFqn
            }.flatMap { (scoreDatabase, scoreTable) ->
                queryService.scan(
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
