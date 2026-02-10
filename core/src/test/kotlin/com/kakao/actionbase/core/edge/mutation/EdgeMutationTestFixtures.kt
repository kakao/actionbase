package com.kakao.actionbase.core.edge.mutation

import com.kakao.actionbase.core.edge.record.EdgeStateRecord
import com.kakao.actionbase.core.state.StateValue

object EdgeMutationTestFixtures {
    const val TABLE_CODE = 100
    const val DEFAULT_VERSION = 1L

    fun edgeRecord(
        source: Any,
        target: Any,
        active: Boolean,
        version: Long = DEFAULT_VERSION,
        properties: Map<Int, StateValue> = emptyMap(),
    ): EdgeStateRecord =
        EdgeStateRecord(
            key = EdgeStateRecord.Key.of(source = source, tableCode = TABLE_CODE, target = target),
            value =
                EdgeStateRecord.Value(
                    active = active,
                    version = version,
                    createdAt = if (active) version else null,
                    deletedAt = if (!active && version > 0) version else null,
                    properties = properties,
                ),
        )

    fun multiEdgeRecord(
        id: Any,
        source: Any,
        target: Any,
        active: Boolean,
        version: Long = DEFAULT_VERSION,
        properties: Map<Int, StateValue> = emptyMap(),
    ): EdgeStateRecord {
        val multiEdgeProperties =
            mapOf(
                EdgeMutationBuilder.MULTI_EDGE_SOURCE_CODE to StateValue(version, source),
                EdgeMutationBuilder.MULTI_EDGE_TARGET_CODE to StateValue(version, target),
            ) + properties
        return EdgeStateRecord(
            key = EdgeStateRecord.Key.of(source = id, tableCode = TABLE_CODE, target = id),
            value =
                EdgeStateRecord.Value(
                    active = active,
                    version = version,
                    createdAt = if (active) version else null,
                    deletedAt = if (!active && version > 0) version else null,
                    properties = multiEdgeProperties,
                ),
        )
    }
}
