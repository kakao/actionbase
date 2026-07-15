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
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.ScanFilter
import com.kakao.actionbase.v2.engine.sql.StatKey
import com.kakao.actionbase.v2.engine.storage.local.LocalStorage
import com.kakao.actionbase.v2.engine.util.getLogger

import reactor.core.publisher.Mono

class LocalBackedJdbcHashLabel(
    override val entity: LabelEntity,
    private val localLabel: Label,
    private val globalLabel: JdbcHashLabel,
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
            globalLabel.mutate(edges, op, alias = alias, bulk = bulk, failOnExist = failOnExist, newCollector = newCollector)
        }

    override fun scan(
        scanFilter: ScanFilter,
        stats: Set<StatKey>,
    ): Mono<DataFrame> {
        // DdlService.getAll() passes indexName=null; the indexed local store needs an explicit
        // prefix-scan index, so route null to the synthetic __default__ index for the local side.
        val localScanFilter = if (scanFilter.indexName == null) scanFilter.copy(indexName = DEFAULT_SCAN_INDEX) else scanFilter
        val local = localLabel.scan(localScanFilter, stats)
        val global = globalLabel.scan(scanFilter, stats)
        return local.zipWith(global) { a, b ->
            a + b
        }
    }

    override fun getSelf(
        src: List<Any>,
        stats: Set<StatKey>,
    ): Mono<DataFrame> {
        val local = localLabel.getSelf(src, stats)
        val global = globalLabel.getSelf(src, stats)
        return local.zipWith(global) { a, b ->
            a + b
        }
    }

    override fun get(
        src: Any,
        tgt: Any,
        dir: Direction,
        stats: Set<StatKey>,
    ): Mono<DataFrame> {
        val local = localLabel.get(src, tgt, dir, stats)
        val global = globalLabel.get(src, tgt, dir, stats)
        return local.zipWith(global) { a, b ->
            a + b
        }
    }

    override fun get(
        src: Any,
        tgt: List<Any>,
        dir: Direction,
        stats: Set<StatKey>,
    ): Mono<DataFrame> {
        val local = localLabel.get(src, tgt, dir, stats)
        val global = globalLabel.get(src, tgt, dir, stats)
        return local.zipWith(global) { a, b ->
            a + b
        }
    }

    override fun count(
        src: Any,
        dir: Direction,
    ): Mono<DataFrame> {
        val local = localLabel.count(src, dir)
        val global = globalLabel.count(src, dir)
        return local.zipWith(global) { a, b ->
            a + b
        }
    }

    override fun count(
        srcSet: Set<Any>,
        dir: Direction,
    ): Mono<DataFrame> {
        val local = localLabel.count(srcSet, dir)
        val global = globalLabel.count(srcSet, dir)
        return local.zipWith(global) { a, b ->
            a + b
        }
    }

    override fun findStaleLockAndClear(
        lockEdge: KeyValue<Any>,
        lockTimeout: Long,
    ): Mono<Void> =
        if (useLocalStore) {
            localLabel.findStaleLockAndClear(lockEdge, lockTimeout)
        } else {
            globalLabel.findStaleLockAndClear(lockEdge, lockTimeout)
        }

    override fun close() {
        localLabel.close()
        globalLabel.close()
    }

    companion object : LabelFactory<LocalBackedJdbcHashLabel, LocalStorage> {
        // Prefix-scan index with no fields — covers DdlService.getAll() (src-prefix only, no predicates).
        const val DEFAULT_SCAN_INDEX = "__default__"
        val defaultScanIndex = Index(DEFAULT_SCAN_INDEX, emptyList())

        override fun create(
            entity: LabelEntity,
            graph: GraphDefaults,
            storage: LocalStorage,
            block: LocalBackedJdbcHashLabel.() -> Unit,
        ): LocalBackedJdbcHashLabel {
            // Augment the entity with a default prefix-scan index so the indexed local store's
            // scan() works for DdlService.getAll(), which issues indexName=null / no-predicate scans.
            val localEntity = entity.copy(indices = listOf(defaultScanIndex))
            val label =
                LocalBackedJdbcHashLabel(
                    entity = entity,
                    localLabel =
                        ByteArrayIndexedLabel.create(
                            entity = localEntity,
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
                )
            label.block()
            if (storage.options.useGlobal) {
                label.useGlobalStore()
            }
            return label
        }
    }
}
