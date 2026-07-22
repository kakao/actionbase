package com.kakao.actionbase.server.api.graph

import com.kakao.actionbase.engine.context.RequestContext
import com.kakao.actionbase.v2.engine.Graph

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class GraphController(
    val graph: Graph,
) {
    @GetMapping("/graph")
    fun get(requestContext: RequestContext): Map<String, Any> =
        mapOf(
            "v2" to "/graph/v2",
            "v3" to "/graph/v3",
            "request" to requestContext,
        )

    @GetMapping("/graph/v3")
    fun getV3(): Map<String, String> = mapOf("engine" to "v3")

    @GetMapping("/graph/v2")
    fun getV2(): Map<String, String> = mapOf("engine" to graph.javaClass.simpleName)
}
