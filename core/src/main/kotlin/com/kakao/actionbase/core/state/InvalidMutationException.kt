package com.kakao.actionbase.core.state

/**
 * A permanently invalid mutation (e.g. an activating write leaving a non-nullable field unset).
 * Surfaces as a per-item INVALID status; a replay/WAL consumer must not retry it.
 */
class InvalidMutationException(
    message: String,
) : IllegalArgumentException(message)
