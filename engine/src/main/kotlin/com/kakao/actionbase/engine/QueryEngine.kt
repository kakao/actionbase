package com.kakao.actionbase.engine

import com.kakao.actionbase.engine.binding.TableBinding

/**
 * Query engine abstraction used by the V3 Query path.
 * Mirrors [MutationEngine] for the read side of CQRS.
 */
interface QueryEngine {
    /**
     * Resolves a table binding from a database/alias pair.
     */
    fun getTableBinding(
        database: String,
        alias: String,
    ): TableBinding
}
