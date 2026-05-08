package com.kakao.actionbase.core.metadata.common

import com.kakao.actionbase.core.java.codec.common.hbase.Order

data class CacheField(
    val field: String,
    val order: Order,
    val dimension: Set<Any>? = null,
) {
    init {
        require(dimension == null || dimension.isNotEmpty()) {
            "Cache field `$field` has an empty `dimension`; omit the key to disable filtering."
        }
    }
}
