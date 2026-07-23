package com.kakao.actionbase.core.state

import com.kakao.actionbase.core.state.EventType
import com.kakao.actionbase.core.state.SpecialStateValue

fun State.transit(
    event: Event,
    fields: AbstractSchema,
    insertMerge: Boolean = false,
): State {
    val nextProperties = transitProperties(properties, event, fields, insertMerge)

    val shouldOverride = event.version == version
    val nextVersion = maxOf(event.version, version)

    var nextCreatedAt = createdAt
    var nextDeletedAt = deletedAt

    when (event.type) {
        EventType.INSERT -> {
            // Update createdAt for INSERT events
            if (nextCreatedAt == null || event.version >= nextCreatedAt) {
                nextCreatedAt = event.version
                if (shouldOverride && nextCreatedAt == nextDeletedAt) {
                    nextDeletedAt = null
                }
            }
        }
        EventType.DELETE -> {
            // Update deletedAt for DELETE events
            if (nextDeletedAt == null || event.version >= nextDeletedAt) {
                nextDeletedAt = event.version
                if (shouldOverride && nextDeletedAt == nextCreatedAt) {
                    nextCreatedAt = null
                }
            }
        }
        EventType.UPDATE -> {}
    }

    // active=true only when createdAt is greater than deletedAt (when the most recent event is INSERT)
    val nextActive = nextCreatedAt != null && (nextDeletedAt == null || nextCreatedAt > nextDeletedAt)

    val nextState =
        State(
            nextActive,
            nextVersion,
            nextCreatedAt,
            nextDeletedAt,
            nextProperties,
        )

    nextState.checkValid(fields, insertMerge)

    return nextState
}

//
// /**
// * Branches logic to process properties according to event type.
// */
private fun transitProperties(
    currentProperties: Map<String, StateValue>,
    event: Event,
    fields: AbstractSchema,
    insertMerge: Boolean,
): Map<String, StateValue> =
    when (event.type) {
        // INSERT_MERGE: INSERT merges like UPDATE (omitted kept, null clears); default snapshots (omitted -> UNSET).
        EventType.INSERT ->
            if (insertMerge) {
                processUpdateOperation(currentProperties, event, fields)
            } else {
                processInsertOperation(currentProperties, event, fields)
            }
        EventType.UPDATE -> processUpdateOperation(currentProperties, event, fields)
        EventType.DELETE -> processDeleteOperation(currentProperties, event, fields)
    }

/**
 * Process INSERT operation: Creates a new entry or updates an existing entry based on event time.
 */
private fun processInsertOperation(
    currentProperties: Map<String, StateValue>,
    event: Event,
    fields: AbstractSchema,
): Map<String, StateValue> =
    fields.names.associateWith { fieldName ->
        val currentValue = currentProperties[fieldName]
        val eventValue = event.properties[fieldName]
        when {
            currentValue == null -> {
                // Use event value when no existing state value
                eventValue?.let { StateValue(event.version, it) }
                    ?: StateValue.unsetOf(event.version)
            }
            event.version < currentValue.version -> {
                // Keep existing state value when event version is older
                currentValue
            }
            eventValue != null -> {
                // Update if event value exists
                StateValue(event.version, eventValue)
            }
            event.version == currentValue.version && currentValue.isPresent() -> {
                // Keep existing value when same version and current value exists
                currentValue
            }
            else -> {
                // Mark as UNSET when field is not specified in INSERT
                StateValue.unsetOf(event.version)
            }
        }
    }

/**
 * Process UPDATE operation: Updates an existing entry based on event time.
 */
private fun processUpdateOperation(
    currentProperties: Map<String, StateValue>,
    event: Event,
    fields: AbstractSchema,
): Map<String, StateValue> =
    fields.names
        .mapNotNull { fieldName ->
            val currentValue = currentProperties[fieldName]
            val eventValue = event.properties[fieldName]
            val fieldExistsInEvent = event.properties.containsKey(fieldName)

            val nextValue =
                when {
                    currentValue != null -> {
                        // When existing state value exists
                        when {
                            event.version < currentValue.version -> {
                                // Keep existing value when event version is older
                                currentValue
                            }
                            !fieldExistsInEvent -> {
                                // Keep existing value when field is not included in UPDATE
                                currentValue
                            }
                            eventValue != null -> {
                                // Update if event has value
                                StateValue(event.version, eventValue)
                            }
                            else -> {
                                // Mark as UNSET when field is explicitly set to null in UPDATE
                                StateValue.unsetOf(event.version)
                            }
                        }
                    }
                    fieldExistsInEvent -> {
                        // When no existing state value and field is included in event
                        if (eventValue != null) {
                            StateValue(event.version, eventValue)
                        } else {
                            StateValue.unsetOf(event.version)
                        }
                    }
                    else -> {
                        // Exclude when neither existing value nor field in event
                        null
                    }
                }

            nextValue?.let { fieldName to it }
        }.toMap()

/**
 * Process DELETE operation: Marks an existing entry as DELETED based on event time.
 */
private fun processDeleteOperation(
    currentProperties: Map<String, StateValue>,
    event: Event,
    fields: AbstractSchema,
): Map<String, StateValue> =
    fields.names.associateWith { fieldName ->
        val currentValue = currentProperties[fieldName]

        when {
            currentValue != null && event.version < currentValue.version -> {
                // Keep existing value when event version is older
                currentValue
            }
            else -> {
                // Mark as DELETED when no existing value or event version is newer
                StateValue.deletedOf(event.version)
            }
        }
    }

private fun State.checkValid(
    fields: AbstractSchema,
    insertMerge: Boolean = false,
) {
    val createdAt = createdAt
    val deletedAt = deletedAt

    // Validate active state
    val shouldBeActive =
        when {
            createdAt == null -> false
            deletedAt == null -> true
            else -> createdAt > deletedAt
        }

    // Field validation
    if (active) {
        require(createdAt != null) {
            "createdAt must be provided when active is true"
        }

        fields.names.forEach { fieldName ->
            val entry = properties[fieldName]
            val isInvalid =
                when {
                    entry == null || entry.value == null -> true
                    entry.value is String && SpecialStateValue.isSpecialStateValue(entry.value) -> true
                    else -> false
                }

            if (isInvalid && !fields.isNullable(fieldName)) {
                val message = "$fieldName must be provided"
                // Under INSERT_MERGE an activating merge can leave a non-nullable field unset —
                // permanent, so surfaced as INVALID. The default path keeps its original exception.
                throw if (insertMerge) InvalidMutationException(message) else IllegalArgumentException(message)
            }
        }

        // Verify that active state matches shouldBeActive
        require(active == shouldBeActive) {
            "active state is inconsistent with timestamps: active=$active, createdAt=$createdAt, deletedAt=$deletedAt"
        }
    }
}
