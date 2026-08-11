package com.kakao.actionbase.server.filter

/** One place, so the filters cannot disagree about what the data plane is. */
object PathPrefixes {
    val GRAPH = setOf("/graph/v2", "/graph/v3")

    val QUEUE = setOf("/queue/v1")

    val AGGREGATIONS = setOf("/aggregations/v1")

    val CONTROL = setOf("/control")

    val DATA = GRAPH + QUEUE + AGGREGATIONS

    val FILTERED = DATA + CONTROL
}
