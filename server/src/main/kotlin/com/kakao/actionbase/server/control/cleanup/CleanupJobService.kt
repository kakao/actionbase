package com.kakao.actionbase.server.control.cleanup

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Plans a batch of cleanup work.
 *
 * Targets arrive as a list on purpose. A per-table endpoint is what makes every client build its own
 * queue, and then build it again for the next client.
 *
 * Only dry runs are answered for now: execution waits for authorization, so that a cross-cluster
 * drop never becomes reachable before there is something checking who asked for it.
 */
class CleanupJobService(
    private val cleanupService: CleanupService,
) {
    fun submit(
        request: JobRequest,
        headers: Map<String, String>,
    ): Mono<JobView> {
        val action = CleanupAction.of(request.action)
        require(request.targets.isNotEmpty()) { "targets is empty: name at least one table to plan for" }
        if (!request.dryRun) {
            throw UnsupportedOperationException(
                "execution is not available yet, only dryRun=true. The plan this returns is what execution will run.",
            )
        }

        return Flux
            .fromIterable(request.targets.map { it.tenant }.distinct())
            .flatMapSequential { cleanupService.htables(it, null, headers) }
            .collectList()
            .map { views -> view(action, request, views) }
    }

    private fun view(
        action: CleanupAction,
        request: JobRequest,
        views: List<HtablesView>,
    ): JobView {
        val tables = views.flatMap { it.tables }.associateBy { it.tenant to it.name }

        return JobView(
            dryRun = true,
            action = action,
            fanout = request.fanout,
            plans =
                request.targets.distinct().map { target ->
                    tables[target.tenant to target.table]
                        ?.let { CleanupPlanner.plan(action, it, request.fanout) }
                        ?: unseen(target, action)
                },
            failures = views.flatMap { it.failures },
        )
    }

    /**
     * A table nobody reported. It may be gone, or its cluster may be the one that did not answer -
     * which is why the failures travel in the same response.
     */
    private fun unseen(
        target: JobTarget,
        action: CleanupAction,
    ) = CleanupPlan(
        target = target,
        action = action,
        ok = false,
        refusal = Precondition.TABLE_ALREADY_GONE,
        detail = "not seen on any side, and nothing binds it - check failures before concluding it is gone",
    )
}
