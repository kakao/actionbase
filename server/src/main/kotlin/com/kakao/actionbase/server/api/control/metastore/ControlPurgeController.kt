package com.kakao.actionbase.server.api.control.metastore

import com.kakao.actionbase.server.configuration.ConditionalOnControlRole
import com.kakao.actionbase.server.control.metastore.MetastorePurgeService
import com.kakao.actionbase.server.control.metastore.PurgeQuery
import com.kakao.actionbase.server.control.metastore.PurgeResult
import com.kakao.actionbase.server.control.metastore.PurgeSet

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

/**
 * Purging tombstoned rows from a metastore table, in three steps an operator drives.
 *
 * `candidates` hands back what it would delete, contents and all. `execute` deletes exactly those
 * rows. `restore` puts them back. All three exchange the same document, so committing a purge is
 * the operator keeping the file and reverting one is posting it to `restore`.
 *
 * This is temporary. It exists because a metadata `DELETE` only tombstones and nothing removes the
 * rows, and it goes away with the JDBC metastore.
 */
@RestController
@ConditionalOnControlRole
class ControlPurgeController(
    private val purgeService: MetastorePurgeService,
) {
    @PostMapping("/control/metastore/purge/candidates")
    fun candidates(
        @RequestBody query: PurgeQuery,
    ): Mono<PurgeSet> = purgeService.candidates(query)

    @PostMapping("/control/metastore/purge/execute")
    fun execute(
        @RequestBody set: PurgeSet,
    ): Mono<PurgeResult> = purgeService.execute(set)

    @PostMapping("/control/metastore/purge/restore")
    fun restore(
        @RequestBody set: PurgeSet,
    ): Mono<PurgeResult> = purgeService.restore(set)
}
