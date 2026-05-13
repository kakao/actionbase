package com.kakao.actionbase.v2.engine.sql

import com.kakao.actionbase.v2.core.types.StructType

import com.fasterxml.jackson.annotation.JsonIgnore

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

data class DataFrame(
    val rows: List<Row>,
    val schema: StructType,
    val stats: List<Stat<*>> = emptyList(),
    val offsets: List<String?> = emptyList(),
    val hasNext: List<Boolean> = emptyList(),
) {
    fun toRowWithSchema(): List<RowWithSchema> =
        rows.map { row ->
            RowWithSchema(row, schema)
        }

    fun filter(predicate: (RowWithSchema) -> Boolean): DataFrame {
        val filteredRows =
            rows.filter { row ->
                predicate(RowWithSchema(row, schema))
            }
        return DataFrame(filteredRows, schema)
    }

    fun select(vararg fieldNames: String): DataFrame = select(fieldNames.toList())

    fun select(fieldNames: List<String>): DataFrame {
        if (fieldNames.contains("*")) {
            return this
        }

        val indices =
            fieldNames
                .map { fieldName ->
                    val index = schema.fieldIndex(fieldName) // 1. Get index
                    index to fieldName
                }
        val newSchema =
            StructType(
                indices.map { (index, fieldName) -> schema.fields[index] }.toTypedArray(),
            )
        val newRows =
            if (schema == newSchema) {
                rows
            } else {
                rows.map { oldRow ->
                    val newRow = arrayOfNulls<Any?>(indices.size)
                    indices.forEachIndexed { idx, (index, _) ->
                        newRow[idx] = oldRow[index] // 2. Set value
                    }
                    Row(newRow) // Convert array to Row
                }
            }
        return DataFrame(newRows, newSchema)
    }

    fun getColumn(columnName: String): List<Any?> {
        val columnIndex = schema.fieldIndex(columnName)
        return rows.map { it[columnIndex] }
    }

    fun show() {
        val columnWidths = IntArray(schema.fieldNames.size) { 0 }
        rows.forEach { row ->
            row.array.forEachIndexed { index, value ->
                columnWidths[index] = maxOf(columnWidths[index], value.toString().length)
            }
        }
        schema.fieldNames.forEachIndexed { index, fieldName ->
            columnWidths[index] = maxOf(columnWidths[index], fieldName.length)
        }

        // Print the header
        println(columnWidths.joinToString("-+-", "+-", "+") { "-".repeat(it) })
        val header =
            schema.fieldNames
                .mapIndexed { index, fieldName ->
                    fieldName.padEnd(columnWidths[index], ' ')
                }.joinToString(" | ", "| ", "|")
        println(header)
        println(columnWidths.joinToString("-+-", "+-", "+") { "-".repeat(it) })

        // Print the rows
        rows.forEach { row ->
            val rowString =
                row.array
                    .mapIndexed { index, value ->
                        value.toString().padEnd(columnWidths[index], ' ')
                    }.joinToString(" | ", "| ", "|")
            println(rowString)
        }
        println(columnWidths.joinToString("-+-", "+-", "+") { "-".repeat(it) })
    }

    operator fun plus(other: DataFrame): DataFrame {
        require(schema == other.schema) { "Schemas must be the same" }
        val newRows = rows + other.rows
        return DataFrame(newRows, schema)
    }

    fun where(otherPredicates: Set<WherePredicate>): DataFrame {
        val filteredRows =
            rows.filter { row ->
                otherPredicates.all { predicate ->
                    predicate.eval(row, schema)
                }
            }
        // Preserve offsets/hasNext/stats: the post-filter is transparent
        // to the underlying paginated scan, whose cursor must survive
        // even when zero rows pass the predicate on this page.
        return copy(rows = filteredRows)
    }

    companion object {
        fun empty(structType: StructType): DataFrame = DataFrame(emptyList(), structType)

        val empty: DataFrame = DataFrame(emptyList(), StructType())
    }
}

data class QueryResult(
    val schema: String,
    val rows: List<Row>,
) {
    sealed interface OutputFormat

    data class JsonCompactFormat(
        val meta: List<Meta>,
        val data: List<Row>,
        val rows: Int,
        @JsonIgnore
        val rowsBeforeLimitAtLeast: Int,
        @JsonIgnore
        val statistics: Statistics,
        val stats: List<Stat<*>>,
        val offset: String?,
        val hasNext: Boolean,
    ) : OutputFormat

    data class JsonFormat(
        @JsonIgnore
        val meta: List<Meta>,
        val data: List<Map<String, Any?>>,
        val rows: Int,
        @JsonIgnore
        val rowsBeforeLimitAtLeast: Int,
        @JsonIgnore
        val statistics: Statistics,
        val stats: List<Stat<*>>,
        val offset: String?,
        val hasNext: Boolean,
    ) : OutputFormat

    data class NamedJsonFormat(
        val name: String,
        @JsonIgnore
        val meta: List<Meta>,
        val data: List<Map<String, Any?>>,
        val rows: Int,
        @JsonIgnore
        val rowsBeforeLimitAtLeast: Int,
        @JsonIgnore
        val statistics: Statistics,
        val stats: List<Stat<*>>,
        val offset: String?,
        val hasNext: Boolean,
    ) : OutputFormat

    data class Statistics(
        val elapsed: Double,
        val rowsRead: Int,
        val bytesRead: Int,
    )

    data class Meta(
        val name: String,
        val type: String,
    )
}

fun Mono<DataFrame>.show() {
    map { it.show() }.block()
}

fun Mono<DataFrame>.select(vararg fieldNames: String): Mono<DataFrame> = map { it.select(*fieldNames) }

fun Mono<DataFrame>.toRowFlux(): Flux<RowWithSchema> = flatMapIterable { it.toRowWithSchema() }

private fun Mono<DataFrame>.toJsonCompactFormat(): Mono<QueryResult.OutputFormat> =
    map {
        val rows = it.rows
        val stats = it.stats
        val meta =
            it.schema.fields.map { field ->
                QueryResult.Meta(field.name, field.type.name)
            }
        val offset = it.offsets.singleOrNull()
        val hasNext = if (offset == null) false else it.hasNext.singleOrNull() ?: false
        QueryResult.JsonCompactFormat(
            meta,
            rows,
            rows.size,
            rows.size,
            QueryResult.Statistics(0.0, 0, 0),
            stats,
            offset,
            hasNext,
        )
    }

fun Mono<DataFrame>.toJsonFormat(): Mono<QueryResult.OutputFormat> = map { it.toJsonFormat() }

fun DataFrame.toJsonFormat(): QueryResult.JsonFormat {
    val data = createJsonFormat()
    return QueryResult.JsonFormat(
        data.meta,
        data.rows,
        data.rows.size,
        data.rows.size,
        QueryResult.Statistics(0.0, 0, 0),
        stats,
        data.offset,
        data.hasNext,
    )
}

fun DataFrame.toNamedJsonFormat(name: String): QueryResult.NamedJsonFormat {
    val data = createJsonFormat()
    return QueryResult.NamedJsonFormat(
        name,
        data.meta,
        data.rows,
        data.rows.size,
        data.rows.size,
        QueryResult.Statistics(0.0, 0, 0),
        stats,
        data.offset,
        data.hasNext,
    )
}

private fun DataFrame.createJsonFormat(): JsonFormatData {
    val rows = toRowWithSchema().map { it.toMap() }
    val meta = schema.fields.map { QueryResult.Meta(it.name, it.type.name) }
    val offset = offsets.singleOrNull()
    val hasNext = offset?.let { hasNext.singleOrNull() ?: false } ?: false
    return JsonFormatData(meta, rows, offset, hasNext)
}

private data class JsonFormatData(
    val meta: List<QueryResult.Meta>,
    val rows: List<Map<String, Any?>>,
    val offset: String?,
    val hasNext: Boolean,
)

fun Mono<DataFrame>.toOutputFormat(format: String?): Mono<QueryResult.OutputFormat> =
    when (format) {
        "jsoncompact" -> toJsonCompactFormat()
        else -> toJsonFormat()
    }
