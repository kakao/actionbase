package com.kakao.actionbase.v2.engine.label.metastore

import com.kakao.actionbase.engine.storage.StorageOpCollector
import com.kakao.actionbase.v2.core.code.EdgeEncoder
import com.kakao.actionbase.v2.core.code.KeyValue
import com.kakao.actionbase.v2.core.edge.TraceEdge
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.engine.GraphDefaults
import com.kakao.actionbase.v2.engine.cdc.CdcContext
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.label.Label
import com.kakao.actionbase.v2.engine.label.LabelFactory
import com.kakao.actionbase.v2.engine.label.hbase.HBaseHashLabel
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.ScanFilter
import com.kakao.actionbase.v2.engine.sql.StatKey
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTables
import com.kakao.actionbase.v2.engine.storage.jdbc.MetadataTable
import com.kakao.actionbase.v2.engine.storage.local.LocalStorage
import com.kakao.actionbase.v2.engine.util.getLogger

import org.jetbrains.exposed.sql.Database

import reactor.core.publisher.Mono

// Merge policy (Phase 1 overlay):
//
// Op            | local (H2) | HBase (overlay) | MySQL (base) | Rule
// --------------|------------|-----------------|--------------|------------------------------
// INSERT/UPDATE | useLocal=T | useLocal=F      | never        | HBase is write target
// DELETE        | useLocal=T | useLocal=F      | mirrored     | both layers deleted → no resurrection
// read          | always     | always          | fallback     | HBase wins on (src,tgt) dedup
// count         | always     | always          | fallback     | HBase wins on src dedup
class LocalBackedJdbcHashLabel internal constructor(
    override val entity: LabelEntity,
    private val localLabel: JdbcHashLabel,
    private val globalLabel: JdbcHashLabel,
    private val consolidatedLabel: HBaseHashLabel,
) : Label {
    val log = getLogger()

    private var useLocalStore = true

    constructor(
        entity: LabelEntity,
        coder: EdgeEncoder<String>,
        localStore: Database,
        globalStore: Database,
        metadataTable: MetadataTable,
        bytesCoder: EdgeEncoder<ByteArray>,
        consolidatedMetastore: Mono<HBaseTables>,
    ) : this(
        entity = entity,
        localLabel =
            JdbcHashLabel(
                entity = entity,
                coder = coder,
                database = localStore,
                metadataTable = metadataTable,
            ),
        globalLabel =
            JdbcHashLabel(
                entity = entity,
                coder = coder,
                database = globalStore,
                metadataTable = metadataTable,
            ),
        consolidatedLabel =
            HBaseHashLabel(
                entity = entity,
                coder = bytesCoder,
                tables = consolidatedMetastore,
            ),
    )

    fun useLocalStore() {
        useLocalStore = true
    }

    fun useGlobalStore() {
        useLocalStore = false
    }

    override fun mutate(
        edges: List<TraceEdge>,
        op: EdgeOperation,
        alias: EntityName?,
        bulk: Boolean,
        failOnExist: Boolean,
        newCollector: () -> StorageOpCollector?,
    ): Mono<List<CdcContext>> =
        if (useLocalStore) {
            localLabel.mutate(edges, op, alias = alias, bulk = bulk, failOnExist = failOnExist, newCollector = newCollector)
        } else {
            val hbase = consolidatedLabel.mutate(edges, op, alias = alias, bulk = bulk, failOnExist = failOnExist, newCollector = newCollector)
            // DELETE is mirrored to MySQL so deleted rows don't resurface on read (no tombstone needed)
            if (op == EdgeOperation.DELETE || op == EdgeOperation.PURGE) {
                hbase.flatMap { globalLabel.mutate(edges, op, alias = alias, bulk = bulk, failOnExist = false, newCollector = newCollector).thenReturn(it) }
            } else {
                hbase
            }
        }

    // overlay merge: HBase wins on (src, tgt) dedup; MySQL rows appear only when HBase has no entry
    private fun mergeOverlay(
        hbase: Mono<DataFrame>,
        mysql: Mono<DataFrame>,
    ): Mono<DataFrame> {
        val srcIdx = entity.schema.srcIndex
        val tgtIdx = entity.schema.tgtIndex
        return hbase.zipWith(mysql) { overlay, base ->
            val seenKeys = overlay.rows.map { row -> row[srcIdx] to row[tgtIdx] }.toHashSet()
            val mysqlOnly = base.rows.filter { row -> (row[srcIdx] to row[tgtIdx]) !in seenKeys }
            DataFrame(overlay.rows + mysqlOnly, overlay.schema, overlay.stats, overlay.offsets, overlay.hasNext)
        }
    }

    // count row structure: (src=0, COUNT(1)=1, dir=2) — dedup on src only
    private fun mergeCountOverlay(
        hbase: Mono<DataFrame>,
        mysql: Mono<DataFrame>,
    ): Mono<DataFrame> =
        hbase.zipWith(mysql) { overlay, base ->
            val seenSrcs = overlay.rows.map { row -> row[0] }.toHashSet()
            val mysqlOnly = base.rows.filter { row -> row[0] !in seenSrcs }
            DataFrame(overlay.rows + mysqlOnly, overlay.schema)
        }

    override fun scan(
        scanFilter: ScanFilter,
        stats: Set<StatKey>,
    ): Mono<DataFrame> {
        val local = localLabel.scan(scanFilter, stats)
        val hbase = consolidatedLabel.scan(scanFilter, stats)
        val mysql = globalLabel.scan(scanFilter, stats)
        return local.zipWith(mergeOverlay(hbase, mysql)) { a, b -> a + b }
    }

    override fun getSelf(
        src: List<Any>,
        stats: Set<StatKey>,
    ): Mono<DataFrame> {
        val local = localLabel.getSelf(src, stats)
        val hbase = consolidatedLabel.getSelf(src, stats)
        val mysql = globalLabel.getSelf(src, stats)
        return local.zipWith(mergeOverlay(hbase, mysql)) { a, b -> a + b }
    }

    override fun get(
        src: Any,
        tgt: Any,
        dir: Direction,
        stats: Set<StatKey>,
    ): Mono<DataFrame> {
        val local = localLabel.get(src, tgt, dir, stats)
        val hbase = consolidatedLabel.get(src, tgt, dir, stats)
        val mysql = globalLabel.get(src, tgt, dir, stats)
        return local.zipWith(mergeOverlay(hbase, mysql)) { a, b -> a + b }
    }

    override fun get(
        src: Any,
        tgt: List<Any>,
        dir: Direction,
        stats: Set<StatKey>,
    ): Mono<DataFrame> {
        val local = localLabel.get(src, tgt, dir, stats)
        val hbase = consolidatedLabel.get(src, tgt, dir, stats)
        val mysql = globalLabel.get(src, tgt, dir, stats)
        return local.zipWith(mergeOverlay(hbase, mysql)) { a, b -> a + b }
    }

    override fun count(
        src: Any,
        dir: Direction,
    ): Mono<DataFrame> {
        val local = localLabel.count(src, dir)
        val hbase = consolidatedLabel.count(src, dir)
        val mysql = globalLabel.count(src, dir)
        return local.zipWith(mergeCountOverlay(hbase, mysql)) { a, b -> a + b }
    }

    override fun count(
        srcSet: Set<Any>,
        dir: Direction,
    ): Mono<DataFrame> {
        val local = localLabel.count(srcSet, dir)
        val hbase = consolidatedLabel.count(srcSet, dir)
        val mysql = globalLabel.count(srcSet, dir)
        return local.zipWith(mergeCountOverlay(hbase, mysql)) { a, b -> a + b }
    }

    override fun findStaleLockAndClear(
        lockEdge: KeyValue<Any>,
        lockTimeout: Long,
    ): Mono<Void> =
        if (useLocalStore) {
            localLabel.findStaleLockAndClear(lockEdge, lockTimeout)
        } else {
            consolidatedLabel.findStaleLockAndClear(lockEdge, lockTimeout)
        }

    override fun close() {
        localLabel.close()
        globalLabel.close()
    }

    companion object : LabelFactory<LocalBackedJdbcHashLabel, LocalStorage> {
        override fun create(
            entity: LabelEntity,
            graph: GraphDefaults,
            storage: LocalStorage,
            block: LocalBackedJdbcHashLabel.() -> Unit,
        ): LocalBackedJdbcHashLabel {
            val label =
                LocalBackedJdbcHashLabel(
                    entity = entity,
                    coder = graph.edgeEncoderFactory.stringKeyFieldValueEncoder,
                    localStore = graph.localMetastore,
                    globalStore = graph.metastore,
                    bytesCoder = graph.edgeEncoderFactory.bytesKeyValueEncoder,
                    metadataTable = graph.metadataTable,
                    consolidatedMetastore = graph.consolidatedMetastore,
                )
            label.block()
            if (storage.options.useGlobal) {
                label.useGlobalStore()
            }
            return label
        }
    }
}
