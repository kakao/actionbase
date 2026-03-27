package com.kakao.actionbase.engine.query

import com.kakao.actionbase.engine.query.compat.toScanFilter
import com.kakao.actionbase.v2.core.code.EmptyEdgeIdEncoder
import com.kakao.actionbase.v2.core.types.Field
import com.kakao.actionbase.v2.core.types.StructType
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.Row

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

import reactor.core.publisher.Mono

/**
 * @see ActionbaseQuery
 */
class ActionbaseQueryExecutor(
    private val labelProvider: LabelProvider,
) {
    private val objectMapper = jacksonObjectMapper()

    private fun refToValues(
        ref: ActionbaseQuery.Vertex.Ref,
        context: Map<String, DataFrame>,
    ): Set<Any> = context[ref.ref]?.getColumn(ref.field)?.filterNotNull()?.toSet() ?: emptySet()

    private fun resolveVertex(
        vertex: ActionbaseQuery.Vertex,
        context: Map<String, DataFrame>,
    ): Set<Any> =
        when (vertex) {
            is ActionbaseQuery.Vertex.Ref -> refToValues(vertex, context)
            is ActionbaseQuery.Vertex.Value -> vertex.value.toSet()
        }

    fun query(actionBaseQuery: ActionbaseQuery): Mono<Map<String, DataFrame>> {
        val returnNames = actionBaseQuery.query.filter { it.include }.map { it.name }
        val computed: Mono<Map<String, DataFrame>> =
            actionBaseQuery.query.fold(Mono.just(emptyMap())) { acc, queryItem ->
                acc.flatMap { context ->
                    processQuery(queryItem, context, actionBaseQuery)
                        .map { context + (queryItem.name to it) }
                }
            }
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
            .let { if (queryItem.memoize) it.cache() else it }

    private fun processQueryItem(
        queryItem: ActionbaseQuery.Item,
        context: Map<String, DataFrame>,
        actionBaseQuery: ActionbaseQuery,
    ): Mono<DataFrame> =
        when (queryItem) {
            is ActionbaseQuery.Item.Self -> processSelf(queryItem, context, actionBaseQuery)
            is ActionbaseQuery.Item.Get -> processGet(queryItem, context, actionBaseQuery)
            is ActionbaseQuery.Item.Count -> processCount(queryItem, context)
            is ActionbaseQuery.Item.Scan -> processScan(queryItem, context, actionBaseQuery)
            is ActionbaseQuery.Item.Cache -> processCache(queryItem, context)
        }

    private fun applyPostProcessors(
        df: DataFrame,
        postProcessors: List<ActionbaseQuery.PostProcessor>,
    ): Mono<DataFrame> =
        postProcessors.fold(Mono.just(df) as Mono<DataFrame>) { acc, postProcessor ->
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

    private fun processSelf(
        queryItem: ActionbaseQuery.Item.Self,
        context: Map<String, DataFrame>,
        actionBaseQuery: ActionbaseQuery,
    ): Mono<DataFrame> {
        val label = labelProvider.getLabel(queryItem.database, queryItem.table)
        val src = resolveVertex(queryItem.source, context).toList()
        return label.getSelf(src, actionBaseQuery.stats, EmptyEdgeIdEncoder.INSTANCE)
    }

    private fun processGet(
        queryItem: ActionbaseQuery.Item.Get,
        context: Map<String, DataFrame>,
        actionBaseQuery: ActionbaseQuery,
    ): Mono<DataFrame> {
        val label = labelProvider.getLabel(queryItem.database, queryItem.table)
        val src = resolveVertex(queryItem.source, context).toList()
        val tgt = resolveVertex(queryItem.target, context).toList()
        return label.get(src, tgt, actionBaseQuery.stats, EmptyEdgeIdEncoder.INSTANCE)
    }

    private fun processCount(
        queryItem: ActionbaseQuery.Item.Count,
        context: Map<String, DataFrame>,
    ): Mono<DataFrame> {
        val label = labelProvider.getLabel(queryItem.database, queryItem.table)
        val src = resolveVertex(queryItem.source, context)
        return label.count(src, queryItem.direction)
    }

    private fun processScan(
        queryItem: ActionbaseQuery.Item.Scan,
        context: Map<String, DataFrame>,
        actionBaseQuery: ActionbaseQuery,
    ): Mono<DataFrame> {
        val label = labelProvider.getLabel(queryItem.database, queryItem.table)
        val src = resolveVertex(queryItem.source, context)
        val scanFilter = queryItem.toScanFilter(src)
        return label.scan(scanFilter, actionBaseQuery.stats, EmptyEdgeIdEncoder.INSTANCE)
    }

    @Suppress("UnusedParameter")
    private fun processCache(
        queryItem: ActionbaseQuery.Item.Cache,
        context: Map<String, DataFrame>,
    ): Mono<DataFrame> =
        // TODO Phase 3: resolve src via resolveVertex(), look up label via labelProvider,
        //  perform EdgeCache multi-get using cache and limit from queryItem,
        //  validate limit > 0 from user input
        Mono.just(DataFrame(emptyList(), StructType(emptyArray())))

    internal fun postProcessJsonObject(
        df: DataFrame,
        plan: ActionbaseQuery.PostProcessor.JsonObject,
    ): Mono<DataFrame> =
        Mono.fromCallable {
            val extractedColumns = extractJsonColumns(df, plan)
            val newFields = createNewFields(df, plan)
            val newRows = createNewRows(df, plan, extractedColumns, newFields)
            DataFrame(newRows, StructType(newFields.toTypedArray()))
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

    private fun createNewFields(
        df: DataFrame,
        plan: ActionbaseQuery.PostProcessor.JsonObject,
    ): List<Field> {
        val extractedFields = plan.paths.map { path -> Field(path.alias, path.dataType, true) }
        return if (plan.drop) {
            df.schema.fields.filter { it.name != plan.field } + extractedFields
        } else {
            df.schema.fields.toList() + extractedFields
        }
    }

    private fun createNewRows(
        df: DataFrame,
        plan: ActionbaseQuery.PostProcessor.JsonObject,
        extractedColumns: List<List<Any?>>,
        newFields: List<Field>,
    ): List<Row> =
        df.rows.mapIndexed { rowIndex, row ->
            val newRowValues = arrayOfNulls<Any?>(newFields.size)
            var columnIndex = 0

            df.schema.fields.forEachIndexed { index, field ->
                if (!plan.drop || field.name != plan.field) {
                    newRowValues[columnIndex++] = row[index]
                }
            }

            extractedColumns[rowIndex].forEachIndexed { index, value ->
                newRowValues[columnIndex + index] = value
            }

            Row(newRowValues)
        }

    internal fun postProcessorSplitExplode(
        df: DataFrame,
        plan: ActionbaseQuery.PostProcessor.SplitExplode,
    ): Mono<DataFrame> {
        return Mono.fromCallable {
            val fieldIndex = df.schema.fields.indexOfFirst { it.name == plan.field }

            require(fieldIndex != -1) { "Field ${plan.field} not found in the DataFrame" }

            val newFields =
                if (plan.drop) {
                    df.schema.fields.filterIndexed { index, _ -> index != fieldIndex } + Field(plan.alias, plan.dataType, true)
                } else {
                    df.schema.fields.toList() + Field(plan.alias, plan.dataType, true)
                }
            val newSchema = StructType(newFields.toTypedArray())

            val newRows =
                df.rows.flatMap { row ->
                    val fieldValue = row[fieldIndex] as? String
                    if (fieldValue.isNullOrEmpty()) {
                        return@flatMap emptyList<Row>() // Skip empty strings entirely
                    }

                    val splitValues =
                        if (plan.limit <= 0) {
                            fieldValue.split(plan.regex.toRegex())
                        } else {
                            fieldValue.split(plan.regex.toRegex(), plan.limit)
                        }

                    splitValues.filter { it.isNotEmpty() }.map { splitValue ->
                        val newArray = row.array.toMutableList()
                        if (plan.drop) {
                            newArray.removeAt(fieldIndex)
                        }
                        newArray.add(plan.dataType.cast(splitValue))
                        Row(newArray.toTypedArray())
                    }
                }

            DataFrame(newRows, newSchema)
        }
    }
}
