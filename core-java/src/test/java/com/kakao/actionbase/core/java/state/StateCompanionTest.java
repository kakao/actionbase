package com.kakao.actionbase.core.java.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kakao.actionbase.core.java.state.base.BaseEvent;
import com.kakao.actionbase.core.java.state.base.ImmutableBaseEvent;
import com.kakao.actionbase.core.java.state.base.ImmutableBaseState;
import com.kakao.actionbase.core.java.state.base.StateCompanion;
import com.kakao.actionbase.core.java.types.DataType;
import com.kakao.actionbase.core.java.types.ImmutableStructType;
import com.kakao.actionbase.core.java.types.StructType;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StateCompanionTest {

  private static final String NAME_FIELD = "name";
  private static final String AGE_FIELD = "age";
  private static final String ADDRESS_FIELD = "address";
  private static final String EMAIL_FIELD = "email";
  private static final String PHONE_FIELD = "phone";

  StructType schema;

  State initialState;

  @BeforeEach
  void setUp() {
    schema =
        ImmutableStructType.builder()
            .addField(NAME_FIELD, DataType.STRING, false)
            .addField(AGE_FIELD, DataType.STRING, false)
            .addField(ADDRESS_FIELD, DataType.STRING, true)
            .addField(EMAIL_FIELD, DataType.STRING, true)
            .addField(PHONE_FIELD, DataType.STRING, true)
            .build();

    initialState = StateCompanion.initialOf(schema);
  }

  @Nested
  @DisplayName("Domain Rules")
  class DomainRulesTests {

    @Test
    @DisplayName("Start with initial state when state cannot be found in storage")
    void testInitialState() {
      // given
      State storedState = null; // Assume state could not be retrieved from storage

      // when
      @SuppressWarnings("ConstantConditions")
      State state = storedState != null ? storedState : initialState;

      // then
      assertFalse(state.active());
      assertEquals(0, state.properties().size());
      assertNull(state.createdAt());
      assertNull(state.deletedAt());
    }

    @Test
    @DisplayName("Test event application pattern to initial state")
    void testEvent() {
      // given
      State storedState = null; // Assume state could not be retrieved from storage
      @SuppressWarnings("ConstantConditions")
      State currentState = storedState != null ? storedState : initialState;

      BaseEvent event =
          ImmutableBaseEvent.builder()
              .type(EventType.INSERT)
              .putProperties(NAME_FIELD, "Alice")
              .putProperties(AGE_FIELD, "30")
              .version(1000L)
              .build();

      // when
      State nextState = StateCompanion.transit(currentState, event, schema);

      // then
      assertTrue(nextState.active(), "State should be active after INSERT event is applied");
      assertEquals(1000L, nextState.createdAt(), "INSERT event version should be set as createdAt");
      assertNull(nextState.deletedAt(), "deletedAt should be null after INSERT event");

      // Verify that required fields are properly reflected in state
      assertEquals(
          "Alice",
          nextState.properties().get(NAME_FIELD).value(),
          "name field should be set to event value");
      assertEquals(
          "30",
          nextState.properties().get(AGE_FIELD).value(),
          "age field should be set to event value");
      assertEquals(
          1000L,
          nextState.properties().get(NAME_FIELD).version(),
          "name field version should match event version");
      assertEquals(
          1000L,
          nextState.properties().get(AGE_FIELD).version(),
          "age field version should match event version");

      // Verify nullable fields
      StateValue addressValue = nextState.properties().get(ADDRESS_FIELD);
      if (addressValue != null) {
        assertEquals(
            SpecialStateValue.UNSET.code(),
            addressValue.value(),
            "Unspecified address field should be set to UNSET");
        assertEquals(
            1000L, addressValue.version(), "address field version should match event version");
      }
    }

    @Test
    @DisplayName("active is true when createdAt is greater than deletedAt after INSERT operation")
    void testInsertOperation() {
      // given
      StateValue stateValue1 = ImmutableStateValue.builder().value("Alice").version(1).build();

      StateValue stateValue2 = ImmutableStateValue.builder().value(30).version(1).build();

      Map<String, StateValue> values = new HashMap<>();
      values.put(NAME_FIELD, stateValue1);
      values.put(AGE_FIELD, stateValue2);

      // active should be true because createdAt > deletedAt
      State state =
          ImmutableBaseState.builder()
              .active(true)
              .properties(values)
              .createdAt(1000L)
              .deletedAt(500L) // deletedAt is less than createdAt
              .checkAndBuild(schema);

      // INSERT event must include required fields
      Map<String, Object> eventValues = new HashMap<>();
      eventValues.put(NAME_FIELD, "Bob");
      eventValues.put(AGE_FIELD, 40);

      BaseEvent event =
          ImmutableBaseEvent.builder()
              .type(EventType.INSERT)
              .putProperties(NAME_FIELD, "Bob")
              .putProperties(AGE_FIELD, 40)
              .version(2000L)
              .build();

      // when
      State result = StateCompanion.transit(state, event, schema);

      // then
      assertEquals(5, result.properties().size());
      assertEquals(
          "Bob", result.properties().get(NAME_FIELD).value()); // Updated to new event value
      assertEquals(40, result.properties().get(AGE_FIELD).value()); // Updated to new event value
      assertEquals(2000L, result.properties().get(NAME_FIELD).version()); // Updated to new version
      assertEquals(2000L, result.properties().get(AGE_FIELD).version()); // Updated to new version
      assertEquals(2000L, result.createdAt()); // Updated to new version
      assertEquals(500L, result.deletedAt()); // deletedAt remains unchanged
      assertTrue(result.active()); // active is true because createdAt > deletedAt
    }

    @Test
    @DisplayName("active is false when createdAt is less than deletedAt after INSERT operation")
    void testInsertOperationWithActiveStateFalse() {
      // given
      StateValue stateValue1 = ImmutableStateValue.builder().value("Alice").version(1).build();

      StateValue stateValue2 = ImmutableStateValue.builder().value(30).version(1).build();

      Map<String, StateValue> values = new HashMap<>();
      values.put(NAME_FIELD, stateValue1);
      values.put(AGE_FIELD, stateValue2);

      // active should be false because createdAt < deletedAt
      State state =
          ImmutableBaseState.builder()
              .active(false)
              .properties(values)
              .createdAt(1000L)
              .deletedAt(3000L) // deletedAt is greater than createdAt
              .checkAndBuild(schema);

      // INSERT event must include required fields
      Map<String, Object> eventValues = new HashMap<>();
      eventValues.put(NAME_FIELD, "Carol");
      eventValues.put(AGE_FIELD, 50);

      BaseEvent event =
          ImmutableBaseEvent.builder()
              .type(EventType.INSERT)
              .putProperties(NAME_FIELD, "Carol")
              .putProperties(AGE_FIELD, 50)
              .version(2000L)
              .build();

      // when
      State result = StateCompanion.transit(state, event, schema);

      // then
      assertEquals(5, result.properties().size());
      assertEquals(
          "Carol", result.properties().get(NAME_FIELD).value()); // Updated to new event value
      assertEquals(50, result.properties().get(AGE_FIELD).value()); // Updated to new event value
      assertEquals(2000L, result.properties().get(NAME_FIELD).version()); // Updated to new version
      assertEquals(2000L, result.properties().get(AGE_FIELD).version()); // Updated to new version
      assertEquals(2000L, result.createdAt()); // Updated to new version
      assertEquals(3000L, result.deletedAt()); // deletedAt remains unchanged
      assertFalse(result.active()); // active is false because createdAt < deletedAt
    }

    @Test
    @DisplayName("UPDATE operation does not change active state")
    void testUpdateOperation() {
      // given
      StateValue stateValue1 = ImmutableStateValue.builder().value("Alice").version(1).build();

      StateValue stateValue2 = ImmutableStateValue.builder().value(30).version(1).build();

      Map<String, StateValue> values = new HashMap<>();
      values.put(NAME_FIELD, stateValue1);
      values.put(AGE_FIELD, stateValue2);

      State state =
          ImmutableBaseState.builder()
              .active(true)
              .properties(values)
              .createdAt(1000L)
              .deletedAt(null)
              .checkAndBuild(schema);

      BaseEvent event = ImmutableBaseEvent.builder().type(EventType.UPDATE).version(2000L).build();

      // when
      State result = StateCompanion.transit(state, event, schema);

      // then
      assertEquals(2, result.properties().size());
      assertEquals("Alice", result.properties().get(NAME_FIELD).value());
      assertEquals(30, result.properties().get(AGE_FIELD).value());
      assertEquals(1L, result.properties().get(NAME_FIELD).version());
      assertEquals(1L, result.properties().get(AGE_FIELD).version());
      assertEquals(1000L, result.createdAt());
      assertNull(result.deletedAt());
      assertTrue(result.active());
    }

    @Test
    @DisplayName("DELETE operation changes active state to false")
    void testDeleteOperation() {
      // given
      StateValue stateValue1 = ImmutableStateValue.builder().value("Alice").version(1).build();

      StateValue stateValue2 = ImmutableStateValue.builder().value(30).version(1).build();

      Map<String, StateValue> values = new HashMap<>();
      values.put(NAME_FIELD, stateValue1);
      values.put(AGE_FIELD, stateValue2);

      State state =
          ImmutableBaseState.builder()
              .active(true)
              .properties(values)
              .createdAt(1000L)
              .deletedAt(null)
              .checkAndBuild(schema);

      BaseEvent event = ImmutableBaseEvent.builder().type(EventType.DELETE).version(2000L).build();

      // when
      State result = StateCompanion.transit(state, event, schema);

      // then
      assertEquals(5, result.properties().size());
      assertEquals(SpecialStateValue.DELETED.code(), result.properties().get(NAME_FIELD).value());
      assertEquals(SpecialStateValue.DELETED.code(), result.properties().get(AGE_FIELD).value());
      assertEquals(2000L, result.properties().get(NAME_FIELD).version());
      assertEquals(2000L, result.properties().get(AGE_FIELD).version());
      assertEquals(1000L, result.createdAt());
      assertEquals(2000L, result.deletedAt());
      assertFalse(result.active());
    }

    @Test
    @DisplayName("active is true when createdAt is greater than deletedAt")
    void testActiveStateWithCreatedAtGreaterThanDeletedAt() {
      // given
      // Add required fields
      StateValue nameEntry = ImmutableStateValue.builder().value("Alice").version(1).build();
      StateValue ageEntry = ImmutableStateValue.builder().value(30).version(1).build();

      Map<String, StateValue> values = new HashMap<>();
      values.put(NAME_FIELD, nameEntry);
      values.put(AGE_FIELD, ageEntry);

      State state =
          ImmutableBaseState.builder()
              .active(true) // active should be true because createdAt > deletedAt
              .properties(values)
              .createdAt(2000L)
              .deletedAt(1000L)
              .checkAndBuild(schema);

      BaseEvent event = ImmutableBaseEvent.builder().type(EventType.UPDATE).version(3000L).build();

      // when
      State result = StateCompanion.transit(state, event, schema);

      // then
      assertEquals(2000L, result.createdAt());
      assertEquals(1000L, result.deletedAt());
      assertTrue(result.active());
    }

    @Test
    @DisplayName("active is false when createdAt is less than deletedAt")
    void testActiveStateWithCreatedAtLessThanDeletedAt() {
      // given
      // Add required fields (non-nullable fields must provide values)
      StateValue nameEntry = ImmutableStateValue.builder().value("Alice").version(1).build();
      StateValue ageEntry = ImmutableStateValue.builder().value(30).version(1).build();

      Map<String, StateValue> values = new HashMap<>();
      values.put(NAME_FIELD, nameEntry);
      values.put(AGE_FIELD, ageEntry);

      State state =
          ImmutableBaseState.builder()
              .active(false) // active should be false because createdAt < deletedAt
              .properties(values)
              .createdAt(1000L)
              .deletedAt(2000L)
              .checkAndBuild(schema);

      BaseEvent event = ImmutableBaseEvent.builder().type(EventType.UPDATE).version(3000L).build();

      // when
      State result = StateCompanion.transit(state, event, schema);

      // then
      assertEquals(1000L, result.createdAt());
      assertEquals(2000L, result.deletedAt());
      assertFalse(result.active());
    }
  }

  @Nested
  @DisplayName("When event time order differs from processing order")
  class EventTimeTests {

    @Test
    @DisplayName(
        "Event time order: INSERT -> DELETE -> INSERT, Processing order: INSERT -> INSERT -> DELETE, active is false")
    void testEventTimeVsProcessingTime1() {
      // given

      // Initial state
      StateValue stateValue1 = ImmutableStateValue.builder().value("Alice").version(1).build();
      StateValue stateValue2 = ImmutableStateValue.builder().value(30).version(1).build();

      Map<String, StateValue> values = new HashMap<>();
      values.put(NAME_FIELD, stateValue1);
      values.put(AGE_FIELD, stateValue2);

      State state =
          ImmutableBaseState.builder()
              .active(true)
              .properties(values)
              .createdAt(1000L)
              .deletedAt(null)
              .checkAndBuild(schema);

      // Event time order: INSERT(1000) -> DELETE(3000) -> INSERT(2000)
      // Processing order: INSERT(1000) -> INSERT(2000) -> DELETE(3000)

      // 1. Process INSERT(2000)
      Map<String, Object> insertValues = new HashMap<>();
      insertValues.put(NAME_FIELD, "Bob");
      insertValues.put(AGE_FIELD, 25);

      BaseEvent insertEvent =
          ImmutableBaseEvent.builder()
              .type(EventType.INSERT)
              .putProperties(NAME_FIELD, "Bob")
              .putProperties(AGE_FIELD, 25)
              .version(2000L)
              .build();

      State afterInsert = StateCompanion.transit(state, insertEvent, schema);

      // 2. Process DELETE(3000)
      BaseEvent deleteEvent =
          ImmutableBaseEvent.builder().type(EventType.DELETE).version(3000L).build();

      // when
      State result = StateCompanion.transit(afterInsert, deleteEvent, schema);

      // then
      // Final state: createdAt=2000, deletedAt=3000, active=false
      assertEquals(5, result.properties().size());
      assertEquals(SpecialStateValue.DELETED.code(), result.properties().get(NAME_FIELD).value());
      assertEquals(SpecialStateValue.DELETED.code(), result.properties().get(AGE_FIELD).value());
      assertEquals(3000L, result.properties().get(NAME_FIELD).version());
      assertEquals(3000L, result.properties().get(AGE_FIELD).version());
      assertEquals(2000L, result.createdAt());
      assertEquals(3000L, result.deletedAt());
      assertFalse(result.active()); // active is false because createdAt < deletedAt
    }

    @Test
    @DisplayName(
        "Event time order: INSERT -> DELETE -> INSERT, Processing order: DELETE -> INSERT -> INSERT, active is true")
    void testEventTimeVsProcessingTime2() {
      // given

      // Initial state (empty state)
      Map<String, StateValue> values = new HashMap<>();
      State state =
          ImmutableBaseState.builder()
              .active(false)
              .properties(values)
              .createdAt(null)
              .deletedAt(null)
              .checkAndBuild(schema);

      // Event time order: INSERT(1000) -> DELETE(2000) -> INSERT(3000)
      // Processing order: DELETE(2000) -> INSERT(1000) -> INSERT(3000)

      // 1. Process DELETE(2000)
      BaseEvent deleteEvent =
          ImmutableBaseEvent.builder().type(EventType.DELETE).version(2000L).build();

      State afterDelete = StateCompanion.transit(state, deleteEvent, schema);

      // 2. Process INSERT(1000)
      Map<String, Object> insertValues1 = new HashMap<>();
      insertValues1.put(NAME_FIELD, "Alice");
      insertValues1.put(AGE_FIELD, 30);

      BaseEvent insertEvent1 =
          ImmutableBaseEvent.builder()
              .type(EventType.INSERT)
              .putProperties(NAME_FIELD, "Alice")
              .putProperties(AGE_FIELD, 30)
              .version(1000L)
              .build();

      State afterInsert1 = StateCompanion.transit(afterDelete, insertEvent1, schema);

      // 3. Process INSERT(3000)
      Map<String, Object> insertValues2 = new HashMap<>();
      insertValues2.put(NAME_FIELD, "Bob");
      insertValues2.put(AGE_FIELD, 25);

      BaseEvent insertEvent2 =
          ImmutableBaseEvent.builder()
              .type(EventType.INSERT)
              .putProperties(NAME_FIELD, "Bob")
              .putProperties(AGE_FIELD, 25)
              .version(3000L)
              .build();

      // when
      State result = StateCompanion.transit(afterInsert1, insertEvent2, schema);

      // then
      // Final state: createdAt=3000, deletedAt=2000, active=true
      assertEquals(5, result.properties().size());
      assertEquals("Bob", result.properties().get(NAME_FIELD).value());
      assertEquals(25, result.properties().get(AGE_FIELD).value());
      assertEquals(3000L, result.properties().get(NAME_FIELD).version());
      assertEquals(3000L, result.properties().get(AGE_FIELD).version());
      assertEquals(3000L, result.createdAt());
      assertEquals(2000L, result.deletedAt());
      assertTrue(result.active()); // active is true because createdAt > deletedAt
    }

    @Test
    @DisplayName("UPDATE operation is not applied when event time is less than entry version")
    void testUpdateWithOlderEventTime() {
      // given

      StateValue stateValue1 = ImmutableStateValue.builder().value("Alice").version(2000).build();
      StateValue stateValue2 = ImmutableStateValue.builder().value(30).version(2000).build();

      Map<String, StateValue> values = new HashMap<>();
      values.put(NAME_FIELD, stateValue1);
      values.put(AGE_FIELD, stateValue2);

      State state =
          ImmutableBaseState.builder()
              .active(true)
              .properties(values)
              .createdAt(1000L)
              .deletedAt(null)
              .checkAndBuild(schema);

      // UPDATE event time is earlier than entry
      Map<String, Object> updateValues = new HashMap<>();
      updateValues.put(NAME_FIELD, "Bob");
      updateValues.put(AGE_FIELD, 25);

      BaseEvent updateEvent =
          ImmutableBaseEvent.builder()
              .type(EventType.UPDATE)
              .putProperties(NAME_FIELD, "Bob")
              .putProperties(AGE_FIELD, 25)
              .version(1500L) // Less than version of stateValue1 and stateValue2
              .build();

      // when
      State result = StateCompanion.transit(state, updateEvent, schema);

      // then
      // Existing values are maintained - because event time is smaller
      assertEquals(2, result.properties().size());
      assertEquals("Alice", result.properties().get(NAME_FIELD).value());
      assertEquals(30, result.properties().get(AGE_FIELD).value());
      assertEquals(2000L, result.properties().get(NAME_FIELD).version());
      assertEquals(2000L, result.properties().get(AGE_FIELD).version());
    }

    @Test
    @DisplayName("UPDATE operation is applied when event time is greater than entry version")
    void testUpdateWithNewerEventTime() {
      // given

      StateValue stateValue1 = ImmutableStateValue.builder().value("Alice").version(2000).build();
      StateValue stateValue2 = ImmutableStateValue.builder().value(30).version(2000).build();

      Map<String, StateValue> values = new HashMap<>();
      values.put(NAME_FIELD, stateValue1);
      values.put(AGE_FIELD, stateValue2);

      State state =
          ImmutableBaseState.builder()
              .active(true)
              .properties(values)
              .createdAt(1000L)
              .deletedAt(null)
              .checkAndBuild(schema);

      // UPDATE event time is later than entry
      Map<String, Object> updateValues = new HashMap<>();
      updateValues.put(NAME_FIELD, "Bob");
      updateValues.put(AGE_FIELD, 25);

      BaseEvent updateEvent =
          ImmutableBaseEvent.builder()
              .type(EventType.UPDATE)
              .putProperties(NAME_FIELD, "Bob")
              .putProperties(AGE_FIELD, 25)
              .version(3000L) // Greater than version of stateValue1 and stateValue2
              .build();

      // when
      State result = StateCompanion.transit(state, updateEvent, schema);

      // then
      // New values are applied - because event time is greater
      assertEquals(2, result.properties().size());
      assertEquals("Bob", result.properties().get(NAME_FIELD).value());
      assertEquals(25, result.properties().get(AGE_FIELD).value());
      assertEquals(3000L, result.properties().get(NAME_FIELD).version());
      assertEquals(3000L, result.properties().get(AGE_FIELD).version());
    }

    @Test
    @DisplayName(
        "When multiple operations are processed together, final state is determined based on event time")
    void testComplexEventSequence() {
      // given

      // Initial state (empty state)
      Map<String, StateValue> values = new HashMap<>();
      State state =
          ImmutableBaseState.builder()
              .active(false)
              .properties(values)
              .createdAt(null)
              .deletedAt(null)
              .checkAndBuild(schema);

      // Event sequence (event time order differs from processing order)

      // 1. [Process] DELETE at 5000
      BaseEvent deleteEvent =
          ImmutableBaseEvent.builder().type(EventType.DELETE).version(5000L).build();

      State state1 = StateCompanion.transit(state, deleteEvent, schema);

      // 2. [Process] INSERT at 1000
      Map<String, Object> insertValues1 = new HashMap<>();
      insertValues1.put(NAME_FIELD, "Alice");
      insertValues1.put(AGE_FIELD, 30);

      BaseEvent insertEvent1 =
          ImmutableBaseEvent.builder()
              .type(EventType.INSERT)
              .putProperties(NAME_FIELD, "Alice")
              .putProperties(AGE_FIELD, 30)
              .version(1000L)
              .build();

      State state2 = StateCompanion.transit(state1, insertEvent1, schema);

      // 3. [Process] UPDATE at 3000
      Map<String, Object> updateValues = new HashMap<>();
      updateValues.put(NAME_FIELD, "Alice Smith");
      updateValues.put(AGE_FIELD, 31);

      BaseEvent updateEvent =
          ImmutableBaseEvent.builder()
              .type(EventType.UPDATE)
              .putProperties(NAME_FIELD, "Alice Smith")
              .putProperties(AGE_FIELD, 31)
              .version(3000L)
              .build();

      State state3 = StateCompanion.transit(state2, updateEvent, schema);

      // 4. [Process] INSERT at 7000
      Map<String, Object> insertValues2 = new HashMap<>();
      insertValues2.put(NAME_FIELD, "Bob");
      insertValues2.put(AGE_FIELD, 25);

      BaseEvent insertEvent2 =
          ImmutableBaseEvent.builder()
              .type(EventType.INSERT)
              .putProperties(NAME_FIELD, "Bob")
              .putProperties(AGE_FIELD, 25)
              .version(7000L)
              .build();

      // when
      State result = StateCompanion.transit(state3, insertEvent2, schema);

      // then
      // Final state - sorted by event time:
      // INSERT(1000) -> UPDATE(3000) -> DELETE(5000) -> INSERT(7000)
      // Final state: createdAt=7000, deletedAt=5000, active=true (most recent event is INSERT)
      assertEquals(5, result.properties().size());
      assertEquals("Bob", result.properties().get(NAME_FIELD).value());
      assertEquals(25, result.properties().get(AGE_FIELD).value());
      assertEquals(7000L, result.properties().get(NAME_FIELD).version());
      assertEquals(7000L, result.properties().get(AGE_FIELD).version());
      assertEquals(7000L, result.createdAt());
      assertEquals(5000L, result.deletedAt());
      assertTrue(result.active()); // active is true because createdAt > deletedAt
    }
  }

  @Nested
  @DisplayName("UPSERT and Field Validation Tests")
  class UpsertAndValidationTest {

    @Test
    @DisplayName("INSERT operation behaves like UPSERT, overwriting existing data")
    void testInsertBehavesAsUpsert() {
      // given
      // Set initial state (some data exists)
      Map<String, StateValue> initialValues = new HashMap<>();
      initialValues.put(
          NAME_FIELD, ImmutableStateValue.builder().value("InitialName").version(1000L).build());
      initialValues.put(AGE_FIELD, ImmutableStateValue.builder().value(25).version(1000L).build());

      State initialState =
          ImmutableBaseState.builder()
              .active(true)
              .properties(initialValues)
              .createdAt(1000L)
              .deletedAt(null)
              .checkAndBuild(schema);

      // New INSERT event (overwrites existing data)
      Map<String, Object> newValues = new HashMap<>();
      newValues.put(NAME_FIELD, "UpdatedName");
      newValues.put(AGE_FIELD, 30);
      newValues.put(ADDRESS_FIELD, "NewAddress");

      BaseEvent insertEvent =
          ImmutableBaseEvent.builder()
              .type(EventType.INSERT)
              .putProperties(NAME_FIELD, "UpdatedName")
              .putProperties(AGE_FIELD, 30)
              .putProperties(ADDRESS_FIELD, "NewAddress")
              .version(2000L)
              .build();

      // when
      State result = StateCompanion.transit(initialState, insertEvent, schema);

      // then
      assertTrue(result.active());
      assertEquals("UpdatedName", result.properties().get(NAME_FIELD).value());
      assertEquals(30, result.properties().get(AGE_FIELD).value());
      assertEquals("NewAddress", result.properties().get(ADDRESS_FIELD).value());
      assertEquals(2000L, result.properties().get(NAME_FIELD).version());
      assertEquals(2000L, result.properties().get(AGE_FIELD).version());
      assertEquals(2000L, result.properties().get(ADDRESS_FIELD).version());
      assertEquals(2000L, result.createdAt()); // createdAt is updated
      assertNull(result.deletedAt());
    }

    @Test
    @DisplayName("In active state, non-nullable fields cannot have null, UNSET, or DELETED values")
    void testNonNullableFieldValidationInActiveState() {
      // Case 1: null value
      Map<String, StateValue> values1 = new HashMap<>();
      values1.put(AGE_FIELD, ImmutableStateValue.builder().value(30).version(1000L).build());
      // NAME_FIELD is intentionally omitted (nullable=false)

      // active=true, NAME_FIELD omitted -> should throw exception
      Exception exception1 =
          assertThrows(
              IllegalArgumentException.class,
              () -> {
                ImmutableBaseState.builder()
                    .active(true)
                    .properties(values1)
                    .createdAt(1000L)
                    .deletedAt(null)
                    .checkAndBuild(schema);
              });
      assertTrue(exception1.getMessage().contains(NAME_FIELD + " must be provided"));

      // Case 2: UNSET value
      Map<String, StateValue> values2 = new HashMap<>();
      values2.put(
          NAME_FIELD,
          ImmutableStateValue.builder()
              .value(SpecialStateValue.UNSET.code())
              .version(1000L)
              .build());
      values2.put(AGE_FIELD, ImmutableStateValue.builder().value(30).version(1000L).build());

      // active=true, NAME_FIELD=UNSET -> should throw exception
      Exception exception2 =
          assertThrows(
              IllegalArgumentException.class,
              () -> {
                ImmutableBaseState.builder()
                    .active(true)
                    .properties(values2)
                    .createdAt(1000L)
                    .deletedAt(null)
                    .checkAndBuild(schema);
              });
      assertTrue(exception2.getMessage().contains(NAME_FIELD + " must be provided"));

      // Case 3: DELETED value
      Map<String, StateValue> values3 = new HashMap<>();
      values3.put(
          NAME_FIELD,
          ImmutableStateValue.builder()
              .value(SpecialStateValue.DELETED.code())
              .version(1000L)
              .build());
      values3.put(AGE_FIELD, ImmutableStateValue.builder().value(30).version(1000L).build());

      // active=true, NAME_FIELD=DELETED -> should throw exception
      Exception exception3 =
          assertThrows(
              IllegalArgumentException.class,
              () -> {
                ImmutableBaseState.builder()
                    .active(true)
                    .properties(values3)
                    .createdAt(1000L)
                    .deletedAt(null)
                    .checkAndBuild(schema);
              });
      assertTrue(exception3.getMessage().contains(NAME_FIELD + " must be provided"));
    }
  }

  @Nested
  @DisplayName("Handling of Nullable and Required Fields")
  class NullableFieldTests {

    @Test
    @DisplayName("INSERT event sets nullable fields that are not provided to UNSET")
    void testInsertWithNullableField() {
      // given
      State initialState = StateCompanion.initialOf(schema);

      // INSERT event with only required fields (nullable fields omitted)
      Map<String, Object> eventValues = new HashMap<>();
      eventValues.put(NAME_FIELD, "User1");
      eventValues.put(AGE_FIELD, "30");
      // ADDRESS_FIELD, EMAIL_FIELD, PHONE_FIELD are omitted because nullable=true

      BaseEvent event =
          ImmutableBaseEvent.builder()
              .type(EventType.INSERT)
              .putProperties(NAME_FIELD, "User1")
              .putProperties(AGE_FIELD, "30")
              .version(1000L)
              .build();

      // when
      State result = StateCompanion.transit(initialState, event, schema);

      // then
      assertTrue(result.active(), "State should be active after INSERT event is applied");
      assertEquals(
          "User1",
          result.properties().get(NAME_FIELD).value(),
          "name field should be set to event value");
      assertEquals(
          "30",
          result.properties().get(AGE_FIELD).value(),
          "age field should be set to event value");

      // ADDRESS_FIELD should be set to UNSET
      StateValue addressValue = result.properties().get(ADDRESS_FIELD);
      assertNotNull(addressValue, "nullable field must also have a value set (UNSET)");
      assertEquals(
          SpecialStateValue.UNSET.code(),
          addressValue.value(),
          "Unspecified nullable field should be set to UNSET");
      assertEquals(1000L, addressValue.version(), "Field version should match event version");

      // EMAIL_FIELD should be set to UNSET
      StateValue emailValue = result.properties().get(EMAIL_FIELD);
      assertNotNull(emailValue, "nullable field must also have a value set (UNSET)");
      assertEquals(
          SpecialStateValue.UNSET.code(),
          emailValue.value(),
          "Unspecified nullable field should be set to UNSET");
      assertEquals(1000L, emailValue.version(), "Field version should match event version");

      // PHONE_FIELD should be set to UNSET
      StateValue phoneValue = result.properties().get(PHONE_FIELD);
      assertNotNull(phoneValue, "nullable field must also have a value set (UNSET)");
      assertEquals(
          SpecialStateValue.UNSET.code(),
          phoneValue.value(),
          "Unspecified nullable field should be set to UNSET");
      assertEquals(1000L, phoneValue.version(), "Field version should match event version");
    }

    @Test
    @DisplayName("UPDATE event does not change values of fields that are not mentioned")
    void testUpdateFieldPreservation() {
      // given
      // Set initial state (all fields have values)
      Map<String, StateValue> values = new HashMap<>();
      values.put(
          NAME_FIELD, ImmutableStateValue.builder().value("InitialName").version(1000L).build());
      values.put(AGE_FIELD, ImmutableStateValue.builder().value("25").version(1000L).build());
      values.put(
          ADDRESS_FIELD,
          ImmutableStateValue.builder().value("InitialAddress").version(1000L).build());
      values.put(
          EMAIL_FIELD,
          ImmutableStateValue.builder().value("initial@example.com").version(1000L).build());
      values.put(
          PHONE_FIELD, ImmutableStateValue.builder().value("010-1234-5678").version(1000L).build());

      State state =
          ImmutableBaseState.builder()
              .active(true)
              .properties(values)
              .createdAt(1000L)
              .deletedAt(null)
              .checkAndBuild(schema);

      // UPDATE event that only updates name
      Map<String, Object> updateValues = new HashMap<>();
      updateValues.put(NAME_FIELD, "UpdatedName");
      // AGE_FIELD, ADDRESS_FIELD, EMAIL_FIELD, PHONE_FIELD are not mentioned

      BaseEvent updateEvent =
          ImmutableBaseEvent.builder()
              .type(EventType.UPDATE)
              .putProperties(NAME_FIELD, "UpdatedName")
              .version(2000L)
              .build();

      // when
      State result = StateCompanion.transit(state, updateEvent, schema);

      // then
      assertEquals(
          "UpdatedName",
          result.properties().get(NAME_FIELD).value(),
          "Mentioned field should be updated");
      assertEquals(
          2000L,
          result.properties().get(NAME_FIELD).version(),
          "Updated field version should be set to event version");

      assertEquals(
          "25",
          result.properties().get(AGE_FIELD).value(),
          "Unmentioned field should retain previous value");
      assertEquals(
          1000L,
          result.properties().get(AGE_FIELD).version(),
          "Unmentioned field version should retain previous version");

      assertEquals(
          "InitialAddress",
          result.properties().get(ADDRESS_FIELD).value(),
          "Nullable field should also retain previous value if not mentioned");
      assertEquals(
          1000L,
          result.properties().get(ADDRESS_FIELD).version(),
          "Unmentioned field version should retain previous version");

      assertEquals(
          "initial@example.com",
          result.properties().get(EMAIL_FIELD).value(),
          "Nullable field should also retain previous value if not mentioned");
      assertEquals(
          1000L,
          result.properties().get(EMAIL_FIELD).version(),
          "Unmentioned field version should retain previous version");

      assertEquals(
          "010-1234-5678",
          result.properties().get(PHONE_FIELD).value(),
          "Nullable field should also retain previous value if not mentioned");
      assertEquals(
          1000L,
          result.properties().get(PHONE_FIELD).version(),
          "Unmentioned field version should retain previous version");
    }

    @Test
    @DisplayName("DELETE event marks all fields as DELETED")
    void testDeleteMarksAllFields() {
      // given
      // Set initial state
      Map<String, StateValue> values = new HashMap<>();
      values.put(NAME_FIELD, ImmutableStateValue.builder().value("User1").version(1000L).build());
      values.put(AGE_FIELD, ImmutableStateValue.builder().value("30").version(1000L).build());
      values.put(
          EMAIL_FIELD,
          ImmutableStateValue.builder().value("user1@example.com").version(1000L).build());
      // ADDRESS_FIELD and PHONE_FIELD are intentionally omitted (possible because nullable)

      State state =
          ImmutableBaseState.builder()
              .active(true)
              .properties(values)
              .createdAt(1000L)
              .deletedAt(null)
              .checkAndBuild(schema);

      BaseEvent deleteEvent =
          ImmutableBaseEvent.builder().type(EventType.DELETE).version(2000L).build();

      // when
      State result = StateCompanion.transit(state, deleteEvent, schema);

      // then
      assertFalse(result.active(), "State should be active=false after DELETE event is applied");
      assertEquals(
          2000L,
          result.deletedAt(),
          "deletedAt should be event version after DELETE event is applied");

      // Verify required fields
      assertEquals(
          SpecialStateValue.DELETED.code(),
          result.properties().get(NAME_FIELD).value(),
          "Required field should be marked as DELETED");
      assertEquals(
          2000L,
          result.properties().get(NAME_FIELD).version(),
          "Field version should be updated to event version");

      assertEquals(
          SpecialStateValue.DELETED.code(),
          result.properties().get(AGE_FIELD).value(),
          "Required field should be marked as DELETED");
      assertEquals(
          2000L,
          result.properties().get(AGE_FIELD).version(),
          "Field version should be updated to event version");

      assertEquals(
          SpecialStateValue.DELETED.code(),
          result.properties().get(EMAIL_FIELD).value(),
          "Existing nullable field should also be marked as DELETED");
      assertEquals(
          2000L,
          result.properties().get(EMAIL_FIELD).version(),
          "Field version should be updated to event version");

      // Verify nullable fields that didn't exist (ADDRESS_FIELD, PHONE_FIELD)
      StateValue addressValue = result.properties().get(ADDRESS_FIELD);
      assertNotNull(
          addressValue, "Non-existent nullable field should also be created and marked as DELETED");
      assertEquals(
          SpecialStateValue.DELETED.code(),
          addressValue.value(),
          "Nullable field should also be marked as DELETED");
      assertEquals(2000L, addressValue.version(), "Field version should be set to event version");

      StateValue phoneValue = result.properties().get(PHONE_FIELD);
      assertNotNull(
          phoneValue, "Non-existent nullable field should also be created and marked as DELETED");
      assertEquals(
          SpecialStateValue.DELETED.code(),
          phoneValue.value(),
          "Nullable field should also be marked as DELETED");
      assertEquals(2000L, phoneValue.version(), "Field version should be set to event version");
    }

    @Test
    @DisplayName("Can explicitly set null for multiple nullable fields individually")
    void testExplicitNullForMultipleNullableFields() {
      // given
      // Set initial state (all fields have values)
      Map<String, StateValue> values = new HashMap<>();
      values.put(NAME_FIELD, ImmutableStateValue.builder().value("User1").version(1000L).build());
      values.put(AGE_FIELD, ImmutableStateValue.builder().value("30").version(1000L).build());
      values.put(
          ADDRESS_FIELD, ImmutableStateValue.builder().value("Address1").version(1000L).build());
      values.put(
          EMAIL_FIELD,
          ImmutableStateValue.builder().value("user1@example.com").version(1000L).build());
      values.put(
          PHONE_FIELD, ImmutableStateValue.builder().value("010-1234-5678").version(1000L).build());

      State state =
          ImmutableBaseState.builder()
              .active(true)
              .properties(values)
              .createdAt(1000L)
              .deletedAt(null)
              .checkAndBuild(schema);

      // UPDATE event that explicitly sets all nullable fields to null
      Map<String, Object> updateValues = new HashMap<>();
      updateValues.put(NAME_FIELD, "UpdatedUser");
      updateValues.put(AGE_FIELD, "35");
      updateValues.put(ADDRESS_FIELD, null); // Explicitly set to null
      updateValues.put(EMAIL_FIELD, null); // Explicitly set to null
      updateValues.put(PHONE_FIELD, null); // Explicitly set to null

      BaseEvent updateEvent =
          ImmutableBaseEvent.builder()
              .type(EventType.UPDATE)
              .putProperties(NAME_FIELD, "UpdatedUser")
              .putProperties(AGE_FIELD, "35")
              .putProperties(ADDRESS_FIELD, null) // Explicitly set to null
              .putProperties(EMAIL_FIELD, null) // Explicitly set to null
              .putProperties(PHONE_FIELD, null) // Explicitly set to null
              .version(2000L)
              .build();

      // when
      State result = StateCompanion.transit(state, updateEvent, schema);

      // then
      assertEquals(
          "UpdatedUser",
          result.properties().get(NAME_FIELD).value(),
          "Required field should be updated");
      assertEquals(
          "35", result.properties().get(AGE_FIELD).value(), "Required field should be updated");

      // Explicit null is now handled as UNSET
      StateValue addressValue = result.properties().get(ADDRESS_FIELD);
      assertNotNull(addressValue, "Explicit null should not make StateValue itself null");
      assertEquals(
          SpecialStateValue.UNSET.code(),
          addressValue.value(),
          "Explicit null should be handled as UNSET");
      assertEquals(
          2000L, addressValue.version(), "Field version should be updated to event version");

      StateValue emailValue = result.properties().get(EMAIL_FIELD);
      assertNotNull(emailValue, "Explicit null should not make StateValue itself null");
      assertEquals(
          SpecialStateValue.UNSET.code(),
          emailValue.value(),
          "Explicit null should be handled as UNSET");
      assertEquals(2000L, emailValue.version(), "Field version should be updated to event version");

      StateValue phoneValue = result.properties().get(PHONE_FIELD);
      assertNotNull(phoneValue, "Explicit null should not make StateValue itself null");
      assertEquals(
          SpecialStateValue.UNSET.code(),
          phoneValue.value(),
          "Explicit null should be handled as UNSET");
      assertEquals(2000L, phoneValue.version(), "Field version should be updated to event version");
    }
  }

  @Test
  @DisplayName("Exception occurs when required fields are missing in INSERT event")
  void testInsertWithMissingRequiredFields() {
    // given
    State initialState = StateCompanion.initialOf(schema);

    // INSERT event with missing required field (NAME_FIELD)
    Map<String, Object> eventValues = new HashMap<>();
    // NAME_FIELD omitted (required)
    eventValues.put(AGE_FIELD, "30");

    BaseEvent event =
        ImmutableBaseEvent.builder()
            .type(EventType.INSERT)
            .version(1000L)
            .properties(eventValues)
            .build();

    // when, then
    Exception exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> StateCompanion.transit(initialState, event, schema),
            "Exception should occur when required fields are missing in INSERT event");

    assertTrue(
        exception.getMessage().contains(NAME_FIELD + " must be provided"),
        "Appropriate exception message should be included when required field is missing");
  }
}
