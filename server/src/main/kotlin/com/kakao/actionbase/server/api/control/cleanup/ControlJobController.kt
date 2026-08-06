package com.kakao.actionbase.server.api.control.cleanup

import com.kakao.actionbase.server.configuration.ConditionalOnControlRole
import com.kakao.actionbase.server.configuration.HttpHeaderConstants
import com.kakao.actionbase.server.control.cleanup.CleanupJobService
import com.kakao.actionbase.server.control.cleanup.JobRequest
import com.kakao.actionbase.server.control.cleanup.JobView
import com.kakao.actionbase.server.control.cluster.CallerHeaders

import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

/**
 * Cleanup work, planned. `dryRun=true` is the only mode for now and returns the steps the action
 * would take, or the precondition that stops it.
 *
 * Execution will be this same endpoint with `dryRun=false`, so that the plan an operator reviewed is
 * the plan that runs - two code paths computing the same thing is how they come to disagree.
 */
@RestController
@ConditionalOnControlRole
class ControlJobController(
    private val jobService: CleanupJobService,
) {
    @PostMapping("/control/jobs")
    fun submit(
        @RequestBody request: JobRequest,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @RequestHeader(value = HttpHeaderConstants.ACTOR_ROLE, required = false) actorRole: String?,
    ): Mono<JobView> = jobService.submit(request, CallerHeaders.forwarded(authorization, actorRole))
}
