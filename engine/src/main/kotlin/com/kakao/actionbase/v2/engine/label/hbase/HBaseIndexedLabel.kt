package com.kakao.actionbase.v2.engine.label.hbase

import com.kakao.actionbase.core.edge.Edge
import com.kakao.actionbase.core.edge.mapper.EdgeRecordMapper
import com.kakao.actionbase.core.edge.mapper.QualifierStartStop
import com.kakao.actionbase.core.edge.mapper.QualifierStartStopItem
import com.kakao.actionbase.core.edge.record.EdgeCacheRecord
import com.kakao.actionbase.core.java.codec.common.hbase.Order
import com.kakao.actionbase.core.metadata.common.IndexField
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
import com.kakao.actionbase.v2.engine.sql.WherePredicate
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
        predicates: List<WherePredicate>,
    ): Mono<DataFrame> {
        val cache =
            entity.caches.find { it.cache == cacheName }
                ?: return Mono.error(IllegalArgumentException("Cache not found: $cacheName"))

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

        val (from, to) = encodeCacheRange(fields = cache.fields.take(predicates.size), predicates, offset)

        return hbaseGetWideRow(keys, from, to, limit + 1)
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

    /**
     * Build the `(from, to)` qualifier byte range.
     *
     * Qualifier bytes already carry each field's [Order] (DESC fields are bit-inverted
     * by `OrderedBytes` at write time), so the resulting range is consumed by a single
     * forward `ColumnRangeFilter` that honours the declared compound order.
     *
     * When [offset] is supplied it replaces the predicate-derived `from`: a paginated
     * cursor always lies inside the predicate range produced by the previous page.
     *
     * @param fields  the leading-prefix subset of the cache schema covered by [predicates].
     * @param offset  encrypted qualifier from the previous page, or `null` for the first page.
     * @return `(from, to)` where either side may be `null` when no bound applies.
     */
    private fun encodeCacheRange(
        fields: List<IndexField>,
        predicates: List<WherePredicate>,
        offset: String?,
    ): Pair<ByteArray?, ByteArray?> {
        val (predicateStart, predicateStop) =
            if (fields.isEmpty()) {
                null to null
            } else {
                val startStops =
                    fields.mapIndexed { idx, field ->
                        toQualifierStartStop(field, predicates[idx])
                    }
                val encoder = edgeRecordMapper.cache.encoder
                val start =
                    if (startStops.any { it.start != null }) {
                        encoder.encodeQualifierPrefixStart(startStops)
                    } else {
                        null
                    }
                val stop =
                    if (startStops.any { it.stop != null }) {
                        encoder.encodeQualifierPrefixStop(startStops)
                    } else {
                        null
                    }
                start to stop
            }

        val decodedOffset = offset?.let(CryptoUtils::decodeAndDecryptUrlSafe)
        return (decodedOffset ?: predicateStart) to predicateStop
    }

    /**
     * Lift one [WherePredicate] against one cache [field] into its qualifier-prefix
     * `start`/`stop` contribution for an HBase `ColumnRangeFilter`.
     *
     * Because qualifier bytes for DESC fields are bit-inverted by `OrderedBytes` at
     * write time, an operator's start/stop side flips relative to ASC: e.g. `<` on a
     * DESC field contributes to `start`, not `stop`.
     */
    private fun toQualifierStartStop(
        field: IndexField,
        predicate: WherePredicate,
    ): QualifierStartStop =
        when (predicate) {
            is WherePredicate.Eq -> {
                val item = QualifierStartStopItem(predicate.value, field.order, true)
                QualifierStartStop(item, item)
            }

            is WherePredicate.Lt -> {
                val item = QualifierStartStopItem(predicate.value, field.order, false)
                if (field.order == Order.ASC) {
                    QualifierStartStop(null, item)
                } else {
                    QualifierStartStop(item, null)
                }
            }

            is WherePredicate.Lte -> {
                val item = QualifierStartStopItem(predicate.value, field.order, true)
                if (field.order == Order.ASC) {
                    QualifierStartStop(null, item)
                } else {
                    QualifierStartStop(item, null)
                }
            }

            is WherePredicate.Gt -> {
                val item = QualifierStartStopItem(predicate.value, field.order, false)
                if (field.order == Order.ASC) {
                    QualifierStartStop(item, null)
                } else {
                    QualifierStartStop(null, item)
                }
            }

            is WherePredicate.Gte -> {
                val item = QualifierStartStopItem(predicate.value, field.order, true)
                if (field.order == Order.ASC) {
                    QualifierStartStop(item, null)
                } else {
                    QualifierStartStop(null, item)
                }
            }

            is WherePredicate.Between -> {
                val (low, high) =
                    if (field.order == Order.ASC) predicate.from to predicate.to else predicate.to to predicate.from
                QualifierStartStop(
                    QualifierStartStopItem(low, field.order, true),
                    QualifierStartStopItem(high, field.order, true),
                )
            }

            is WherePredicate.In ->
                error("Cache `${entity.fullName}` does not support `IN` predicate on field `${field.field}`")
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
