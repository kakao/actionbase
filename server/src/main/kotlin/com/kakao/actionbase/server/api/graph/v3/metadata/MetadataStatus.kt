package com.kakao.actionbase.server.api.graph.v3.metadata

enum class MetadataStatus {
    ACTIVE,
    INACTIVE,
    ALL,
    ;

    fun matches(active: Boolean): Boolean =
        when (this) {
            ACTIVE -> active
            INACTIVE -> !active
            ALL -> true
        }
}
