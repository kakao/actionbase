package com.kakao.actionbase.server.control.cluster

import com.kakao.actionbase.server.configuration.HttpHeaderConstants

import org.springframework.http.HttpHeaders

/** The cluster should see who is asking, not who is relaying. */
object CallerHeaders {
    fun forwarded(
        authorization: String?,
        actorRole: String?,
    ): Map<String, String> =
        buildMap {
            authorization?.let { put(HttpHeaders.AUTHORIZATION, it) }
            actorRole?.let { put(HttpHeaderConstants.ACTOR_ROLE, it) }
        }
}
