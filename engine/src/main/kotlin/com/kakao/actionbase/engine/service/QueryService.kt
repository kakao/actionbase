package com.kakao.actionbase.engine.service

import com.kakao.actionbase.core.Constants
import com.kakao.actionbase.core.codec.ByteArrayBufferPool
import com.kakao.actionbase.core.edge.mapper.EdgeCacheRecordMapper
import com.kakao.actionbase.core.edge.mapper.EdgeGroupRecordMapper
import com.kakao.actionbase.core.edge.payload.DataFrameEdgeAggPayload
import com.kakao.actionbase.core.edge.payload.DataFrameEdgeCountPayload
import com.kakao.actionbase.core.edge.payload.DataFrameEdgePayload
import com.kakao.actionbase.core.edge.payload.EdgeAggPayload
import com.kakao.actionbase.core.edge.payload.EdgeCountPayload
import com.kakao.actionbase.core.edge.payload.EdgePayload
import com.kakao.actionbase.core.edge.record.EdgeCacheRecord
import com.kakao.actionbase.core.edge.record.EdgeGroupRecord
import com.kakao.actionbase.core.java.codec.common.hbase.Order
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.storage.HBaseRecord
import com.kakao.actionbase.engine.QueryEngine
import com.kakao.actionbase.engine.query.ActionbaseQuery
import com.kakao.actionbase.v2.core.code.CryptoUtils
import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.label.hbase.HBaseHashLabel
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.ScanFilter
import com.kakao.actionbase.v2.engine.sql.WherePredicate
import com.kakao.actionbase.v2.engine.sql.toJsonFormat

import org.apache.hadoop.hbase.client.Get

import reactor.core.publisher.Mono

class QueryService(
    private val engine: QueryEngine,
) {
    private val byteArrayBufferPool = ByteArrayBufferPool.create(engine.encoderPoolSize, Constants.Codec.DEFAULT_BUFFER_SIZE)

    private val groupRecordMapper = EdgeGroupRecordMapper.create(byteArrayBufferPool)
    private val cacheRecordMapper = EdgeCacheRecordMapper.create(byteArrayBufferPool)

    @Suppress("UnusedParameter")
    fun count(
        database: String,
        table: String,
        start: Any,
        direction: Direction,
        ranges: String? = null,
        filters: String? = null,
        features: List<String> = emptyList(),
    ): Mono<EdgeCountPayload> =
        counts(database, table, listOf(start), direction, ranges, filters, features)
            .map {
                if (it.count == 0) {
                    empty(direction)
                } else {
                    it.counts.first()
                }
            }

    @Suppress("UnusedParameter")
    fun counts(
        database: String,
        table: String,
        start: List<Any>,
        direction: Direction,
        ranges: String? = null,
        filters: String? = null,
        features: List<String> = emptyList(),
    ): Mono<DataFrameEdgeCountPayload> {
        val name = EntityName(database, table)
        val label = engine.getLabel(name)

        require(ranges == null) { "`ranges` is not yet supported in count query." }
        require(filters == null) { "`filters` is not yet supported in count query." }
        require(features.isEmpty()) { "`features` ${features.joinToString(", ")} are not supported in get query." }

        return label
            .count(start.toSet(), direction)
            .map {
                val jsonFormat = it.toJsonFormat()
                DataFrameEdgeCountPayload(
                    counts =
                        jsonFormat.data.map { row ->
                            EdgeCountPayload(
                                start = row[SRC_FIELD] as Any,
                                direction = direction.toV3(),
                                count = row[SELECT_COUNT_FIELD] as Long,
                                context = emptyMap(),
                            )
                        },
                    count = jsonFormat.rows,
                    context = emptyMap(),
                )
            }.switchIfEmpty(emptyDataFrameCountPayload)
    }

    @Suppress("UnusedParameter")
    fun gets(
        database: String,
        table: String,
        source: List<Any>,
        target: List<Any>,
        ranges: String? = null,
        filters: String? = null,
        features: List<String> = emptyList(),
    ): Mono<DataFrameEdgePayload> {
        require(ranges == null) { "`ranges` is not supported in get query." }

        val keys =
            source.distinct().flatMap { s ->
                target.distinct().map { t -> s to t }
            }

        return getsByKeys(database, table, keys, filters, features)
    }

    /**
     * Overloaded gets for multi-edge tables using ids.
     * Multi-edge stores edge state with source=id, target=id, so this method
     * provides an optimized lookup by ids instead of source×target combinations.
     */
    @Suppress("UnusedParameter")
    fun gets(
        database: String,
        table: String,
        ids: List<Any>,
        filters: String? = null,
        features: List<String> = emptyList(),
    ): Mono<DataFrameEdgePayload> {
        val name = EntityName(database, table)
        val label = engine.getLabel(name)

        require(label.entity.type == LabelType.MULTI_EDGE) {
            "get query with ids is only supported for multi-edge tables."
        }

        val keys = ids.distinct().map { id -> id to id }

        return getsByKeys(database, table, keys, filters, features)
    }

    private fun getsByKeys(
        database: String,
        table: String,
        keys: List<Pair<Any, Any>>,
        filters: String? = null,
        features: List<String> = emptyList(),
    ): Mono<DataFrameEdgePayload> {
        val name = EntityName(database, table)
        val label = engine.getLabel(name)

        require(label is HBaseHashLabel) {
            "get query is only supported for HBaseHashLabel, but got ${label::class.java.simpleName}."
        }

        require(features.isEmpty()) { "`features` ${features.joinToString(", ")} are not supported in get query." }
        val postPredicates = filters?.let { WherePredicate.parse(it, label.entity.schema) }?.toSet() ?: emptySet()

        val gets =
            keys.map { (s, t) ->
                val edge = Edge(0L, s, t).ensureType(label.entity.schema)
                val key = label.coder.encodeHashEdgeKey(edge, label.entity.id)
                Get(key.key)
                    .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER)
            }

        return label
            .getActiveStates(gets)
            .map {
                it.applyPredicates(postPredicates).toDataFrameEdgePayload(flip = false)
            }.switchIfEmpty(emptyDataFrameEdgePayload)
    }

    fun scan(
        database: String,
        table: String,
        index: String,
        start: Any,
        direction: Direction,
        limit: Int = ScanFilter.defaultLimit,
        offset: String? = null,
        ranges: String? = null,
        filters: String? = null,
        features: List<String> = emptyList(),
    ): Mono<DataFrameEdgePayload> {
        val name = EntityName(database, table)
        val label = engine.getLabel(name)

        val indexFieldNames =
            label.entity.indices
                .find { it.name == index }
                ?.let { it.fields.map { field -> field.name } }
        requireNotNull(indexFieldNames) { "index `$index` is not found in label `${label.entity.name}`." }

        // matching index predicates with the index field names
        // index fields: a, b, c
        // valid predicate key orders:
        // - none
        // - a
        // - a, b
        // - a, b, c
        val indexPredicates = ranges?.let { WherePredicate.parse(it) }?.toSet() ?: emptySet() // order preserved
        val indexPredicateKeys = indexPredicates.map { it.key }

        val lazyIndexMismatchErrorMessage: () -> String = {
            "valid `ranges` order for the index `$index` is $indexFieldNames. input was: $indexPredicateKeys."
        }
        require(indexPredicateKeys.size <= indexFieldNames.size, lazyIndexMismatchErrorMessage)
        indexPredicateKeys.zip(indexFieldNames).forEach { (predicateFieldName, indexFieldName) ->
            require(predicateFieldName == indexFieldName, lazyIndexMismatchErrorMessage)
        }

        val postPredicates = filters?.let { WherePredicate.parse(it, label.entity.schema) }?.toSet() ?: emptySet()

        if (FEATURE_TOTAL in features) {
            require(indexPredicates.isEmpty() && postPredicates.isEmpty()) {
                "total count does not support with `ranges` or `filters`."
            }
        }
        val invalidFeatures = features - AVAILABLE_SCAN_FEATURES
        require(invalidFeatures.isEmpty()) { "`features` ${invalidFeatures.joinToString(", ")} are not supported in get query." }

        val scanFilter =
            ScanFilter(
                name = name,
                srcSet = setOf(start),
                dir = direction,
                limit = limit,
                offset = offset,
                indexName = index,
                otherPredicates = indexPredicates,
            )

        val totalMono =
            if (FEATURE_TOTAL in features) {
                label
                    .count(setOf(start), direction)
                    .map {
                        val jsonFormat = it.toJsonFormat()
                        jsonFormat.data.first()[SELECT_COUNT_FIELD] as Long
                    }
            } else {
                DEFAULT_TOTAL_VALUE_MONO
            }
        val dfMono = engine.singleStepQuery(scanFilter)

        return dfMono
            .zipWith(totalMono)
            .map { tuple ->
                val df = tuple.t1
                val total = tuple.t2
                val flip = direction == Direction.IN
                df.applyPredicates(postPredicates).toDataFrameEdgePayload(flip, total)
            }.switchIfEmpty(emptyDataFrameEdgePayload)
    }

    fun seek(
        database: String,
        table: String,
        cache: String,
        start: Any,
        direction: Direction,
        limit: Int = ScanFilter.defaultLimit,
        offset: String? = null,
    ): Mono<DataFrameEdgePayload> {
        val name = EntityName(database, table)
        val label = engine.getLabel(name)

        require(label is HBaseHashLabel) {
            "cache query is only supported for HBaseHashLabel, but got ${label::class.java.simpleName}."
        }

        val cacheEntity = label.entity.caches.find { it.cache == cache }
        requireNotNull(cacheEntity) { "cache `$cache` is not found in label `${label.entity.name}`." }

        val order = cacheEntity.fields.first().order
        val casted =
            if (direction == Direction.OUT) {
                label.entity.schema.src.type.type
                    .cast(start)
            } else {
                label.entity.schema.tgt.type.type
                    .cast(start)
            }
        val key =
            EdgeCacheRecord.Key.of(
                directedSource = casted,
                tableCode = label.entity.id,
                direction = direction.toV3(),
                cacheCode = cacheEntity.code,
            )
        val encodedKey = cacheRecordMapper.encoder.encodeKey(key)

        val offsetNext =
            offset?.let {
                CryptoUtils.decodeAndDecryptUrlSafe(it)
            }
        // offsetNext maps to ColumnRangeFilter min (ascending byte order start)
        val (from, to) =
            if (offsetNext == null) {
                null to null
            } else if (order == Order.DESC) {
                null to offsetNext
            } else {
                offsetNext to null
            }

        return label
            .hbaseGetWideRow(listOf(encodedKey), from, to, order, limit = limit + 1)
            .map { records ->
                val hasNext = records.size > limit
                val results = if (hasNext) records.dropLast(1) else records
                val fieldNameMap = label.entity.schema.hashToFieldNameMap
                val edges =
                    results.map { record ->
                        val decoded = cacheRecordMapper.decoder.decode(record.key, record.qualifier, record.value)
                        val (source, target) =
                            when (direction) {
                                Direction.OUT -> decoded.key.directedSource to decoded.qualifier.directedTarget
                                Direction.IN -> decoded.qualifier.directedTarget to decoded.key.directedSource
                            }
                        EdgePayload(
                            version = decoded.value.version,
                            source = source,
                            target = target,
                            properties =
                                decoded.value.properties
                                    .mapNotNull { (code, value) ->
                                        fieldNameMap[code]?.let { it to value }
                                    }.toMap(),
                            context = emptyMap(),
                        )
                    }
                val nextOffset =
                    if (hasNext) {
                        results.lastOrNull()?.qualifier?.let {
                            CryptoUtils.encryptAndEncodeUrlSafe(it)
                        }
                    } else {
                        null
                    }
                DataFrameEdgePayload(
                    edges = edges,
                    count = edges.size,
                    total = edges.size.toLong(),
                    offset = nextOffset,
                    hasNext = hasNext,
                    context = emptyMap(),
                )
            }.switchIfEmpty(emptyDataFrameEdgePayload)
    }

    fun agg(
        database: String,
        table: String,
        group: String,
        start: List<Any>,
        direction: Direction,
        ranges: String,
        filters: String? = null,
        features: List<String> = emptyList(),
        ttl: Long? = null,
    ): Mono<DataFrameEdgeAggPayload> {
        val name = EntityName(database, table)
        val label = engine.getLabel(name)

        require(label is HBaseHashLabel) {
            "group query is only supported for HBaseHashLabel, but got ${label::class.java.simpleName}."
        }

        val groupEntity = label.entity.groups.find { it.group == group }
        val groupFieldNames = groupEntity?.fields?.map { field -> field.bucket?.name ?: field.name }
        requireNotNull(groupFieldNames) { "group `$group` is not found in label `${label.entity.name}`." }
        require(filters == null) { "`filters` is not yet supported in count query." }

        val ffs = features.toSet()
        val ffAccumulators = ffs.contains("accumulators")
        val ffCached = ffs.contains("cachedRecords")
        require((ffs - setOf("accumulators", "trends", "cachedRecords")).isEmpty()) {
            "`features` ${features.joinToString(", ")} are not supported in agg query. only `accumulators` and `trends` are supported."
        }

        // matching index predicates with the index field names
        // index fields: a, b, c
        // valid predicate key orders:
        // - none
        // - a
        // - a, b
        // - a, b, c
        val groupPredicates = WherePredicate.parse(ranges).toSet()
        val groupPredicateKeys = groupPredicates.map { it.key }

        val lazyGroupFieldMismatchErrorMessage: () -> String = {
            "valid `ranges` order for the index `$group` is $groupFieldNames. input was: $groupPredicateKeys."
        }

        require(groupPredicateKeys.size <= groupFieldNames.size, lazyGroupFieldMismatchErrorMessage)
        groupPredicateKeys.zip(groupFieldNames).forEach { (predicateFieldName, groupFieldName) ->
            require(predicateFieldName == groupFieldName, lazyGroupFieldMismatchErrorMessage)
        }

        val keys =
            start.distinct().map {
                val casted =
                    if (direction == Direction.OUT) {
                        label.entity.schema.src.type.type
                            .cast(it)
                    } else {
                        label.entity.schema.tgt.type.type
                            .cast(it)
                    }

                val key =
                    EdgeGroupRecord.Key.of(
                        directedSource = casted,
                        tableCode = label.entity.id,
                        direction = direction.toV3(),
                        groupCode = groupEntity.code,
                    )
                groupRecordMapper.encoder.encodeKey(key)
            }
        require(groupPredicates.isNotEmpty()) {
            "group query requires at least one group predicate, but got none."
        }

        val fieldPredicatePairs = groupEntity.fields.zip(groupPredicates)
        val (firstPairs, lastPair) = fieldPredicatePairs.dropLast(1) to fieldPredicatePairs.last()
        val (lastField, lastPredicate) = lastPair

        val eqValues =
            firstPairs.map { (field, predicate) ->
                require(predicate is WherePredicate.Eq) {
                    "only `Eq` predicate is allowed for the first ${firstPairs.size} group fields, but got $predicate."
                }
                field.bucketOrGet(predicate.value, ceil = false)
            }

        val (from, to) = encodeAggRanges(eqValues, lastField, lastPredicate)

        return label
            .hbaseGet(keys, from, to, EdgeGroupRecordMapper.GROUP_RECORD_ORDER, ttl)
            .map { (records, cached) ->
                val items =
                    records
                        .map { record -> createEdgeAggPayload(record, ffAccumulators) }
                        .groupingBy { it.start to it.direction }
                        .aggregate { key, accumulator: EdgeAggPayload?, element, first ->
                            if (first || accumulator == null) {
                                element
                            } else {
                                mergeEdgeAggPayloads(accumulator, element, ffAccumulators)
                            }
                        }.values
                        .toList()

                val rootContext =
                    if (ffCached) {
                        mapOf("cachedRecords" to cached)
                    } else {
                        emptyMap()
                    }

                DataFrameEdgeAggPayload(items, items.size, rootContext)
            }
    }

    fun query(request: ActionbaseQuery): Mono<Map<String, DataFrame>> = engine.query(request)

    private fun encodeAggRanges(
        values: List<Any>,
        lastField: Group.Field,
        lastPredicate: WherePredicate,
    ): Pair<ByteArray, ByteArray> {
        fun encode(vararg additionalValues: Any) =
            groupRecordMapper.encoder.encodeQualifier(
                EdgeGroupRecord.Qualifier(groupValues = values + additionalValues.toList()),
            )

        return when (lastPredicate) {
            is WherePredicate.Eq -> {
                val parsed = lastField.bucketOrGet(lastPredicate.value, ceil = false)
                encode(parsed).let { it to it }
            }
            is WherePredicate.Between -> {
                val from = encode(lastField.bucketOrGet(lastPredicate.from, ceil = false))
                val to = encode(lastField.bucketOrGet(lastPredicate.to, ceil = true))
                from to to
            }
            else -> throw IllegalArgumentException(
                "only `Eq` or `Between` predicate is allowed for group query, but got $lastPredicate.",
            )
        }
    }

    private fun createEdgeAggPayload(
        record: HBaseRecord,
        ffAccumulators: Boolean,
    ): EdgeAggPayload {
        val decodedRecord = groupRecordMapper.decoder.decode(record.key, record.qualifier, record.value)

        return EdgeAggPayload(
            start = decodedRecord.key.directedSource,
            direction = decodedRecord.key.direction,
            value = decodedRecord.value,
            context =
                buildMap {
                    if (ffAccumulators) {
                        put(
                            "accumulators",
                            listOf(
                                mapOf(
                                    "groupValues" to decodedRecord.qualifier.groupValues,
                                    "value" to decodedRecord.value,
                                ),
                            ),
                        )
                    }
                },
        )
    }

    private fun mergeEdgeAggPayloads(
        accumulator: EdgeAggPayload,
        element: EdgeAggPayload,
        ffAccumulators: Boolean,
    ): EdgeAggPayload =
        EdgeAggPayload(
            start = element.start,
            direction = element.direction,
            value = accumulator.value + element.value,
            context =
                buildMap {
                    if (ffAccumulators) {
                        put(
                            "accumulators",
                            (accumulator.context["accumulators"] as? List<*> ?: emptyList<Any>()) +
                                (element.context["accumulators"] as? List<*> ?: emptyList<Any>()),
                        )
                    }
                },
        )

    private fun DataFrame.applyPredicates(predicates: Set<WherePredicate>): DataFrame =
        if (predicates.isEmpty()) {
            this
        } else {
            this.where(predicates)
        }

    private fun DataFrame.toDataFrameEdgePayload(
        flip: Boolean,
        total: Long? = null, // null to indicate the total is the same as the number of rows
    ): DataFrameEdgePayload {
        val jsonFormat = toJsonFormat()
        val edges =
            jsonFormat.data.map { row ->
                EdgePayload(
                    version = row[TS_FIELD] as Long,
                    source = if (!flip) row[SRC_FIELD]!! else row[TGT_FIELD]!!,
                    target = if (!flip) row[TGT_FIELD]!! else row[SRC_FIELD]!!,
                    properties = row.filterKeys { it !in EDGE_FIELDS },
                    context = emptyMap(),
                )
            }
        return DataFrameEdgePayload(
            edges = edges,
            count = jsonFormat.rows,
            total = total ?: rows.size.toLong(),
            offset = if (jsonFormat.hasNext) jsonFormat.offset else null,
            hasNext = jsonFormat.hasNext,
            context = emptyMap(),
        )
    }

    companion object {
        private const val SELECT_COUNT_FIELD = "COUNT(1)"
        private const val TS_FIELD = "ts"
        private const val SRC_FIELD = "src"
        private const val TGT_FIELD = "tgt"
        private const val DIR_FIELD = "dir"
        private val EDGE_FIELDS =
            setOf(
                TS_FIELD,
                SRC_FIELD,
                TGT_FIELD,
                DIR_FIELD,
            )
        private const val FEATURE_TOTAL = "total"
        private const val DEFAULT_TOTAL_VALUE = -1L
        private val DEFAULT_TOTAL_VALUE_MONO = Mono.just(DEFAULT_TOTAL_VALUE)

        private val AVAILABLE_SCAN_FEATURES =
            setOf(
                FEATURE_TOTAL,
            )

        val emptyDataFrameEdgePayload: Mono<DataFrameEdgePayload> =
            Mono.just(
                DataFrameEdgePayload(
                    edges = emptyList(),
                    count = 0,
                    total = 0L,
                    offset = null,
                    hasNext = false,
                    context = emptyMap(),
                ),
            )

        val emptyDataFrameCountPayload: Mono<DataFrameEdgeCountPayload> =
            Mono.just(
                DataFrameEdgeCountPayload(
                    emptyList(),
                    0,
                    emptyMap(),
                ),
            )

        fun empty(direction: Direction): EdgeCountPayload =
            if (direction == Direction.OUT) {
                emptyEdgeCountPayloadOut
            } else {
                emptyEdgeCountPayloadIn
            }

        private val emptyEdgeCountPayloadOut: EdgeCountPayload =
            EdgeCountPayload(
                start = "",
                direction = Direction.OUT.toV3(),
                count = 0L,
                context = emptyMap(),
            )

        private val emptyEdgeCountPayloadIn: EdgeCountPayload =
            EdgeCountPayload(
                start = "",
                direction = Direction.IN.toV3(),
                count = 0L,
                context = emptyMap(),
            )

        private fun Direction.toV3(): com.kakao.actionbase.core.metadata.common.Direction =
            when (this) {
                Direction.OUT -> com.kakao.actionbase.core.metadata.common.Direction.OUT
                Direction.IN -> com.kakao.actionbase.core.metadata.common.Direction.IN
            }
    }
}
