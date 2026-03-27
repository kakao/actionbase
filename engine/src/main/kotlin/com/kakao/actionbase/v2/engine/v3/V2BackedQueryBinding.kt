package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.engine.query.QueryBinding
import com.kakao.actionbase.engine.query.QueryScanFilter
import com.kakao.actionbase.v2.core.code.EmptyEdgeIdEncoder
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.ScanFilter
import com.kakao.actionbase.v2.engine.sql.StatKey
import reactor.core.publisher.Mono

/**
 * [QueryBinding] backed by V2 [Graph].
 *
 * Encapsulates V2 types ([EntityName], [EmptyEdgeIdEncoder], [ScanFilter])
 * so that [com.kakao.actionbase.engine.query.ActionbaseQueryExecutor] stays
 * free of direct V2 storage dependencies.
 */
class V2BackedQueryBinding(
    private val graph: Graph,
) : QueryBinding {
    override fun getSelf(database: String, table: String, src: List<Any>, stats: Set<StatKey>): Mono<DataFrame> {
        val label = graph.getLabel(EntityName(database, table))
        return label.getSelf(src, stats, EmptyEdgeIdEncoder.INSTANCE)
    }

    override fun get(database: String, table: String, src: List<Any>, tgt: List<Any>, stats: Set<StatKey>): Mono<DataFrame> {
        val label = graph.getLabel(EntityName(database, table))
        return label.get(src, tgt, stats, EmptyEdgeIdEncoder.INSTANCE)
    }

    override fun count(database: String, table: String, src: Set<Any>, direction: Direction): Mono<DataFrame> {
        val label = graph.getLabel(EntityName(database, table))
        return label.count(src, direction)
    }

    override fun scan(database: String, table: String, filter: QueryScanFilter, stats: Set<StatKey>): Mono<DataFrame> {
        val scanFilter = ScanFilter(
            name = EntityName(database, table),
            srcSet = filter.srcSet,
            dir = filter.direction,
            limit = filter.limit,
            offset = filter.offset,
            indexName = filter.indexName,
            otherPredicates = filter.predicates,
        )
        val label = graph.getLabel(EntityName(database, table))
        return label.scan(scanFilter, stats, EmptyEdgeIdEncoder.INSTANCE)
    }
}
