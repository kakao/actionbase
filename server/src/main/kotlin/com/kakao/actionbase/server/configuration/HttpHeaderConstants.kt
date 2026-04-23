package com.kakao.actionbase.server.configuration

object HttpHeaderConstants {
    const val REQUEST_ID = "X-Request-ID"
    const val ACTOR_ID = "X-Actor-ID"
    const val ACTOR_ROLE = "Actor-ROLE"
    const val AB_ACTOR_ID = "X-AB-Actor-ID" // legacy support
    const val RESPONSE_META = "Response-Meta"
    const val INCLUDE_MUTATION_CONTEXT = "AB-Include-Mutation-Context"
}
