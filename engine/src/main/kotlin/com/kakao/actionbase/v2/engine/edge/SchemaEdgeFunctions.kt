package com.kakao.actionbase.v2.engine.edge

import com.kakao.actionbase.v2.core.code.hbase.ValueUtils
import com.kakao.actionbase.v2.core.edge.SchemaEdge
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.core.metadata.SystemProperties
import com.kakao.actionbase.v2.engine.sql.Row

private val SOURCE_CODE_AS_STRING = ValueUtils.stringHash("_source").toString()
private val TARGET_CODE_AS_STRING = ValueUtils.stringHash("_target").toString()

fun SchemaEdge.toRow(
    withAll: Boolean,
    isMultiEdge: Boolean = false,
): Row {
    val numFields =
        if (withAll) {
            schema.allStructType.size
        } else {
            schema.structType.size
        }
    val array = arrayOfNulls<Any>(numFields)

    if (withAll) {
        array[schema.activeIndex] = isActive
    }

    if (isMultiEdge) {
        // src = directedSource
        // tgt = id
        // the v2 result of src and tgt is flipped in v3.
        array[schema.dirIndex] = dir.name
        array[schema.tsIndex] = ts
        if (dir == Direction.OUT) {
            array[schema.srcIndex] = rawData[SOURCE_CODE_AS_STRING]?.value!!
            array[schema.tgtIndex] = rawData[TARGET_CODE_AS_STRING]?.value!!
        } else {
            array[schema.srcIndex] = rawData[TARGET_CODE_AS_STRING]?.value!!
            array[schema.tgtIndex] = rawData[SOURCE_CODE_AS_STRING]?.value!!
        }
    } else {
        array[schema.dirIndex] = dir.name
        array[schema.tsIndex] = ts
        array[schema.srcIndex] = src
        array[schema.tgtIndex] = tgt
    }

    // Iterate over properties and assign values to the corresponding indices.
    props.withIndex().forEach { (index, value) ->
        val fieldName = schema.fields[index].name
        if (!SystemProperties.isSystemProperty(fieldName)) {
            schema.getFieldIndex(fieldName)?.let { fieldIndex ->
                if (isMultiEdge && fieldName == "_id") {
                    array[fieldIndex] = tgt
                } else {
                    array[fieldIndex] = value
                }
            }
        }
    }
    return Row(array)
}

fun SchemaEdge.toRowWithOffset(
    offset: String,
    isMultiEdge: Boolean = false,
): Row {
    val numFields = schema.offsetStructType.size
    val array = arrayOfNulls<Any>(numFields)

    if (isMultiEdge) {
        array[schema.dirIndex] = dir.name
        array[schema.tsIndex] = ts
        array[schema.srcIndex] = src
        if (dir == Direction.OUT) {
            array[schema.tgtIndex] = rawData[TARGET_CODE_AS_STRING]?.value!!
        } else {
            array[schema.tgtIndex] = rawData[SOURCE_CODE_AS_STRING]?.value!!
        }
        array[schema.offsetIndex] = offset
    } else {
        array[schema.dirIndex] = dir.name
        array[schema.tsIndex] = ts
        array[schema.srcIndex] = src
        array[schema.tgtIndex] = tgt
        array[schema.offsetIndex] = offset
    }

    // Iterate over properties and assign values to the corresponding indices.
    props.withIndex().forEach { (index, value) ->
        val fieldName = schema.fields[index].name
        if (!SystemProperties.isSystemProperty(fieldName)) {
            schema.getFieldIndex(fieldName)?.let { fieldIndex ->
                if (isMultiEdge && fieldName == "_id") {
                    array[fieldIndex] = tgt
                } else {
                    array[fieldIndex] = value
                }
            }
        }
    }
    return Row(array)
}
