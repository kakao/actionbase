package com.kakao.actionbase.core.edge.mutation

import com.kakao.actionbase.core.codec.XXHash32Wrapper
import com.kakao.actionbase.core.edge.record.EdgeCountRecord
import com.kakao.actionbase.core.edge.record.EdgeGroupRecord
import com.kakao.actionbase.core.edge.record.EdgeIndexRecord
import com.kakao.actionbase.core.edge.record.EdgeStateRecord
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.GroupType
import com.kakao.actionbase.core.metadata.common.Index
import com.kakao.actionbase.core.metadata.common.SystemProperties
import com.kakao.actionbase.core.state.AbstractSchema
import com.kakao.actionbase.core.types.PrimitiveType

object EdgeMutationBuilder {
    const val MULTI_EDGE_ID_FIELD_NAME = "id"
    const val MULTI_EDGE_SOURCE_FIELD_NAME = "_source"
    const val MULTI_EDGE_TARGET_FIELD_NAME = "_target"

    val MULTI_EDGE_ID_CODE = XXHash32Wrapper.default.stringHash(MULTI_EDGE_ID_FIELD_NAME)
    val MULTI_EDGE_SOURCE_CODE = XXHash32Wrapper.default.stringHash(MULTI_EDGE_SOURCE_FIELD_NAME)
    val MULTI_EDGE_TARGET_CODE = XXHash32Wrapper.default.stringHash(MULTI_EDGE_TARGET_FIELD_NAME)

    fun build(
        before: EdgeStateRecord,
        after: EdgeStateRecord,
        directionType: DirectionType,
        indexes: List<Index>,
        groups: List<Group>,
    ): EdgeMutationRecords = buildWith(EdgeMutationStrategy.Edge, before, after, directionType, indexes, groups)

    fun buildForMultiEdge(
        before: EdgeStateRecord,
        after: EdgeStateRecord,
        directionType: DirectionType,
        indexes: List<Index>,
        groups: List<Group>,
    ): EdgeMutationRecords = buildWith(EdgeMutationStrategy.MultiEdge, before, after, directionType, indexes, groups)

    private fun buildWith(
        strategy: EdgeMutationStrategy,
        before: EdgeStateRecord,
        after: EdgeStateRecord,
        directionType: DirectionType,
        indexes: List<Index>,
        groups: List<Group>,
    ): EdgeMutationRecords {
        val beforeActive = before.value.active
        val afterActive = after.value.active

        var createIndexRecords: List<EdgeIndexRecord> = emptyList()
        var deleteIndexRecordKeys: List<EdgeIndexRecord.Key> = emptyList()
        var countRecords: List<EdgeCountRecord> = emptyList()
        var groupRecords: List<EdgeGroupRecord> = emptyList()

        val (status, acc) =
            when {
                before == after -> {
                    "IDLE" to 0L
                }

                !beforeActive && afterActive -> {
                    createIndexRecords = buildIndexRecords(strategy, after, directionType, indexes)
                    countRecords = buildCountRecords(strategy, after, directionType, 1L)
                    groupRecords = buildGroupRecords(strategy, after, groups, 1L)
                    "CREATED" to 1L
                }

                beforeActive && !afterActive -> {
                    val countSource = strategy.countRecordOnDelete(before, after)
                    deleteIndexRecordKeys = buildIndexRecords(strategy, before, directionType, indexes).map { it.key }
                    countRecords = buildCountRecords(strategy, countSource, directionType, -1L)
                    groupRecords = buildGroupRecords(strategy, before, groups, -1L)
                    "DELETED" to -1L
                }

                beforeActive && afterActive -> {
                    createIndexRecords = buildIndexRecords(strategy, after, directionType, indexes)

                    val willBeUpdated = createIndexRecords.map { it.key }.toSet()

                    deleteIndexRecordKeys =
                        buildIndexRecords(strategy, before, directionType, indexes)
                            .map { it.key }
                            .filter { it !in willBeUpdated }

                    countRecords =
                        strategy.countRecordsOnUpdate(before, after, directionType) { record, dt, acc ->
                            buildCountRecords(strategy, record, dt, acc)
                        }

                    val decrementGroupRecords = buildGroupRecords(strategy, before, groups, -1L)
                    val incrementGroupRecords = buildGroupRecords(strategy, after, groups, 1L)
                    groupRecords = decrementGroupRecords + incrementGroupRecords

                    "UPDATED" to 0L
                }

                else -> {
                    "IDLE" to 0L
                }
            }

        return EdgeMutationRecords(
            status = status,
            acc = acc,
            stateRecord = after,
            createIndexRecords = createIndexRecords,
            deleteIndexRecordKeys = deleteIndexRecordKeys,
            countRecords = countRecords,
            groupRecords = groupRecords,
        )
    }

    private fun buildIndexRecords(
        strategy: EdgeMutationStrategy,
        record: EdgeStateRecord,
        directionType: DirectionType,
        indexes: List<Index>,
    ): List<EdgeIndexRecord> {
        val properties: Map<Int, Any?> = record.value.properties.mapValues { (_, stateValue) -> stateValue.value }

        val directions = directionType.directions()
        return indexes.flatMap { index ->
            directions.map { direction ->
                val indexValues =
                    index.fields.map { field ->
                        val value = record.indexValueOf(properties, field.field)
                        EdgeIndexRecord.Key.IndexValue(
                            value = value,
                            order = field.order,
                        )
                    }
                EdgeIndexRecord(
                    key =
                        EdgeIndexRecord.Key(
                            prefix =
                                EdgeIndexRecord.Key.Prefix.of(
                                    tableCode = record.key.tableCode,
                                    directedSource = strategy.directedSource(record, direction),
                                    direction = direction,
                                    indexCode = index.code,
                                    indexValues = indexValues,
                                ),
                            suffix =
                                EdgeIndexRecord.Key.Suffix(
                                    restIndexValues = emptyList(),
                                    directedTarget = strategy.directedTarget(record, direction),
                                ),
                        ),
                    value =
                        EdgeIndexRecord.Value(
                            version = record.value.version,
                            properties = properties,
                        ),
                )
            }
        }
    }

    private fun buildCountRecords(
        strategy: EdgeMutationStrategy,
        record: EdgeStateRecord,
        directionType: DirectionType,
        accumulator: Long,
    ): List<EdgeCountRecord> {
        val directions = directionType.directions()
        return directions.map { direction ->
            EdgeCountRecord(
                key =
                    EdgeCountRecord.Key.of(
                        directedSource = strategy.directedSource(record, direction),
                        tableCode = record.key.tableCode,
                        direction = direction,
                    ),
                value = accumulator,
            )
        }
    }

    private fun buildGroupRecords(
        strategy: EdgeMutationStrategy,
        record: EdgeStateRecord,
        groups: List<Group>,
        weight: Long,
    ): List<EdgeGroupRecord> {
        val properties: Map<Int, Any?> = record.value.properties.mapValues { (_, stateValue) -> stateValue.value }

        return groups.flatMap { group ->
            group.directionType.directions().map { direction ->
                val value =
                    when (group.type) {
                        GroupType.COUNT -> 1L
                        GroupType.SUM -> {
                            val anyValue = record.indexValueOf(properties, group.valueField)
                            requireNotNull(anyValue) { "The value field ${group.valueField} is not found." }
                            PrimitiveType.LONG.cast(anyValue) as Long
                        }
                    }
                val groupValues =
                    group.fields.map { field ->
                        val value = record.indexValueOf(properties, field.name)
                        if (field.bucket != null) {
                            field.bucket.apply(value)
                        } else {
                            value
                        }
                    }
                EdgeGroupRecord(
                    key =
                        EdgeGroupRecord.Key.of(
                            directedSource = strategy.directedSource(record, direction),
                            tableCode = record.key.tableCode,
                            direction = direction,
                            groupCode = group.code,
                        ),
                    qualifier = EdgeGroupRecord.Qualifier(groupValues),
                    value = weight * value,
                )
            }
        }
    }

    fun EdgeStateRecord.indexValueOf(
        properties: Map<Int, Any?>,
        name: String,
    ): Any? {
        val systemProperty = SystemProperties.getOrNull(name)
        return when (systemProperty) {
            SystemProperties.VERSION -> value.version
            SystemProperties.SOURCE -> key.source
            SystemProperties.TARGET -> key.target
            else -> {
                properties[AbstractSchema.codeOf(name)]
            }
        }
    }
}
