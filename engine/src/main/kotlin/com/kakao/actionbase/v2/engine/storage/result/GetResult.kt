package com.kakao.actionbase.v2.engine.storage.result

sealed class GetResult {
    data class Found(
        val value: ByteArray,
    ) : GetResult()

    object NotFound : GetResult()
}
