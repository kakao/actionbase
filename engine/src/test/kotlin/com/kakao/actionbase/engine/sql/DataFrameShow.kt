package com.kakao.actionbase.engine.sql

import com.kakao.actionbase.core.types.PrimitiveType

/**
 * Renders a [DataFrame] as a bordered table, the way `spark-shell` shows one. Types sit under the
 * column names — a `?` marks a nullable field — because a transform that changes a column's type is
 * as interesting as one that changes its values.
 *
 * Test-scope on purpose. Move it next to [DataFrame] once something in main wants it.
 */
fun DataFrame.show(name: String): DataFrame {
    println(render(name))
    return this
}

fun DataFrame.render(name: String): String {
    val header = "=== $name === ${describe()}"
    if (schema.fields.isEmpty()) {
        return "$header\n(no columns)\n"
    }

    val names = schema.fields.map { it.name }
    val types = schema.fields.map { field -> field.type.name + if (field.nullable) "?" else "" }
    val cells = rows.map { row -> schema.fields.map { field -> row.data[field.name].toCell() } }
    val widths = names.indices.map { column -> (cells.map { it[column].length } + names[column].length + types[column].length).max() }
    val alignRight = schema.fields.map { it.type.isNumeric() }
    val border = widths.joinToString(separator = "+", prefix = "+", postfix = "+") { "-".repeat(it + 2) }

    return (
        listOf(header, border, names.toLine(widths, alignRight), types.toLine(widths, alignRight), border) +
            cells.map { it.toLine(widths, alignRight) } +
            border
    ).joinToString(separator = "\n", postfix = "\n")
}

private fun DataFrame.describe(): String =
    listOfNotNull(
        "$count of $total rows",
        offset?.let { "offset=$it" },
        "hasNext=$hasNext".takeIf { hasNext },
    ).joinToString()

private fun List<String>.toLine(
    widths: List<Int>,
    alignRight: List<Boolean>,
): String =
    mapIndexed { column, cell ->
        if (alignRight[column]) cell.padStart(widths[column]) else cell.padEnd(widths[column])
    }.joinToString(separator = " | ", prefix = "| ", postfix = " |")

private fun Any?.toCell(): String = this?.toString() ?: "NULL"

private fun PrimitiveType.isNumeric(): Boolean = this in setOf(PrimitiveType.BYTE, PrimitiveType.SHORT, PrimitiveType.INT, PrimitiveType.LONG, PrimitiveType.FLOAT, PrimitiveType.DOUBLE)
