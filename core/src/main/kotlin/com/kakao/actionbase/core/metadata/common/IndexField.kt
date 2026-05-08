package com.kakao.actionbase.core.metadata.common

import com.kakao.actionbase.core.java.codec.common.hbase.Order

data class IndexField(
    val field: String,
    val order: Order,
)
