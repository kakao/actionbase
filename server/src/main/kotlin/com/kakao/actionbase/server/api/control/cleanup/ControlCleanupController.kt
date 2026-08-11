package com.kakao.actionbase.server.api.control.cleanup

import com.kakao.actionbase.server.configuration.ConditionalOnControlRole
import com.kakao.actionbase.server.configuration.HttpHeaderConstants
import com.kakao.actionbase.server.control.cleanup.CleanupService
import com.kakao.actionbase.server.control.cleanup.HtablesView
import com.kakao.actionbase.server.control.cluster.CallerHeaders
import com.kakao.actionbase.server.control.topology.Env

import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

/**
 * What is left to clean up across the fleet. One request answers for every configured tenant, both
 * sides, with the bindings that still hold each table.
 */
@RestController
@ConditionalOnControlRole
class ControlCleanupController(
    private val cleanupService: CleanupService,
) {
    @GetMapping("/control/htables")
    fun htables(
        @RequestParam(required = false) tenant: String?,
        @RequestParam(required = false) env: String?,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @RequestHeader(value = HttpHeaderConstants.ACTOR_ROLE, required = false) actorRole: String?,
    ): Mono<HtablesView> = cleanupService.htables(tenant, env?.let(Env::of), CallerHeaders.forwarded(authorization, actorRole))
}
