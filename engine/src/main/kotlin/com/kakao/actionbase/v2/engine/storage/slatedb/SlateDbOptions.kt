package com.kakao.actionbase.v2.engine.storage.slatedb

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class SlateDbOptions(
    val path: String = "data",
    val url: String = "",
    val libraryPath: String = "",
)
