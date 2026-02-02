package com.kakao.actionbase.core.java.state.base;

import com.kakao.actionbase.core.java.state.Event;
import com.kakao.actionbase.core.java.state.EventType;
import com.kakao.actionbase.core.java.state.ImmutableStateValue;
import com.kakao.actionbase.core.java.state.SpecialStateValue;
import com.kakao.actionbase.core.java.state.State;
import com.kakao.actionbase.core.java.state.StateValue;
import com.kakao.actionbase.core.java.types.StructType;

import java.util.HashMap;
import java.util.Map;

/**
 * The Merge class provides functionality to merge VersionObjects. It ensures consistent results in
 * situations where event time order and processing order may differ.
 */
public class StateCompanion {

  private StateCompanion() {}

  public static State initialOf(StructType schema) {
    return ImmutableBaseState.builder().active(false).checkAndBuild(schema);
  }

  public static State transit(State state, Event event, StructType schema) {
    Map<String, StateValue> nextProperties = transitProperties(state.properties(), event, schema);

    boolean shouldOverride = event.version() == state.version();
    long nextVersion = Math.max(event.version(), state.version());

    Long nextCreatedAt = state.createdAt();
    Long nextDeletedAt = state.deletedAt();

    // Timestamp processing
    if (event.type() == EventType.INSERT) {
      // Update createdAt for INSERT, UPDATE events
      if (nextCreatedAt == null || event.version() >= nextCreatedAt) {
        nextCreatedAt = event.version();
        if (shouldOverride && nextCreatedAt.equals(nextDeletedAt)) {
          nextDeletedAt = null;
        }
      }
    } else if (event.type() == EventType.DELETE) {
      // Update deletedAt for DELETE events
      if (nextDeletedAt == null || event.version() >= nextDeletedAt) {
        nextDeletedAt = event.version();
        if (shouldOverride && nextDeletedAt.equals(nextCreatedAt)) {
          nextCreatedAt = null;
        }
      }
    }

    // Calculate active state (based on event time)
    // active=true only when createdAt is greater than deletedAt (when the most recent event is
    // INSERT)
    boolean nextActive =
        (nextCreatedAt != null) && (nextDeletedAt == null || nextCreatedAt > nextDeletedAt);

    return ImmutableBaseState.builder()
        .active(nextActive)
        .version(nextVersion)
        .properties(nextProperties)
        .createdAt(nextCreatedAt)
        .deletedAt(nextDeletedAt)
        .checkAndBuild(schema);
  }

  /** Processes all entries. */
  private static Map<String, StateValue> transitProperties(
      Map<String, StateValue> currentProperties, Event event, StructType schema) {
    if (event.type() == EventType.INSERT) {
      return processInsertOperation(currentProperties, event, schema);
    } else if (event.type() == EventType.UPDATE) {
      return processUpdateOperation(currentProperties, event, schema);
    } else if (event.type() == EventType.DELETE) {
      return processDeleteOperation(currentProperties, event, schema);
    } else {
      throw new IllegalArgumentException(
          "Unsupported event type: " + event.type() + " for event: " + event);
    }
  }

  /**
   * Processes INSERT operation. INSERT creates a new entry or updates an existing entry based on
   * event time.
   */
  private static Map<String, StateValue> processInsertOperation(
      Map<String, StateValue> currentProperties, Event event, StructType schema) {
    Map<String, StateValue> nextProperties = new HashMap<>();

    for (String fieldName : schema.fieldNames()) {
      StateValue currentValue = currentProperties.get(fieldName);
      Object eventValue = event.properties().get(fieldName);

      if (currentValue != null) {
        // When existing state value exists
        if (event.version() >= currentValue.version()) {
          // Update only when event version is newer
          if (eventValue != null) {
            nextProperties.put(
                fieldName,
                ImmutableStateValue.builder().value(eventValue).version(event.version()).build());
          } else {
            if (event.version() == currentValue.version() && currentValue.isPresent()) {
              // When same version and current value exists, keep existing value
              nextProperties.put(fieldName, currentValue);
            } else {
              // Mark as UNSET when field is not specified in INSERT
              nextProperties.put(fieldName, StateValue.unsetOf(event.version()));
            }
          }
        } else {
          // Keep existing value when event version is same or older
          nextProperties.put(fieldName, currentValue);
        }
      } else {
        // When existing state value does not exist
        if (eventValue != null) {
          nextProperties.put(
              fieldName,
              ImmutableStateValue.builder().value(eventValue).version(event.version()).build());
        } else {
          // Mark as UNSET when field is not specified in INSERT
          nextProperties.put(
              fieldName,
              ImmutableStateValue.builder()
                  .value(SpecialStateValue.UNSET.code())
                  .version(event.version())
                  .build());
        }
      }
    }
    return nextProperties;
  }

  /** Processes UPDATE operation. UPDATE updates an existing entry based on event time. */
  private static Map<String, StateValue> processUpdateOperation(
      Map<String, StateValue> currentProperties, Event event, StructType schema) {
    Map<String, StateValue> nextProperties = new HashMap<>();

    for (String fieldName : schema.fieldNames()) {
      StateValue currentStateValue = currentProperties.get(fieldName);
      Object eventValue = event.properties().getOrDefault(fieldName, null);

      if (currentStateValue != null) {
        // When existing state value exists
        if (event.version() >= currentStateValue.version()) {
          // Update only when event version is newer
          if (event.properties().containsKey(fieldName)) {
            if (eventValue != null) {
              nextProperties.put(
                  fieldName,
                  ImmutableStateValue.builder().value(eventValue).version(event.version()).build());
            } else {
              // Mark as UNSET when field is explicitly set to null in UPDATE
              nextProperties.put(
                  fieldName,
                  ImmutableStateValue.builder()
                      .value(SpecialStateValue.UNSET.code())
                      .version(event.version())
                      .build());
            }
          } else {
            // Keep existing value when field is not included in UPDATE
            nextProperties.put(fieldName, currentStateValue);
          }
        } else {
          // Keep existing value when event version is same or older
          nextProperties.put(fieldName, currentStateValue);
        }
      } else {
        // When existing state value does not exist
        if (event.properties().containsKey(fieldName)) {
          if (eventValue != null) {
            // Set if value exists in event
            nextProperties.put(
                fieldName,
                ImmutableStateValue.builder().value(eventValue).version(event.version()).build());
          } else {
            // Set to UNSET if value is null in event
            nextProperties.put(
                fieldName,
                ImmutableStateValue.builder()
                    .value(SpecialStateValue.UNSET.code())
                    .version(event.version())
                    .build());
          }
        }
        // Keep as is (null) when neither existing value nor event field exists
      }
    }
    return nextProperties;
  }

  /** Processes DELETE operation. DELETE marks an existing entry as DELETED based on event time. */
  private static Map<String, StateValue> processDeleteOperation(
      Map<String, StateValue> currentProperties, Event event, StructType schema) {
    Map<String, StateValue> nextProperties = new HashMap<>();

    for (String fieldName : schema.fieldNames()) {
      StateValue stateValue = currentProperties.get(fieldName);

      if (stateValue != null) {
        // When existing state value exists
        if (event.version() >= stateValue.version()) {
          // Mark as DELETED only when event version is newer
          nextProperties.put(
              fieldName,
              ImmutableStateValue.builder()
                  .value(SpecialStateValue.DELETED.code())
                  .version(event.version())
                  .build());
        } else {
          // Keep existing value when event version is same or older
          nextProperties.put(fieldName, stateValue);
        }
      } else {
        // Mark as DELETED when existing state value does not exist
        nextProperties.put(
            fieldName,
            ImmutableStateValue.builder()
                .value(SpecialStateValue.DELETED.code())
                .version(event.version())
                .build());
      }
    }
    return nextProperties;
  }
}
