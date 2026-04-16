package com.kakao.actionbase.v2.engine.label.hbase

import com.kakao.actionbase.core.edge.Edge
import com.kakao.actionbase.core.edge.mapper.EdgeRecordMapper
import com.kakao.actionbase.core.edge.record.EdgeCacheRecord
import com.kakao.actionbase.core.java.codec.common.hbase.Order
import com.kakao.actionbase.engine.binding.TableBinding
import com.kakao.actionbase.v2.core.code.CryptoUtils
import com.kakao.actionbase.v2.core.code.EdgeEncoder
import com.kakao.actionbase.v2.core.code.IdEdgeEncoder
import com.kakao.actionbase.v2.core.code.Index
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.core.types.DataType
import com.kakao.actionbase.v2.core.types.EdgeSchema
import com.kakao.actionbase.v2.core.types.Field
import com.kakao.actionbase.v2.core.types.StructType
import com.kakao.actionbase.v2.engine.GraphDefaults
import com.kakao.actionbase.v2.engine.cdc.CdcContext
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.label.AbstractLabel
import com.kakao.actionbase.v2.engine.label.LabelFactory
import com.kakao.actionbase.v2.engine.label.mixin.IndexedLabelMixin
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.Row
import com.kakao.actionbase.v2.engine.sql.ScanFilter
import com.kakao.actionbase.v2.engine.sql.StatKey
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseStorage
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTables
import com.kakao.actionbase.v2.engine.v3.V2BackedTableBinding
import com.kakao.actionbase.v2.engine.v3.V2BackedTableBinding.Companion.toV3
import com.kakao.actionbase.v2.engine.v3.V3TableDescriptor

import reactor.core.publisher.Mono

/**
 * Manages IndexedEdgeEncoder in HBase
 */
open class HBaseIndexedLabel(
    entity: LabelEntity,
    coder: EdgeEncoder<ByteArray>,
    override val indices: List<Index>,
    override val indexNameToIndex: Map<String, Index>,
    tables: Mono<HBaseTables>,
    private val edgeRecordMapper: EdgeRecordMapper,
    lockTimeout: Long,
) : HBaseHashLabel(
        entity = entity,
        coder = coder,
        tables = tables,
    ),
    IndexedLabelMixin<ByteArray> {
    val tableBinding: TableBinding =
        V2BackedTableBinding(
            descriptor = V3TableDescriptor.create(entity),
            label = this,
            mapper = edgeRecordMapper,
            lockTimeout = lockTimeout,
        )

    override val self: AbstractLabel<ByteArray> = this

    override fun finalizeEdgeMutationUnderLock(context: CdcContext): Mono<List<Any>> = mutateIndexedEdges(context)

    override fun scan(
        scanFilter: ScanFilter,
        stats: Set<StatKey>,
        idEdgeEncoder: IdEdgeEncoder,
    ): Mono<DataFrame> =
        scanIndexedEdges(
            scanFilter,
            stats,
            idEdgeEncoder,
        )

    override fun cache(
        sources: List<Any>,
        cacheName: String,
        direction: Direction,
        limit: Int,
        offset: String?,
    ): Mono<DataFrame> {
        val cache =
            entity.caches.find { it.cache == cacheName }
                ?: return Mono.error(IllegalArgumentException("Cache not found: $cacheName"))

        val order = cache.fields.first().order
        val source =
            when (direction) {
                Direction.OUT -> entity.schema.src.type.type
                Direction.IN -> entity.schema.tgt.type.type
            }

        val cacheMapper = edgeRecordMapper.cache
        val keys =
            sources.distinct().map { vertex ->
                cacheMapper.encoder.encodeKey(
                    EdgeCacheRecord.Key.of(
                        directedSource = source.cast(vertex),
                        tableCode = entity.id,
                        direction = direction.toV3(),
                        cacheCode = cache.code,
                    ),
                )
            }

        val descriptor = V3TableDescriptor.create(entity)
        val schema = buildSchema()

        val decodedOffset = offset?.let { CryptoUtils.decodeAndDecryptUrlSafe(it) }
        val (from, to) =
            if (decodedOffset == null) {
                null to null
            } else if (order == Order.DESC) {
                null to decodedOffset
            } else {
                decodedOffset to null
            }

        return hbaseGetWideRow(keys, from, to, order, limit + 1)
            .map { records ->
                val hasNext = records.size > limit
                val results = if (hasNext) records.dropLast(1) else records
                val rows =
                    results.map { record ->
                        val decoded = cacheMapper.decoder.decode(record.key, record.qualifier, record.value)
                        val edge = decoded.toEdge(descriptor.schema)
                        toRow(edge, schema)
                    }
                val nextOffset =
                    if (hasNext) {
                        results.lastOrNull()?.qualifier?.let {
                            CryptoUtils.encryptAndEncodeUrlSafe(it)
                        }
                    } else {
                        null
                    }
                DataFrame(rows, schema, offsets = listOfNotNull(nextOffset), hasNext = listOf(hasNext))
            }
    }

    private fun toRow(
        edge: Edge,
        schema: StructType,
    ): Row {
        val array = arrayOfNulls<Any?>(schema.fields.size)
        array[0] = edge.version
        array[1] = edge.source
        array[2] = edge.target
        entity.schema.fields.forEachIndexed { i, field ->
            array[3 + i] = edge.properties[field.name]
        }
        return Row(array)
    }

    private fun buildSchema(): StructType =
        StructType(
            arrayOf(
                Field(EdgeSchema.Fields.TS, DataType.LONG, false),
                Field(EdgeSchema.Fields.SRC, entity.schema.src.type.type, false),
                Field(EdgeSchema.Fields.TGT, entity.schema.tgt.type.type, false),
            ) +
                entity.schema.fields.map {
                    Field(it.name, it.type, it.isNullable)
                },
        )

    companion object : LabelFactory<HBaseIndexedLabel, HBaseStorage> {
        override fun create(
            entity: LabelEntity,
            graph: GraphDefaults,
            storage: HBaseStorage,
            block: HBaseIndexedLabel.() -> Unit,
        ): HBaseIndexedLabel {
            val tables = storage.options.getTables()
            val indices: List<Index> = entity.indices
            val indexNameToId = indices.associateBy { it.name }
            return HBaseIndexedLabel(
                entity = entity,
                coder = graph.edgeEncoderFactory.bytesKeyValueEncoder,
                indices = indices,
                indexNameToIndex = indexNameToId,
                tables = tables,
                edgeRecordMapper = graph.edgeRecordMapper,
                lockTimeout = graph.lockTimeout,
            )
        }
    }
}
