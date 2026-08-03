package com.kakao.actionbase.engine.service

import com.kakao.actionbase.core.metadata.common.AggregationConstants
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.v2.core.metadata.Direction

internal data class RankScan(
    val database: String,
    val table: String,
    val index: String,
    val start: String,
    val direction: Direction,
) {
    companion object {
        fun from(
            schema: ModelSchema,
            database: String,
            table: String,
            topk: String,
            entity: String,
            dimensionValues: List<String>,
        ): RankScan {
            val config =
                schema.topkByName[topk]
                    ?: throw IllegalArgumentException("Unknown topk `$topk` for $database.$table.")
            val (rankDatabase, rankTable) = parseFqn(config.rank)

            return RankScan(
                database = rankDatabase,
                table = rankTable,
                index = AggregationConstants.Topk.RANK_INDEX,
                start =
                    AggregationConstants.Topk.rankSource(
                        database = database,
                        table = table,
                        topk = topk,
                        entity = entity,
                        dimensionValues = dimensionValues,
                    ),
                direction = Direction.OUT,
            )
        }
    }
}
