package com.kakao.actionbase.server.filter

/** One place, so two filters cannot disagree about what the data plane is. */
object PathPrefixes {
    val DATA = setOf("/graph/v2", "/graph/v3", "/queue/v1")

    val CONTROL = setOf("/control")

    val FILTERED = DATA + CONTROL
}
