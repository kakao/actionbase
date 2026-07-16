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
import com.kakao.actionbase.v2.engine.label.bytearray.ByteArrayIndexedLabel
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
// Op            | local (seed) | HBase (overlay) | MySQL (base) | Rule
// --------------|--------------|-----------------|--------------|------------------------------
// INSERT/UPDATE | useLocal=T   | useLocal=F      | mirrored     | HBase is write target; MySQL mirrored for rollback safety
// DELETE        | useLocal=T   | useLocal=F      | mirrored     | both layers deleted → no resurrection
// read          | always       | always          | fallback*    | HBase wins on (src,tgt) dedup; *skipped when useJdbcMetastore=false
// count         | always       | always          | never        | local + HBase; MySQL cannot count (JdbcHashLabel returns a -1 sentinel)
//
// useJdbcMetastore=false: MySQL layer is bypassed entirely (writes go to HBase only, reads skip MySQL merge)
class LocalBackedJdbcHashLabel internal constructor(
    override val entity: LabelEntity,
    private val localLabel: Label,
    private val globalLabel: JdbcHashLabel,
    private val consolidatedLabel: HBaseIndexedLabel,
) : Label {
    val log = getLogger()

    private var useLocalStore = true
    private var useJdbcMetastore = true

    fun useLocalStore() {
        useLocalStore = true
    }

    fun useGlobalStore() {
        useLocalStore = false
    }

    fun disableJdbcMetastore() {
        useJdbcMetastore = false
    }

    override fun mutate(
        edges: List<TraceEdge>,
        op: EdgeOperation,
        alias: EntityName?,
        bulk: Boolean,
        failOnExist: Boolean,
        newCollector: () -> StorageOpCollector?,
    ): Mono<List<CdcContext>> {
        if (useLocalStore) return localLabel.mutate(edges, op, alias = alias, bulk = bulk, failOnExist = failOnExist, newCollector = newCollector)
        val hbase = consolidatedLabel.mutate(edges, op, alias = alias, bulk = bulk, failOnExist = failOnExist, newCollector = newCollector)
        // MySQL mirrored for rollback safety; HBase result is authoritative
        return if (useJdbcMetastore) {
            hbase.flatMap { ctx -> globalLabel.mutate(edges, op, alias = alias, bulk = bulk, failOnExist = false, newCollector = newCollector).thenReturn(ctx) }
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

    private fun remoteEdges(
        hbase: () -> Mono<DataFrame>,
        mysql: () -> Mono<DataFrame>,
    ): Mono<DataFrame> = if (useJdbcMetastore) merge(hbase(), mysql()) { row -> row[entity.schema.srcIndex] to row[entity.schema.tgtIndex] } else hbase()

    override fun scan(
        scanFilter: ScanFilter,
        stats: Set<StatKey>,
    ): Mono<DataFrame> {
        // DdlService.getAll() passes indexName=null; both indexed backends need the default
        // prefix-scan index. MySQL (globalLabel) ignores the index name, so it keeps the original.
        val defaultScanFilter = if (scanFilter.indexName == null) scanFilter.copy(indexName = DEFAULT_SCAN_INDEX) else scanFilter
        val remote = remoteEdges({ consolidatedLabel.scan(defaultScanFilter, stats) }, { globalLabel.scan(scanFilter, stats) })
        return localLabel.scan(defaultScanFilter, stats).zipWith(remote) { a, b -> a + b }
    }

    override fun getSelf(
        src: List<Any>,
        stats: Set<StatKey>,
    ): Mono<DataFrame> {
        val remote = remoteEdges({ consolidatedLabel.getSelf(src, stats) }, { globalLabel.getSelf(src, stats) })
        return localLabel.getSelf(src, stats).zipWith(remote) { a, b -> a + b }
    }

    override fun get(
        src: Any,
        tgt: Any,
        dir: Direction,
        stats: Set<StatKey>,
    ): Mono<DataFrame> {
        val remote = remoteEdges({ consolidatedLabel.get(src, tgt, dir, stats) }, { globalLabel.get(src, tgt, dir, stats) })
        return localLabel.get(src, tgt, dir, stats).zipWith(remote) { a, b -> a + b }
    }

    override fun get(
        src: Any,
        tgt: List<Any>,
        dir: Direction,
        stats: Set<StatKey>,
    ): Mono<DataFrame> {
        val remote = remoteEdges({ consolidatedLabel.get(src, tgt, dir, stats) }, { globalLabel.get(src, tgt, dir, stats) })
        return localLabel.get(src, tgt, dir, stats).zipWith(remote) { a, b -> a + b }
    }

    // local + HBase overlay only. The global JdbcHashLabel does not support counting and would
    // otherwise merge in a -1 sentinel row per src.
    override fun count(
        srcSet: Set<Any>,
        dir: Direction,
    ): Mono<DataFrame> = localLabel.count(srcSet, dir).zipWith(consolidatedLabel.count(srcSet, dir)) { a, b -> a + b }

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
        const val DEFAULT_SCAN_INDEX = "__default__"
        val defaultScanIndex = Index(DEFAULT_SCAN_INDEX, emptyList())

        override fun create(
            entity: LabelEntity,
            graph: GraphDefaults,
            storage: LocalStorage,
            block: LocalBackedJdbcHashLabel.() -> Unit,
        ): LocalBackedJdbcHashLabel {
            val indexedEntity = entity.copy(indices = listOf(defaultScanIndex))
            val label =
                LocalBackedJdbcHashLabel(
                    entity = entity,
                    localLabel =
                        ByteArrayIndexedLabel.create(
                            entity = indexedEntity,
                            coder = graph.edgeEncoderFactory.bytesKeyValueEncoder,
                            store = graph.localStore,
                        ),
                    globalLabel =
                        JdbcHashLabel(
                            entity = entity,
                            coder = graph.edgeEncoderFactory.stringKeyFieldValueEncoder,
                            database = graph.metastore,
                            metadataTable = graph.metadataTable,
                        ),
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
            if (!graph.useJdbcMetastore) {
                label.disableJdbcMetastore()
            }
            return label
        }
    }
}
