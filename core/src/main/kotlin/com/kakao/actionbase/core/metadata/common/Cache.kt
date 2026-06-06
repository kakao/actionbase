package com.kakao.actionbase.core.metadata.common

import com.kakao.actionbase.core.Constants
import com.kakao.actionbase.core.codec.XXHash32Wrapper

import com.fasterxml.jackson.annotation.JsonIgnore

data class Cache(
    val cache: String,
    val fields: List<CacheField>,
    val limit: Int = 100,
    val tolerance: Int = limit * 2,
    val comment: String = Constants.DEFAULT_COMMENT,
) {
    init {
        require(limit > 0) { "Cache limit must be positive, got: $limit" }
        require(tolerance > 0) { "Cache tolerance must be positive, got: $tolerance" }
    }

    @JsonIgnore
    val code = XXHash32Wrapper.default.stringHash(cache)
}
