package com.kakao.actionbase.core.metadata.features

enum class TableFeature {
    /**
     * INSERT merges into the existing row: an omitted field keeps its current value instead of
     * being cleared to UNSET (snapshot).
     */
    INSERT_MERGE,
}
