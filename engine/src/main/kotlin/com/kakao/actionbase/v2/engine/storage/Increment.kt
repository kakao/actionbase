package com.kakao.actionbase.v2.engine.storage

data class Increment(
    val key: ByteArray,
    val amount: Long = 1,
)
