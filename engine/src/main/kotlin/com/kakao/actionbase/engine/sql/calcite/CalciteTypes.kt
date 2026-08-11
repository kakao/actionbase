package com.kakao.actionbase.engine.sql.calcite

import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.core.metadata.common.StructType
import com.kakao.actionbase.core.types.PrimitiveType

import java.sql.ResultSetMetaData
import java.sql.Types

import org.apache.calcite.rel.type.RelDataType
import org.apache.calcite.rel.type.RelDataTypeFactory
import org.apache.calcite.sql.type.SqlTypeName

internal fun StructType.toRelDataType(typeFactory: RelDataTypeFactory): RelDataType =
    typeFactory
        .builder()
        .also { builder ->
            fields.forEach { field ->
                builder.add(field.name, typeFactory.createTypeWithNullability(typeFactory.createSqlType(field.type.toSqlTypeName()), field.nullable))
            }
        }.build()

/** The output schema of a prepared statement, which Calcite knows before the statement is executed. */
internal fun ResultSetMetaData.toStructType(): StructType =
    StructType(
        (1..columnCount).map { column ->
            StructField(
                name = getColumnLabel(column),
                type = getColumnType(column).toPrimitiveType(),
                comment = "",
                nullable = isNullable(column) != ResultSetMetaData.columnNoNulls,
            )
        },
    )

private fun PrimitiveType.toSqlTypeName(): SqlTypeName =
    when (this) {
        PrimitiveType.BOOLEAN -> SqlTypeName.BOOLEAN
        PrimitiveType.BYTE -> SqlTypeName.TINYINT
        PrimitiveType.SHORT -> SqlTypeName.SMALLINT
        PrimitiveType.INT -> SqlTypeName.INTEGER
        PrimitiveType.LONG -> SqlTypeName.BIGINT
        PrimitiveType.FLOAT -> SqlTypeName.REAL
        PrimitiveType.DOUBLE -> SqlTypeName.DOUBLE
        // A JSON node has no SQL type. It is carried as text, so `JSON_VALUE` and friends can reach
        // into it and a projection can hand it back unchanged.
        PrimitiveType.STRING, PrimitiveType.OBJECT -> SqlTypeName.VARCHAR
    }

private fun Int.toPrimitiveType(): PrimitiveType =
    when (this) {
        Types.BOOLEAN -> PrimitiveType.BOOLEAN
        Types.TINYINT -> PrimitiveType.BYTE
        Types.SMALLINT -> PrimitiveType.SHORT
        Types.INTEGER -> PrimitiveType.INT
        Types.BIGINT -> PrimitiveType.LONG
        Types.REAL -> PrimitiveType.FLOAT
        // Calcite's FLOAT is eight bytes wide, the same as DOUBLE.
        Types.FLOAT, Types.DOUBLE, Types.DECIMAL, Types.NUMERIC -> PrimitiveType.DOUBLE
        Types.CHAR, Types.VARCHAR -> PrimitiveType.STRING
        else -> throw IllegalArgumentException("A transform cannot return JDBC type $this; a DataFrame has no matching type.")
    }
