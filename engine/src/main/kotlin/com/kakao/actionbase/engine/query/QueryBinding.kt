package com.kakao.actionbase.engine.query

import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.StatKey

import reactor.core.publisher.Mono

/**
 * Abstraction for query operations on a database table.
 *
 * Decouples [ActionbaseQueryExecutor] from V2 storage types
 * (Label, EntityName, EmptyEdgeIdEncoder).
 */
interface QueryBinding {
    fun getSelf(
        database: String,
        table: String,
        src: List<Any>,
        stats: Set<StatKey>,
    ): Mono<DataFrame>

    fun get(
        database: String,
        table: String,
        src: List<Any>,
        tgt: List<Any>,
        stats: Set<StatKey>,
    ): Mono<DataFrame>

    fun count(
        database: String,
        table: String,
        src: Set<Any>,
        direction: Direction,
    ): Mono<DataFrame>

    fun scan(
        database: String,
        table: String,
        filter: QueryScanFilter,
        stats: Set<StatKey>,
    ): Mono<DataFrame>
}
