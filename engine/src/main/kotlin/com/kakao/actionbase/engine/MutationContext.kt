package com.kakao.actionbase.engine

import com.kakao.actionbase.engine.metadata.MutationModeContext

/**
 * Request-scoped context used by MutationService in the mutation pipeline.
 */
data class MutationContext(
    val database: String,
    val alias: String,
    val table: String,
    val mutationMode: MutationModeContext,
    val audit: Audit,
    val requestId: String,
)
