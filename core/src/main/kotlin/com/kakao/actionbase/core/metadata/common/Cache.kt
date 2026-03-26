package com.kakao.actionbase.core.metadata.common

import com.kakao.actionbase.core.Constants
import com.kakao.actionbase.core.codec.XXHash32Wrapper

import com.fasterxml.jackson.annotation.JsonIgnore

data class Cache(
    val cache: String,
    val fields: List<IndexField>,
    val limit: Int = 100,
    val comment: String = Constants.DEFAULT_COMMENT,
) {
    init {
        require(limit > 0) { "Cache limit must be positive, got: $limit" }
    }

    @JsonIgnore
    val code = XXHash32Wrapper.default.stringHash(cache)
}
