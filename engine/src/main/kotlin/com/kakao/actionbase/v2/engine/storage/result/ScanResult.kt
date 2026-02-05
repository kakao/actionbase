package com.kakao.actionbase.v2.engine.storage.result

sealed class ScanResult {
    data class Data(
        val key: ByteArray,
        val value: ByteArray,
    ) : ScanResult()
}
