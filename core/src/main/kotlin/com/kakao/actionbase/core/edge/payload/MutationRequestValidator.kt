package com.kakao.actionbase.core.edge.payload

import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.core.state.EventType

/**
 * Validates payload nullability against the schema (throws [IllegalArgumentException] -> 400):
 * INSERT (snapshot) requires every non-nullable field present and non-null; INSERT_MERGE and
 * UPDATE only require present non-nullable fields to be non-null (row completeness is checked
 * later in `State.transit`); DELETE validates nothing.
 */
internal fun checkNonNullableFields(
    eventType: EventType,
    properties: List<StructField>,
    payload: Map<String, Any?>,
    insertMerge: Boolean = false,
) {
    if (eventType == EventType.INSERT && !insertMerge) {
        for (field in properties) {
            if (field.nullable) continue
            require(payload.containsKey(field.name)) {
                "Property '${field.name}' is required and cannot be null"
            }
            require(payload[field.name] != null) {
                "Property '${field.name}' cannot be null"
            }
        }
    } else if (eventType == EventType.INSERT || eventType == EventType.UPDATE) {
        for (field in properties) {
            if (field.nullable) continue
            if (payload.containsKey(field.name)) {
                require(payload[field.name] != null) {
                    "Property '${field.name}' cannot be null"
                }
            }
        }
    }
    // DELETE: no property validation (tombstone semantics)
}
