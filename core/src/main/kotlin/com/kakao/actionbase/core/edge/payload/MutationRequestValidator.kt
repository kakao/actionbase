package com.kakao.actionbase.core.edge.payload

import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.core.state.EventType

/**
 * Validates payload-deterministic nullability constraints against the schema.
 *
 * Rules by event type:
 * - INSERT: every non-nullable field must be present and non-null.
 * - UPDATE: every non-nullable field, if present in the payload, must be non-null.
 *   Absent fields are legal (they retain their existing value).
 * - DELETE: no property validation (tombstone semantics).
 *
 * Throws [IllegalArgumentException] on violation so the global exception handler maps it to 400.
 */
internal fun checkNonNullableConstraints(
    type: EventType,
    properties: List<StructField>,
    payload: Map<String, Any?>,
) {
    if (type == EventType.DELETE) return
    for (field in properties) {
        if (field.nullable) continue
        if (!payload.containsKey(field.name)) {
            require(type == EventType.UPDATE) {
                "Property '${field.name}' is required and cannot be null"
            }
        } else {
            require(payload[field.name] != null) {
                "Property '${field.name}' cannot be null"
            }
        }
    }
}
