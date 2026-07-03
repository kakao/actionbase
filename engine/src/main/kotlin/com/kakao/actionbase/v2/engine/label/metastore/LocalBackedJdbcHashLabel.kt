package com.kakao.actionbase.v2.engine.label.metastore

import com.kakao.actionbase.engine.storage.StorageOpCollector
import com.kakao.actionbase.v2.core.code.Index
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
import com.kakao.actionbase.v2.engine.label.hbase.HBaseIndexedLabel
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.Row
import com.kakao.actionbase.v2.engine.sql.ScanFilter
import com.kakao.actionbase.v2.engine.sql.StatKey
import com.kakao.actionbase.v2.engine.storage.local.LocalStorage
import com.kakao.actionbase.v2.engine.util.getLogger

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
    private val consolidatedLabel: HBaseIndexedLabel,
) : Label {
    val log = getLogger()

    private var useLocalStore = true

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

    // HBase wins on dedup; MySQL rows appear only when HBase has no entry for that key.
    // keyOf extracts the dedup key from a row — (src,tgt) for edges, src alone for counts.
    private fun merge(
        hbase: Mono<DataFrame>,
        mysql: Mono<DataFrame>,
        keyOf: (Row) -> Any,
    ): Mono<DataFrame> =
        hbase.zipWith(mysql) { overlay, base ->
            val seen = overlay.rows.mapTo(HashSet(), keyOf)
            val mysqlOnly = base.rows.filter { keyOf(it) !in seen }
            DataFrame(overlay.rows + mysqlOnly, overlay.schema, overlay.stats, overlay.offsets, overlay.hasNext)
        }

    override fun scan(
        scanFilter: ScanFilter,
        stats: Set<StatKey>,
    ): Mono<DataFrame> {
        // DdlService.getAll() passes indexName=null; route to default prefix-scan index on HBase
        val hbaseScanFilter = if (scanFilter.indexName == null) scanFilter.copy(indexName = DEFAULT_SCAN_INDEX) else scanFilter
        val local = localLabel.scan(scanFilter, stats)
        val hbase = consolidatedLabel.scan(hbaseScanFilter, stats)
        val mysql = globalLabel.scan(scanFilter, stats)
        return local.zipWith(merge(hbase, mysql) { row -> row[entity.schema.srcIndex] to row[entity.schema.tgtIndex] }) { a, b -> a + b }
    }

    override fun getSelf(
        src: List<Any>,
        stats: Set<StatKey>,
    ): Mono<DataFrame> {
        val local = localLabel.getSelf(src, stats)
        val hbase = consolidatedLabel.getSelf(src, stats)
        val mysql = globalLabel.getSelf(src, stats)
        return local.zipWith(merge(hbase, mysql) { row -> row[entity.schema.srcIndex] to row[entity.schema.tgtIndex] }) { a, b -> a + b }
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
        return local.zipWith(merge(hbase, mysql) { row -> row[entity.schema.srcIndex] to row[entity.schema.tgtIndex] }) { a, b -> a + b }
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
        return local.zipWith(merge(hbase, mysql) { row -> row[entity.schema.srcIndex] to row[entity.schema.tgtIndex] }) { a, b -> a + b }
    }

    override fun count(
        src: Any,
        dir: Direction,
    ): Mono<DataFrame> {
        val local = localLabel.count(src, dir)
        val hbase = consolidatedLabel.count(src, dir)
        val mysql = globalLabel.count(src, dir)
        return local.zipWith(merge(hbase, mysql) { row -> row[0]!! }) { a, b -> a + b }
    }

    override fun count(
        srcSet: Set<Any>,
        dir: Direction,
    ): Mono<DataFrame> {
        val local = localLabel.count(srcSet, dir)
        val hbase = consolidatedLabel.count(srcSet, dir)
        val mysql = globalLabel.count(srcSet, dir)
        return local.zipWith(merge(hbase, mysql) { row -> row[0]!! }) { a, b -> a + b }
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
        // Prefix scan index with no fields — covers DdlService.getAll() (src-prefix only, no predicates)
        const val DEFAULT_SCAN_INDEX = "__default__"
        val defaultScanIndex = Index(DEFAULT_SCAN_INDEX, emptyList())

        override fun create(
            entity: LabelEntity,
            graph: GraphDefaults,
            storage: LocalStorage,
            block: LocalBackedJdbcHashLabel.() -> Unit,
        ): LocalBackedJdbcHashLabel {
            val coder = graph.edgeEncoderFactory.stringKeyFieldValueEncoder
            // Augment entity with a default prefix-scan index so HBaseIndexedLabel.scan() works
            // for DdlService.getAll() which issues indexName=null / no predicates scans.
            val indexedEntity = entity.copy(indices = listOf(defaultScanIndex))
            val label =
                LocalBackedJdbcHashLabel(
                    entity = entity,
                    localLabel = JdbcHashLabel(entity, coder, graph.localMetastore, graph.metadataTable),
                    globalLabel = JdbcHashLabel(entity, coder, graph.metastore, graph.metadataTable),
                    consolidatedLabel =
                        HBaseIndexedLabel(
                            entity = indexedEntity,
                            coder = graph.edgeEncoderFactory.bytesKeyValueEncoder,
                            indices = indexedEntity.indices,
                            indexNameToIndex = indexedEntity.indices.associateBy { it.name },
                            tables = graph.consolidatedMetastore,
                            edgeRecordMapper = graph.edgeRecordMapper,
                            lockTimeout = graph.lockTimeout,
                        ),
                )
            label.block()
            if (storage.options.useGlobal) {
                label.useGlobalStore()
            }
            return label
        }
    }
}
