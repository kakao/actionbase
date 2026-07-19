package com.kakao.actionbase.core.metadata.common

/**
 * Per-table behavior switches. Until table metadata carries features directly, they are
 * resolved from server configuration at database scope (`actionbase.database-level-features`).
 */
enum class TableFeature {
    /**
     * INSERT merges into the existing row: an omitted field keeps its current value instead of
     * being cleared to UNSET (snapshot). Not consumed yet — wired up when #415 moves behind
     * this gate.
     */
    INSERT_MERGE,
}
