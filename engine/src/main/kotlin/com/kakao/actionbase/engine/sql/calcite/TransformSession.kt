package com.kakao.actionbase.engine.sql.calcite

import com.kakao.actionbase.core.metadata.common.StructType
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.engine.sql.DataFrame
import com.kakao.actionbase.engine.sql.Row

import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.Properties

import org.apache.calcite.DataContext
import org.apache.calcite.jdbc.CalciteConnection
import org.apache.calcite.linq4j.Enumerable
import org.apache.calcite.linq4j.Linq4j
import org.apache.calcite.rel.type.RelDataType
import org.apache.calcite.rel.type.RelDataTypeFactory
import org.apache.calcite.schema.ScannableTable
import org.apache.calcite.schema.impl.AbstractTable

/**
 * One connection, its frame slots, and the [PreparedStatement] the transform was prepared into.
 *
 * Leased for one execution and never shared, which is what makes writing a request's frames into the
 * slots safe.
 */
internal class TransformSession(
    sql: String,
    schemas: Map<String, StructType>,
) : AutoCloseable {
    private val connection: Connection = DriverManager.getConnection("jdbc:calcite:", CONNECTION_PROPERTIES)
    private val slots: Map<String, FrameSlot> = schemas.mapValues { (_, schema) -> FrameSlot(schema) }
    private val statement: PreparedStatement

    /** The columns the statement returns, taken from its metadata rather than from a first execution. */
    val schema: StructType

    /** How many `?` placeholders the SQL carries. */
    val parameterCount: Int

    init {
        val rootSchema = connection.unwrap(CalciteConnection::class.java).rootSchema
        slots.forEach { (name, slot) -> rootSchema.add(name, slot) }

        statement = connection.prepareStatement(sql)
        schema = statement.metaData.toStructType()
        parameterCount = statement.parameterMetaData.parameterCount
    }

    fun execute(
        frames: Map<String, DataFrame>,
        arguments: List<Any?>,
    ): DataFrame =
        try {
            slots.forEach { (name, slot) -> slot.frame = frames.getValue(name) }
            arguments.forEachIndexed { index, argument -> statement.setObject(index + 1, argument) }
            statement.executeQuery().use { it.toDataFrame(schema) }
        } finally {
            // Slots outlive the execution; holding the frames would keep a request's rows reachable.
            slots.forEach { (_, slot) -> slot.frame = null }
        }

    override fun close() {
        statement.close()
        connection.close()
    }

    private companion object {
        val CONNECTION_PROPERTIES =
            Properties().apply {
                // Keeps unquoted identifiers as written, so `hop1.paidAt` is not `PAIDAT`.
                setProperty("lex", "JAVA")
                // The standard operator table has no `IFNULL`; the dialect libraries do.
                setProperty("fun", "all")
            }
    }
}

/** Columns fixed at prepare time, rows written just before each execution by the lease holder alone. */
private class FrameSlot(
    private val schema: StructType,
) : AbstractTable(),
    ScannableTable {
    var frame: DataFrame? = null

    override fun getRowType(typeFactory: RelDataTypeFactory): RelDataType = schema.toRelDataType(typeFactory)

    override fun scan(root: DataContext): Enumerable<Array<Any?>> {
        val frame = checkNotNull(frame) { "A transform was executed without rows for one of its inputs." }
        require(frame.schema == schema) {
            "This transform was prepared for ${schema.fields.map { it.name }} but was given ${frame.schema.fields.map { it.name }}."
        }

        val fields = schema.fields
        return Linq4j.asEnumerable(
            frame.rows.map { row ->
                Array<Any?>(fields.size) { column ->
                    val value = row.data[fields[column].name]
                    if (fields[column].type == PrimitiveType.OBJECT) value?.toString() else value
                }
            },
        )
    }
}

private fun ResultSet.toDataFrame(schema: StructType): DataFrame {
    val fields = schema.fields
    val rows = mutableListOf<Row>()
    while (next()) {
        rows += Row(fields.mapIndexed { index, field -> field.name to getObject(index + 1)?.let { field.type.cast(it) } }.toMap(), schema)
    }
    return DataFrame(rows, schema, total = rows.size.toLong())
}
