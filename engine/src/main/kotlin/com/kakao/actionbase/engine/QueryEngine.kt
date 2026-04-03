package com.kakao.actionbase.engine

import com.kakao.actionbase.engine.binding.TableBinding
import com.kakao.actionbase.engine.query.ActionbaseQuery
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.label.Label
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.ScanFilter

import reactor.core.publisher.Mono

/**
 * Query engine abstraction used by the V3 Query path.
 * Decouples QueryService from Graph by exposing only the operations it needs.
 *
 * Note: some methods still expose V2 types (Label, ScanFilter, DataFrame) —
 * these will be abstracted in future iterations.
 */
interface QueryEngine {
    fun getTableBinding(
        database: String,
        alias: String,
    ): TableBinding

    fun getLabel(name: EntityName): Label

    fun singleStepQuery(scanFilter: ScanFilter): Mono<DataFrame>

    fun query(request: ActionbaseQuery): Mono<Map<String, DataFrame>>

    val encoderPoolSize: Int
}
