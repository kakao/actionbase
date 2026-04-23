package com.kakao.actionbase.engine.query

import com.kakao.actionbase.core.java.codec.common.hbase.Order
import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.core.metadata.common.StructType
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.engine.QueryEngine
import com.kakao.actionbase.engine.sql.DataFrame
import com.kakao.actionbase.engine.sql.Row

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.LongNode
import com.fasterxml.jackson.databind.node.MissingNode
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.NumericNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * @see ActionbaseQuery
 */
class ActionbaseQueryExecutor(
    private val engine: QueryEngine,
) {
    private val objectMapper = jacksonObjectMapper()

    private fun refToValues(
        ref: ActionbaseQuery.Vertex.Ref,
        context: Map<String, DataFrame>,
    ): Set<Any> = context[ref.ref]?.getColumn(ref.field)?.filterNotNull()?.toSet() ?: emptySet()

    private fun resolveVertex(
        vertex: ActionbaseQuery.Vertex,
        context: Map<String, DataFrame>,
        actionBaseQuery: ActionbaseQuery,
    ): Mono<Set<Any>> =
        when (vertex) {
            is ActionbaseQuery.Vertex.Ref -> Mono.just(refToValues(vertex, context))
            is ActionbaseQuery.Vertex.Value -> Mono.just(vertex.value.toSet())
            is ActionbaseQuery.Vertex.Step ->
                processQuery(vertex.step, context, actionBaseQuery)
                    .map { df -> df.getColumn(vertex.field).filterNotNull().toSet() }
        }

    fun query(actionBaseQuery: ActionbaseQuery): Mono<Map<String, DataFrame>> {
        val computed: Mono<Map<String, DataFrame>> =
            actionBaseQuery.query.fold(Mono.just(emptyMap())) { acc, queryItem ->
                acc.flatMap { context ->
                    processQuery(queryItem, context, actionBaseQuery)
                        .map { context + (queryItem.name to it) }
                }
            }
        val returnNames = actionBaseQuery.query.filter { it.include }.map { it.name }
        return computed.map { context ->
            returnNames.associateWith { context.getValue(it) }
        }
    }

    private fun processQuery(
        queryItem: ActionbaseQuery.Item,
        context: Map<String, DataFrame>,
        actionBaseQuery: ActionbaseQuery,
    ): Mono<DataFrame> =
        processQueryItem(queryItem, context, actionBaseQuery)
            .flatMap { applyPostProcessors(it, queryItem.post) }
            .flatMap { applyAggregators(it, queryItem.aggregators) }
            .let { if (queryItem.memoize) it.cache() else it }

    private fun processQueryItem(
        queryItem: ActionbaseQuery.Item,
        context: Map<String, DataFrame>,
        actionBaseQuery: ActionbaseQuery,
    ): Mono<DataFrame> =
        when (queryItem) {
            is ActionbaseQuery.Item.Self -> processSelf(queryItem, context, actionBaseQuery)
            is ActionbaseQuery.Item.Get -> processGet(queryItem, context, actionBaseQuery)
            is ActionbaseQuery.Item.Count -> processCount(queryItem, context, actionBaseQuery)
            is ActionbaseQuery.Item.Scan -> processScan(queryItem, context, actionBaseQuery)
            is ActionbaseQuery.Item.Seek -> processSeek(queryItem, context, actionBaseQuery)
        }

    private fun applyPostProcessors(
        df: DataFrame,
        postProcessors: List<ActionbaseQuery.PostProcessor>,
    ): Mono<DataFrame> =
        postProcessors.fold(Mono.just(df)) { acc, postProcessor ->
            acc.flatMap { applyPostProcessor(it, postProcessor) }
        }

    private fun applyPostProcessor(
        df: DataFrame,
        postProcessor: ActionbaseQuery.PostProcessor,
    ): Mono<DataFrame> =
        when (postProcessor) {
            is ActionbaseQuery.PostProcessor.JsonObject -> postProcessJsonObject(df, postProcessor)
            is ActionbaseQuery.PostProcessor.SplitExplode -> postProcessorSplitExplode(df, postProcessor)
        }

    private fun applyAggregators(
        df: DataFrame,
        aggregators: List<ActionbaseQuery.Aggregator>,
    ): Mono<DataFrame> =
        aggregators.fold(Mono.just(df)) { acc, aggregator ->
            acc.flatMap { applyAggregator(it, aggregator) }
        }

    private fun applyAggregator(
        df: DataFrame,
        aggregator: ActionbaseQuery.Aggregator,
    ): Mono<DataFrame> =
        when (aggregator) {
            is ActionbaseQuery.Aggregator.Flatten -> Mono.just(df)
            is ActionbaseQuery.Aggregator.Count -> aggregateCount(df, aggregator)
            is ActionbaseQuery.Aggregator.Sum -> aggregateSum(df, aggregator)
        }

    private fun processSelf(
        queryItem: ActionbaseQuery.Item.Self,
        context: Map<String, DataFrame>,
        actionBaseQuery: ActionbaseQuery,
    ): Mono<DataFrame> =
        resolveVertex(queryItem.source, context, actionBaseQuery).flatMap { source ->
            val keys = source.map { it to it }

            engine
                .getTableBinding(database = queryItem.database, alias = queryItem.table)
                .gets(keys, null)
        }

    private fun processGet(
        queryItem: ActionbaseQuery.Item.Get,
        context: Map<String, DataFrame>,
        actionBaseQuery: ActionbaseQuery,
    ): Mono<DataFrame> =
        resolveVertex(queryItem.source, context, actionBaseQuery)
            .zipWith(resolveVertex(queryItem.target, context, actionBaseQuery))
            .flatMap { tuple ->
                val sources = tuple.t1
                val targets = tuple.t2
                val keys = sources.flatMap { s -> targets.map { t -> s to t } }

                engine
                    .getTableBinding(database = queryItem.database, alias = queryItem.table)
                    .gets(keys, null)
            }

    private fun processCount(
        queryItem: ActionbaseQuery.Item.Count,
        context: Map<String, DataFrame>,
        actionBaseQuery: ActionbaseQuery,
    ): Mono<DataFrame> =
        resolveVertex(queryItem.source, context, actionBaseQuery).flatMap { source ->
            engine
                .getTableBinding(database = queryItem.database, alias = queryItem.table)
                .count(source, queryItem.direction)
        }

    private fun processScan(
        queryItem: ActionbaseQuery.Item.Scan,
        context: Map<String, DataFrame>,
        actionBaseQuery: ActionbaseQuery,
    ): Mono<DataFrame> =
        resolveVertex(queryItem.source, context, actionBaseQuery).flatMap { source ->
            Flux
                .fromIterable(source)
                .flatMap { start ->
                    engine
                        .getTableBinding(database = queryItem.database, alias = queryItem.table)
                        .scan(queryItem.index, start, queryItem.direction, queryItem.limit, queryItem.offset, ranges = queryItem.ranges, filters = null, features = emptyList())
                }.reduce { a, b ->
                    DataFrame(rows = a.rows + b.rows, schema = a.schema, total = a.total + b.total)
                }.switchIfEmpty(Mono.just(DataFrame.empty))
        }

    private fun processSeek(
        queryItem: ActionbaseQuery.Item.Seek,
        context: Map<String, DataFrame>,
        actionBaseQuery: ActionbaseQuery,
    ): Mono<DataFrame> =
        resolveVertex(queryItem.source, context, actionBaseQuery).flatMap { source ->
            Flux
                .fromIterable(source)
                .flatMap { start ->
                    engine
                        .getTableBinding(database = queryItem.database, alias = queryItem.table)
                        .seek(queryItem.cache, start, queryItem.direction, queryItem.limit, offset = null)
                }.reduce { a, b ->
                    DataFrame(rows = a.rows + b.rows, schema = a.schema, total = a.total + b.total)
                }.switchIfEmpty(Mono.just(DataFrame.empty))
        }

    // region Aggregators

    internal fun aggregateCount(
        df: DataFrame,
        agg: ActionbaseQuery.Aggregator.Count,
    ): Mono<DataFrame> =
        Mono.fromCallable {
            val grouped =
                df.rows
                    .groupingBy { it.data[agg.field] }
                    .eachCount()
                    .entries
                    .let { counts ->
                        when (agg.order) {
                            Order.ASC -> counts.sortedBy { it.value }
                            Order.DESC -> counts.sortedByDescending { it.value }
                        }
                    }.take(agg.limit)

            val sourceField = df.schema.getField(agg.field)
            val newSchema =
                StructType(
                    listOf(
                        sourceField,
                        StructField("count", PrimitiveType.LONG, "", false),
                    ),
                )
            val newRows =
                grouped.map {
                    Row(mapOf(agg.field to it.key, "count" to it.value.toLong()), newSchema)
                }
            DataFrame(newRows, newSchema, total = newRows.size.toLong())
        }

    internal fun aggregateSum(
        df: DataFrame,
        agg: ActionbaseQuery.Aggregator.Sum,
    ): Mono<DataFrame> =
        Mono.fromCallable {
            val grouped =
                df.rows
                    .groupingBy { row -> agg.keyFields.map { row.data[it] } }
                    .fold(0.0) { acc, row -> acc + (row.data[agg.valueField] as Number).toDouble() }
                    .entries
                    .let { sums ->
                        when (agg.order) {
                            Order.ASC -> sums.sortedBy { it.value }
                            Order.DESC -> sums.sortedByDescending { it.value }
                        }
                    }.take(agg.limit)

            val keyFields = agg.keyFields.map { df.schema.getField(it) }
            val newSchema = StructType(keyFields + StructField(agg.valueField, PrimitiveType.DOUBLE, "", false))
            val newRows =
                grouped.map { (keys, sum) ->
                    val data = agg.keyFields.zip(keys).toMap() + (agg.valueField to sum)
                    Row(data, newSchema)
                }
            DataFrame(newRows, newSchema, total = newRows.size.toLong())
        }

    // endregion

    // region PostProcessors

    internal fun postProcessJsonObject(
        df: DataFrame,
        plan: ActionbaseQuery.PostProcessor.JsonObject,
    ): Mono<DataFrame> =
        Mono.fromCallable {
            val extractedColumns = extractJsonColumns(df, plan)
            val newSchema = createNewSchema(df, plan)
            val newRows = createNewRows(df, plan, extractedColumns, newSchema)
            DataFrame(newRows, newSchema, total = newRows.size.toLong())
        }

    private fun extractJsonColumns(
        df: DataFrame,
        plan: ActionbaseQuery.PostProcessor.JsonObject,
    ): List<List<Any?>> =
        df.getColumn(plan.field).map { columnValue ->
            columnValue?.let { parseJsonValue(it.toString(), plan.paths) } ?: plan.paths.map { null }
        }

    private fun parseJsonValue(
        jsonString: String,
        paths: List<ActionbaseQuery.PostProcessor.JsonObject.Path>,
    ): List<Any?> {
        val root = objectMapper.readTree(jsonString)
        return paths.map { path ->
            when (val node = traversePath(root, path.path)) {
                null, is MissingNode, is NullNode -> null
                is BooleanNode -> node.booleanValue()
                is IntNode -> node.intValue()
                is LongNode -> node.longValue()
                is NumericNode -> node.numberValue().toDouble()
                is TextNode -> node.asText()
                is ArrayNode -> node.toString()
                is ObjectNode -> node.toString()
                else -> node.toString()
            }
        }
    }

    @Suppress("ReturnCount")
    private fun traversePath(
        root: JsonNode,
        path: String,
    ): JsonNode? {
        var current: JsonNode = root
        for (key in path.split(".")) {
            if (current is ObjectNode) {
                current = current.get(key) ?: return null
            } else {
                return null
            }
        }
        return current
    }

    private fun createNewSchema(
        df: DataFrame,
        plan: ActionbaseQuery.PostProcessor.JsonObject,
    ): StructType {
        val extractedFields = plan.paths.map { path -> StructField(path.alias, PrimitiveType.valueOf(path.dataType.name), "", true) }
        val baseFields =
            if (plan.drop) {
                df.schema.fields.filter { it.name != plan.field }
            } else {
                df.schema.fields
            }
        return StructType(baseFields + extractedFields)
    }

    private fun createNewRows(
        df: DataFrame,
        plan: ActionbaseQuery.PostProcessor.JsonObject,
        extractedColumns: List<List<Any?>>,
        newSchema: StructType,
    ): List<Row> =
        df.rows.mapIndexed { rowIndex, row ->
            val baseData =
                if (plan.drop) {
                    row.data.filterKeys { it != plan.field }
                } else {
                    row.data
                }
            val extractedData = plan.paths.mapIndexed { i, path -> path.alias to extractedColumns[rowIndex][i] }.toMap()
            Row(baseData + extractedData, newSchema)
        }

    internal fun postProcessorSplitExplode(
        df: DataFrame,
        plan: ActionbaseQuery.PostProcessor.SplitExplode,
    ): Mono<DataFrame> =
        Mono.fromCallable {
            val newFields =
                if (plan.drop) {
                    df.schema.fields.filter { it.name != plan.field } +
                        StructField(plan.alias, PrimitiveType.valueOf(plan.dataType.name), "", true)
                } else {
                    df.schema.fields + StructField(plan.alias, PrimitiveType.valueOf(plan.dataType.name), "", true)
                }
            val newSchema = StructType(newFields)

            val newRows =
                df.rows.flatMap { row ->
                    val fieldValue = row.data[plan.field] as? String
                    if (fieldValue.isNullOrEmpty()) {
                        return@flatMap emptyList<Row>()
                    }

                    val splitValues =
                        if (plan.limit <= 0) {
                            fieldValue.split(plan.regex.toRegex())
                        } else {
                            fieldValue.split(plan.regex.toRegex(), plan.limit)
                        }

                    splitValues.filter { it.isNotEmpty() }.map { splitValue ->
                        val baseData =
                            if (plan.drop) {
                                row.data.filterKeys { it != plan.field }
                            } else {
                                row.data
                            }
                        Row(baseData + (plan.alias to plan.dataType.cast(splitValue)), newSchema)
                    }
                }

            DataFrame(newRows, newSchema, total = newRows.size.toLong())
        }

    // endregion
}
