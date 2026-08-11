package com.kakao.actionbase.v2.engine.metastore

import com.kakao.actionbase.v2.core.code.DecodedEdge
import com.kakao.actionbase.v2.core.code.KeyValue
import com.kakao.actionbase.v2.engine.GraphDefaults
import com.kakao.actionbase.v2.engine.storage.jdbc.MetadataTable

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * Reads the JDBC metastore's rows as they sit on disk, tombstones included. No read path above this
 * one shows them, and a scan key crowded with tombstones truncates silently.
 */
class JdbcMetastoreInspector(
    private val metastore: Database,
    private val table: MetadataTable,
) {
    /** Ordered by `id`, the primary key, so paging walks an index that already exists. */
    fun dump(
        limit: Int,
        offset: Long,
    ): Mono<List<JdbcMetastoreRow>> =
        Mono
            .fromCallable {
                transaction(metastore) {
                    table
                        .selectAll()
                        .orderBy(table.id, SortOrder.ASC)
                        .limit(limit, offset)
                        .map { row ->
                            JdbcMetastoreRow(
                                id = row[table.id].value,
                                k = row[table.k],
                                decoded = decode(row[table.k], row[table.v]),
                            )
                        }
                }
            }.subscribeOn(Schedulers.boundedElastic())

    fun count(): Mono<Long> =
        Mono
            .fromCallable { transaction(metastore) { table.selectAll().count() } }
            .subscribeOn(Schedulers.boundedElastic())

    // Null when the codec cannot read the row, which still fills its window and is still reported.
    // Exception rather than runCatching, which would also swallow an Error and pass a broken JVM off
    // as one more undecodable row.
    private fun decode(
        k: String,
        v: String,
    ): JdbcMetastoreEdge? =
        try {
            val edge = DecodedEdge.fromMetastore(KeyValue(k, v), emptyMap())
            JdbcMetastoreEdge(active = edge.isActive, src = edge.src, tgt = edge.tgt, labelId = edge.labelId)
        } catch (e: Exception) {
            null
        }

    companion object {
        // From the graph, not its parts: the server module cannot name Exposed's Database.
        fun of(graph: GraphDefaults): JdbcMetastoreInspector = JdbcMetastoreInspector(graph.metastore, graph.metadataTable)
    }
}

/** `v` is not carried: it is the largest column and nothing reads it. */
data class JdbcMetastoreRow(
    val id: Long,
    val k: String,
    val decoded: JdbcMetastoreEdge?,
)

/**
 * `src` and `labelId` are the scan key `DdlService.getAll` builds, so they name the window whose
 * limit is at stake; `active` says whether the row is a tombstone. Nothing else is needed. `src` and
 * `tgt` stay `Any?` because the codec types them as `Object`.
 */
data class JdbcMetastoreEdge(
    val active: Boolean,
    val src: Any?,
    val tgt: Any?,
    val labelId: Int,
)
