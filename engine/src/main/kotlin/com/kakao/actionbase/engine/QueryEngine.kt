package com.kakao.actionbase.engine

import com.kakao.actionbase.engine.binding.TableBinding

/**
 * Query engine abstraction used by the V3 Query path.
 * Resolves table bindings without direct references to V2 internals (Graph, Label).
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
