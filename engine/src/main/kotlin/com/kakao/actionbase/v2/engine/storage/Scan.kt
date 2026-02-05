package com.kakao.actionbase.v2.engine.storage

data class Scan(
    val prefix: ByteArray,
    val limit: Int = 100,
)
