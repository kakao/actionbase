package com.kakao.actionbase.server.api.graph.v2.metastore

import com.kakao.actionbase.v2.engine.metastore.JdbcMetastoreInspector
import com.kakao.actionbase.v2.engine.metastore.JdbcMetastoreRow

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Mono

/**
 * Pages the JDBC metastore's rows, tombstones included, so `jdbc-metastore remaining` can measure how
 * much room each scan window has left.
 *
 * Narrower than the endpoint #418 removed: no `prefix` (it built `k LIKE '<prefix>%'` unescaped over
 * base64url keys, whose `_` is a LIKE wildcard), no `sort`, no `/local`. The request shape still
 * matches 0.4.x so one client reads both.
 */
@RestController
class JdbcMetastoreController(
    private val metastoreInspector: JdbcMetastoreInspector,
) {
    @GetMapping("/graph/v2/metastore/global")
    fun metastoreGlobal(
        @PageableDefault(size = 100, page = 0) pageable: Pageable,
    ): Mono<Page<JdbcMetastoreRow>> =
        metastoreInspector
            .dump(pageable.pageSize, pageable.offset)
            .zipWith(metastoreInspector.count())
            .map { PageImpl(it.t1, pageable, it.t2) }
}
