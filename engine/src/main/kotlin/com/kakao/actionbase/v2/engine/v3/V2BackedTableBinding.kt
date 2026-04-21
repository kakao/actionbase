package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.v2.core.code.hbase.Constants as HBaseConstants
import com.kakao.actionbase.v2.engine.sql.DataFrame as V2DataFrame

import com.kakao.actionbase.core.Constants
import com.kakao.actionbase.core.edge.EdgeField
import com.kakao.actionbase.core.edge.MutationKey
import com.kakao.actionbase.core.edge.mapper.EdgeGroupRecordMapper
import com.kakao.actionbase.core.edge.mapper.EdgeRecordMapper
import com.kakao.actionbase.core.edge.mutation.EdgeMutationBuilder
import com.kakao.actionbase.core.edge.mutation.EdgeMutationRecords
import com.kakao.actionbase.core.edge.payload.DataFrameEdgeAggPayload
import com.kakao.actionbase.core.edge.payload.EdgeAggPayload
import com.kakao.actionbase.core.edge.record.EdgeGroupRecord
import com.kakao.actionbase.core.edge.record.EdgeStateRecord
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.state.SpecialStateValue
import com.kakao.actionbase.core.state.State
import com.kakao.actionbase.core.storage.HBaseRecord
import com.kakao.actionbase.engine.binding.MutationRecordsSummary
import com.kakao.actionbase.engine.binding.TableBinding
import com.kakao.actionbase.engine.metadata.MutationMode
import com.kakao.actionbase.engine.sql.DataFrame
import com.kakao.actionbase.engine.sql.Row
import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.label.LockAcquisitionFailedException
import com.kakao.actionbase.v2.engine.label.hbase.HBaseIndexedLabel
import com.kakao.actionbase.v2.engine.sql.ScanFilter
import com.kakao.actionbase.v2.engine.sql.WherePredicate
import com.kakao.actionbase.v2.engine.sql.toJsonFormat

import org.apache.hadoop.hbase.client.Delete
import org.apache.hadoop.hbase.client.Get
import org.apache.hadoop.hbase.client.Increment
import org.apache.hadoop.hbase.client.Mutation
import org.apache.hadoop.hbase.client.Put
import org.slf4j.LoggerFactory

import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

class V2BackedTableBinding(
    private val descriptor: V3TableDescriptor,
    private val label: HBaseIndexedLabel,
    private val mapper: EdgeRecordMapper,
    private val lockTimeout: Long,
) : TableBinding {
    override val table: String = descriptor.table
    override val schema: ModelSchema = descriptor.schema
    override val mutationMode: MutationMode = MutationMode.valueOf(label.entity.mode.name)

    private val groupRecordMapper = mapper.group
    private val cacheRecordMapper = mapper.cache

    // -- mutation

    override fun <T> withLock(
        key: MutationKey,
        action: () -> Mono<T>,
    ): Mono<T> {
        val (source, target) = key.toSourceTarget()
        val traceId = "$key@${System.currentTimeMillis()}"
        with(label) {
            val compatibleEdge = Edge(0L, source, target)
            val lockEdge = coder.encodeLockEdge(compatibleEdge, entity.id)
            return acquireLock(traceId, lockEdge, false)
                .flatMap { action() }
                .doFinally {
                    releaseLock(traceId, lockEdge, false)
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe({}, { log.error("Lock release failed for {}", key, it) })
                }.subscribeOn(Schedulers.boundedElastic())
        }
    }

    override fun read(key: MutationKey): Mono<State> {
        val (source, target) = key.toSourceTarget()
        with(label) {
            val compatibleEdge = Edge(0L, source, target)
            return findHashEdge(coder.encodeHashEdgeKey(compatibleEdge, entity.id))
                .map { decodeV2HashEdgeToState(it) }
                .switchIfEmpty(Mono.defer { Mono.just(State.initial) })
                .subscribeOn(Schedulers.boundedElastic())
        }
    }

    override fun write(
        key: MutationKey,
        before: State,
        after: State,
    ): Mono<MutationRecordsSummary> {
        val (source, target) = key.toSourceTarget()
        val beforeClean = before.specialStateValueToNull()
        val afterClean = after.specialStateValueToNull()
        val beforeRecord = EdgeStateRecord.of(source, target, beforeClean, label.entity.id)
        val afterRecord = EdgeStateRecord.of(source, target, afterClean, label.entity.id)
        val records = buildMutationRecords(beforeRecord, afterRecord)
        return label
            .handleDeferredRequests(buildHBaseMutations(records))
            .thenReturn(MutationRecordsSummary(records.status, records.acc, beforeClean, afterClean))
    }

    override fun handleMutationError(error: Throwable) {
        if (error is LockAcquisitionFailedException) {
            label
                .findStaleLockAndClear(error.lockEdge, lockTimeout)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe({}, { log.error("Stale lock clear failed", it) })
        }
    }

    // -- query

    override fun count(
        sources: Set<Any>,
        direction: Direction,
    ): Mono<DataFrame> =
        label
            .count(sources, direction)
            .map { it.toV3() }
            .switchIfEmpty(EMPTY_DATAFRAME)

    override fun gets(
        keys: List<Pair<Any, Any>>,
        filters: String?,
    ): Mono<DataFrame> {
        val postPredicates = filters?.let { WherePredicate.parse(it, label.entity.schema) }?.toSet() ?: emptySet()

        val hbaseGets =
            keys.map { (s, t) ->
                val edge = Edge(0L, s, t).ensureType(label.entity.schema)
                val key = label.coder.encodeHashEdgeKey(edge, label.entity.id)
                Get(key.key)
                    .addColumn(Constants.DEFAULT_COLUMN_FAMILY, Constants.DEFAULT_QUALIFIER)
            }

        return label
            .getActiveStates(hbaseGets)
            .map {
                it.applyPredicates(postPredicates).toV3()
            }.switchIfEmpty(EMPTY_DATAFRAME)
    }

    override fun scan(
        index: String,
        start: Any,
        direction: Direction,
        limit: Int,
        offset: String?,
        ranges: String?,
        filters: String?,
        features: List<String>,
    ): Mono<DataFrame> {
        val indexFieldNames =
            label.entity.indices
                .find { it.name == index }
                ?.let { it.fields.map { field -> field.name } }
        requireNotNull(indexFieldNames) { "index `$index` is not found in label `${label.entity.name}`." }

        val indexPredicates = ranges?.let { WherePredicate.parse(it) }?.toSet() ?: emptySet()
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

        val name = EntityName(descriptor.database, descriptor.table)
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
                        jsonFormat.data.first()[DataFrame.COUNT_FIELD] as Long
                    }
            } else {
                DEFAULT_TOTAL_VALUE_MONO
            }

        @Suppress("UNCHECKED_CAST")
        val dfMono = label.scan(scanFilter, emptySet(), label.coder as com.kakao.actionbase.v2.core.code.IdEdgeEncoder)

        return dfMono
            .zipWith(totalMono)
            .map { tuple ->
                val df = tuple.t1
                val total = tuple.t2
                df.applyPredicates(postPredicates).toV3(total)
            }.switchIfEmpty(EMPTY_DATAFRAME)
    }

    override fun seek(
        cache: String,
        start: Any,
        direction: Direction,
        limit: Int,
        offset: String?,
    ): Mono<DataFrame> =
        label
            .cache(listOf(start), cache, direction, limit, offset)
            .map { it.toV3() }
            .switchIfEmpty(EMPTY_DATAFRAME)

    override fun agg(
        group: String,
        start: List<Any>,
        direction: Direction,
        ranges: String,
        filters: String?,
        features: List<String>,
        ttl: Long?,
    ): Mono<DataFrameEdgeAggPayload> {
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
                        .aggregate { _, accumulator: EdgeAggPayload?, element, first ->
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

    // -- mutation helpers

    private fun buildMutationRecords(
        before: EdgeStateRecord,
        after: EdgeStateRecord,
    ): EdgeMutationRecords {
        val schema = descriptor.schema
        return when (schema) {
            is ModelSchema.Edge ->
                EdgeMutationBuilder.buildForUniqueEdge(before, after, schema.direction, schema.indexes, schema.groups, schema.caches)
            is ModelSchema.MultiEdge ->
                EdgeMutationBuilder.buildForMultiEdge(before, after, schema.direction, schema.indexes, schema.groups, schema.caches)
        }
    }

    private fun decodeV2HashEdgeToState(encodedValue: ByteArray): State {
        val codeToFieldNameMap = schema.codeToName
        val stateValue = mapper.state.decoder.decodeValue(encodedValue)
        return State(
            stateValue.active,
            stateValue.version,
            stateValue.createdAt,
            stateValue.deletedAt,
            stateValue.properties
                .mapNotNull { (key, value) ->
                    codeToFieldNameMap[key]?.let { name -> name to value }
                }.toMap(),
        )
    }

    private fun buildHBaseMutations(mutationRecords: EdgeMutationRecords): List<Mutation> {
        val mutations = mutableListOf<Mutation>()
        val record = mapper.state.encoder.encode(mutationRecords.stateRecord)
        mutations +=
            Put(record.key)
                .addColumn(HBaseConstants.DEFAULT_COLUMN_FAMILY, HBaseConstants.DEFAULT_QUALIFIER, record.value)
        mutations +=
            mutationRecords.createIndexRecords.map {
                val record = mapper.index.encoder.encode(it)
                Put(record.key)
                    .addColumn(HBaseConstants.DEFAULT_COLUMN_FAMILY, HBaseConstants.DEFAULT_QUALIFIER, record.value)
            }
        mutations +=
            mutationRecords.countRecords.map {
                val key = mapper.count.encoder.encodeKey(it.key)
                Increment(key)
                    .addColumn(HBaseConstants.DEFAULT_COLUMN_FAMILY, HBaseConstants.DEFAULT_QUALIFIER, it.value)
            }
        mutations +=
            mutationRecords.deleteIndexRecordKeys.map {
                val key = mapper.index.encoder.encodeKey(it)
                Delete(key)
            }
        mutations +=
            mutationRecords.groupRecords.groupBy { it.key to it.ttl }.map { (groupKey, records) ->
                val (key, ttl) = groupKey
                val encodedKey = mapper.group.encoder.encodeKey(key)
                val increment = Increment(encodedKey)
                records.mergeQualifiers().forEach { (mergedQualifier, mergedValue) ->
                    val qualifier = mapper.group.encoder.encodeQualifier(mergedQualifier)
                    increment.addColumn(HBaseConstants.DEFAULT_COLUMN_FAMILY, qualifier, mergedValue)
                }
                if (ttl != null && ttl != Long.MAX_VALUE && ttl > 0) {
                    increment.ttl = ttl
                }
                increment
            }
        mutations +=
            mutationRecords.createCacheRecords.map {
                val encoded = mapper.cache.encoder.encode(it)
                Put(encoded.key)
                    .addColumn(HBaseConstants.DEFAULT_COLUMN_FAMILY, encoded.qualifier, encoded.value)
            }
        mutations +=
            mutationRecords.deleteCacheRecordQualifiers.map { (key, qualifier) ->
                val encodedKey = mapper.cache.encoder.encodeKey(key)
                val encodedQualifier = mapper.cache.encoder.encodeQualifier(qualifier)
                Delete(encodedKey)
                    .addColumns(HBaseConstants.DEFAULT_COLUMN_FAMILY, encodedQualifier)
            }
        return mutations
    }

    // -- query helpers

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

    private fun V2DataFrame.applyPredicates(predicates: Set<WherePredicate>): V2DataFrame =
        if (predicates.isEmpty()) {
            this
        } else {
            this.where(predicates)
        }

    companion object {
        private val log = LoggerFactory.getLogger(V2BackedTableBinding::class.java)

        private const val FEATURE_TOTAL = "total"
        private const val DEFAULT_TOTAL_VALUE = -1L
        private val DEFAULT_TOTAL_VALUE_MONO = Mono.just(DEFAULT_TOTAL_VALUE)
        private val AVAILABLE_SCAN_FEATURES = setOf(FEATURE_TOTAL)

        val EMPTY_DATAFRAME: Mono<DataFrame> = Mono.just(DataFrame.empty)

        private fun MutationKey.toSourceTarget(): Pair<Any, Any> =
            when (this) {
                is MutationKey.SourceTarget -> source to target
                is MutationKey.Id -> id to id
            }

        internal fun State.specialStateValueToNull(): State =
            State(
                active = this.active,
                version = this.version,
                createdAt = this.createdAt,
                deletedAt = this.deletedAt,
                properties =
                    this.properties.mapValues { (_, value) ->
                        if (SpecialStateValue.isSpecialStateValue(value.value)) {
                            value.copy(value = null)
                        } else {
                            value
                        }
                    },
            )

        fun List<EdgeGroupRecord>.mergeQualifiers(): Map<EdgeGroupRecord.Qualifier, Long> =
            this
                .groupingBy { it.qualifier }
                .fold(0L) { acc, record -> acc + record.value }
                .filterValues { it != 0L }

        fun Direction.toV3(): com.kakao.actionbase.core.metadata.common.Direction =
            when (this) {
                Direction.OUT -> com.kakao.actionbase.core.metadata.common.Direction.OUT
                Direction.IN -> com.kakao.actionbase.core.metadata.common.Direction.IN
            }
    }
}

internal fun V2DataFrame.toV3(total: Long? = null): DataFrame {
    val v3Schema =
        com.kakao.actionbase.core.metadata.common.StructType(
            schema.fields.map { field ->
                com.kakao.actionbase.core.metadata.common.StructField(
                    name = EdgeField.toV3(field.name),
                    type =
                        com.kakao.actionbase.core.types.PrimitiveType
                            .valueOf(field.type.name),
                    comment = field.desc,
                    nullable = field.isNullable,
                )
            },
        )
    val newRows =
        rows.map { row ->
            Row(
                data =
                    schema.fieldNames
                        .mapIndexed { i, name ->
                            EdgeField.toV3(name) to row.array[i]
                        }.toMap(),
                schema = v3Schema,
            )
        }
    val rawOffset = offsets.singleOrNull()
    val hasNext = rawOffset?.let { this.hasNext.singleOrNull() ?: false } ?: false
    val offset = if (hasNext) rawOffset else null
    return DataFrame(
        rows = newRows,
        schema = v3Schema,
        total = total ?: rows.size.toLong(),
        offset = offset,
        hasNext = hasNext,
    )
}
