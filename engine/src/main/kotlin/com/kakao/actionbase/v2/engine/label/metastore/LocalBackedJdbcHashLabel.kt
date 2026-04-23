package com.kakao.actionbase.v2.engine.label.metastore

import com.kakao.actionbase.engine.storage.StorageOpCollector
import com.kakao.actionbase.v2.core.code.EdgeEncoder
import com.kakao.actionbase.v2.core.code.IdEdgeEncoder
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
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.ScanFilter
import com.kakao.actionbase.v2.engine.sql.StatKey
import com.kakao.actionbase.v2.engine.storage.jdbc.MetadataTable
import com.kakao.actionbase.v2.engine.storage.local.LocalStorage
import com.kakao.actionbase.v2.engine.util.getLogger

import org.jetbrains.exposed.sql.Database

import reactor.core.publisher.Mono

class LocalBackedJdbcHashLabel(
    override val entity: LabelEntity,
    coder: EdgeEncoder<String>,
    localStore: Database,
    globalStore: Database,
    metadataTable: MetadataTable,
) : Label {
    val log = getLogger()

    private var useLocalStore = true

    private val localLabel =
        JdbcHashLabel(
            entity = entity,
            coder = coder,
            database = localStore,
            metadataTable = metadataTable,
        )

    private val globalLabel =
        JdbcHashLabel(
            entity = entity,
            coder = coder,
            database = globalStore,
            metadataTable = metadataTable,
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
            globalLabel.mutate(edges, op, alias = alias, bulk = bulk, failOnExist = failOnExist, newCollector = newCollector)
        }

    override fun scan(
        scanFilter: ScanFilter,
        stats: Set<StatKey>,
        idEdgeEncoder: IdEdgeEncoder,
    ): Mono<DataFrame> {
        val local = localLabel.scan(scanFilter, stats, idEdgeEncoder)
        val global = globalLabel.scan(scanFilter, stats, idEdgeEncoder)
        return local.zipWith(global) { a, b ->
            a + b
        }
    }

    override fun getSelf(
        src: List<Any>,
        stats: Set<StatKey>,
        idEdgeEncoder: IdEdgeEncoder,
    ): Mono<DataFrame> {
        val local = localLabel.getSelf(src, stats, idEdgeEncoder)
        val global = globalLabel.getSelf(src, stats, idEdgeEncoder)
        return local.zipWith(global) { a, b ->
            a + b
        }
    }

    override fun get(
        src: Any,
        tgt: Any,
        dir: Direction,
        stats: Set<StatKey>,
        idEdgeEncoder: IdEdgeEncoder,
    ): Mono<DataFrame> {
        val local = localLabel.get(src, tgt, dir, stats, idEdgeEncoder)
        val global = globalLabel.get(src, tgt, dir, stats, idEdgeEncoder)
        return local.zipWith(global) { a, b ->
            a + b
        }
    }

    override fun get(
        src: Any,
        tgt: List<Any>,
        dir: Direction,
        stats: Set<StatKey>,
        idEdgeEncoder: IdEdgeEncoder,
    ): Mono<DataFrame> {
        val local = localLabel.get(src, tgt, dir, stats, idEdgeEncoder)
        val global = globalLabel.get(src, tgt, dir, stats, idEdgeEncoder)
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
                    metadataTable = graph.metadataTable,
                )
            label.block()
            if (storage.options.useGlobal) {
                label.useGlobalStore()
            }
            return label
        }
    }
}
