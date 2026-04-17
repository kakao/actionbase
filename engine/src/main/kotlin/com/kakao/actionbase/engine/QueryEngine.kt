package com.kakao.actionbase.engine

import com.kakao.actionbase.engine.binding.TableBinding

/**
 * Query engine abstraction used by the V3 Query path.
 * Decouples QueryService from Graph by exposing only the operations it needs.
 */
interface QueryEngine {
    fun getTableBinding(
        database: String,
        alias: String,
    ): TableBinding
}
