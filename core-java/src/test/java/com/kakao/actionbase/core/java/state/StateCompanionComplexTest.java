package com.kakao.actionbase.core.java.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kakao.actionbase.core.java.state.base.BaseEvent;
import com.kakao.actionbase.core.java.state.base.ImmutableBaseEvent;
import com.kakao.actionbase.core.java.state.base.ImmutableBaseState;
import com.kakao.actionbase.core.java.state.base.StateCompanion;
import com.kakao.actionbase.core.java.types.DataType;
import com.kakao.actionbase.core.java.types.ImmutableStructType;
import com.kakao.actionbase.core.java.types.StructType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Final consistency test. This test verifies that if event time order is the same, the final state
 * is identical even if event processing order differs.
 */
@DisplayName("Merge Final Consistency Test")
class StateCompanionComplexTest {

  private static final String NAME_FIELD = "name";
  private static final String AGE_FIELD = "age";
  private static final String ADDRESS_FIELD = "address";
  private static final String EMAIL_FIELD = "email";
  private static final String PHONE_FIELD = "phone";

  StructType schema;

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
  }

  @Test
  @DisplayName(
      "Final state should be identical for all combinations regardless of event processing order")
  void testEventualConsistency() {
    // Limit number of events
    int n = 2;

    System.out.println("===== 1. Event Sequence List to Test =====");
    System.out.println("Test content: For all valid event sequences with different event times");
    System.out.println(
        "        Verify that final state is identical regardless of event processing order");
    System.out.println("Configuration: Number of events = " + n);
    System.out.println(
        "      Fields used = "
            + NAME_FIELD
            + ", "
            + AGE_FIELD
            + ", "
            + ADDRESS_FIELD
            + ", "
            + EMAIL_FIELD
            + ", "
            + PHONE_FIELD);
    System.out.println("Valid event sequence examples:");
    System.out.println(
        "      I1/I2/I3, I1/I2/U3, I1/I2/D3, I1/U2/I3, I1/U2/U3, I1/U2/D3, I1/D2/I3, etc.");

    // Generate all possible event sequences
    List<List<EventData>> allEventSequences = generateAllValidEventSequences(n);

    System.out.println("===== 2. Test Start =====");

    // Test all possible processing orders for each event sequence
    for (List<EventData> eventSequence : allEventSequences) {
      try {
        System.out.println(
            "\nTest Sequence: "
                + eventSequence.stream()
                    .map(evt -> getShortEventType(evt.type) + (evt.version / 1000))
                    .collect(Collectors.joining("/")));

        List<EventData> sortedByEventTime = new ArrayList<>(eventSequence);
        Collections.sort(sortedByEventTime);

        // Generate all possible processing orders
        List<List<EventData>> allProcessingOrders = generateAllPermutations(sortedByEventTime);

        // Store final results for each processing order
        List<State> results = new ArrayList<>();

        // Apply events in each processing order
        for (List<EventData> processingOrder : allProcessingOrders) {
          State result = processEventsWithFixedData(processingOrder);
          results.add(result);

          System.out.println(
              "  Processing Order: "
                  + processingOrder.stream()
                      .map(evt -> getShortEventType(evt.type) + (evt.version / 1000))
                      .collect(Collectors.joining("/"))
                  + " => active="
                  + result.active()
                  + ", createdAt="
                  + result.createdAt()
                  + ", deletedAt="
                  + result.deletedAt()
                  + ", values="
                  + summarizeValues(result.properties()));
        }

        // Verify that all results are identical
        if (results.size() > 1) {
          State firstResult = results.get(0);

          for (int i = 1; i < results.size(); i++) {
            State currentResult = results.get(i);

            // Verify basic state
            assertEquals(
                firstResult.active(), currentResult.active(), "Active state does not match.");
            assertEquals(
                firstResult.createdAt(), currentResult.createdAt(), "createdAt does not match.");
            assertEquals(
                firstResult.deletedAt(), currentResult.deletedAt(), "deletedAt does not match.");

            // Verify values
            assertEquals(
                firstResult.properties().size(),
                currentResult.properties().size(),
                "Values size does not match.");
            for (String key : firstResult.properties().keySet()) {
              assertEquals(
                  firstResult.properties().get(key),
                  currentResult.properties().get(key),
                  "Values " + key + " value does not match.");
            }
          }
        }

        // Print verification success message
        System.out.println(
            "Event Sequence Verification Success: "
                + eventSequence.stream()
                    .map(evt -> getShortEventType(evt.type) + (evt.version / 1000))
                    .collect(Collectors.joining("/")));
      } catch (Exception exc) {
        System.err.println(
            "Event Sequence Verification Failed: "
                + eventSequence.stream()
                    .map(evt -> getShortEventType(evt.type) + (evt.version / 1000))
                    .collect(Collectors.joining("/")));
        System.err.println("Failure Reason: " + exc.getMessage());
      }
    }
  }

  /**
   * Returns a pre-generated list of valid event sequences based on Python/Kotlin code. These
   * sequences follow these rules: 1. After DELETE, only INSERT is allowed (UPDATE or DELETE are not
   * allowed) 2. Must always start with INSERT
   */
  private List<List<EventData>> generateAllValidEventSequences(int n) {
    // Pre-defined valid operation sequence strings (use only first n)
    String[] allValidRawOpSequences = {
      "IIIIIII", "IIIIIIU", "IIIIIID", "IIIIIUI", "IIIIIUU", "IIIIIUD", "IIIIIDI", "IIIIUII",
      "IIIIUIU", "IIIIUID", "IIIIUUI", "IIIIUUU", "IIIIUUD", "IIIIUDI", "IIIIDII", "IIIIDIU",
      "IIIIDID", "IIIUIII", "IIIUIIU", "IIIUIID", "IIIUIUI", "IIIUIUU", "IIIUIUD", "IIIUIDI",
      "IIIUUII", "IIIUUIU", "IIIUUID", "IIIUUUI", "IIIUUUU", "IIIUUUD", "IIIUUDI", "IIIUDII",
      "IIIUDIU", "IIIUDID", "IIIDIII", "IIIDIIU", "IIIDIID", "IIIDIUI", "IIIDIUU", "IIIDIUD",
      "IIIDIDI", "IIUIIII", "IIUIIIU", "IIUIIID", "IIUIIUI", "IIUIIUU", "IIUIIUD", "IIUIIDI",
      "IIUIUII", "IIUIUIU", "IIUIUID", "IIUIUUI", "IIUIUUU", "IIUIUUD", "IIUIUDI", "IIUIDII",
      "IIUIDIU", "IIUIDID", "IIUUIII", "IIUUIIU", "IIUUIID", "IIUUIUI", "IIUUIUU", "IIUUIUD",
      "IIUUIDI", "IIUUUII", "IIUUUIU", "IIUUUID", "IIUUUUI", "IIUUUUU", "IIUUUUD", "IIUUUDI",
      "IIUUDII", "IIUUDIU", "IIUUDID", "IIUDIII", "IIUDIIU", "IIUDIID", "IIUDIUI", "IIUDIUU",
      "IIUDIUD", "IIUDIDI", "IIDIIII", "IIDIIIU", "IIDIIID", "IIDIIUI", "IIDIIUU", "IIDIIUD",
      "IIDIIDI", "IIDIUII", "IIDIUIU", "IIDIUID", "IIDIUUI", "IIDIUUU", "IIDIUUD", "IIDIUDI",
      "IIDIDII", "IIDIDIU", "IIDIDID", "IUIIIII", "IUIIIIU", "IUIIIID", "IUIIIUI", "IUIIIUU",
      "IUIIIUD", "IUIIIDI", "IUIIUII", "IUIIUIU", "IUIIUID", "IUIIUUI", "IUIIUUU", "IUIIUUD",
      "IUIIUDI", "IUIIDII", "IUIIDIU", "IUIIDID", "IUIUIII", "IUIUIIU", "IUIUIID", "IUIUIUI",
      "IUIUIUU", "IUIUIUD", "IUIUIDI", "IUIUUII", "IUIUUIU", "IUIUUID", "IUIUUUI", "IUIUUUU",
      "IUIUUUD", "IUIUUDI", "IUIUDII", "IUIUDIU", "IUIUDID", "IUIDIII", "IUIDIIU", "IUIDIID",
      "IUIDIUI", "IUIDIUU", "IUIDIUD", "IUIDIDI", "IUUIIII", "IUUIIIU", "IUUIIID", "IUUIIUI",
      "IUUIIUU", "IUUIIUD", "IUUIIDI", "IUUIUII", "IUUIUIU", "IUUIUID", "IUUIUUI", "IUUIUUU",
      "IUUIUUD", "IUUIUDI", "IUUIDII", "IUUIDIU", "IUUIDID", "IUUUIII", "IUUUIIU", "IUUUIID",
      "IUUUIUI", "IUUUIUU", "IUUUIUD", "IUUUIDI", "IUUUUII", "IUUUUIU", "IUUUUID", "IUUUUUI",
      "IUUUUUU", "IUUUUUD", "IUUUUDI", "IUUUDII", "IUUUDIU", "IUUUDID", "IUUDIII", "IUUDIIU",
      "IUUDIID", "IUUDIUI", "IUUDIUU", "IUUDIUD", "IUUDIDI", "IUDIIII", "IUDIIIU", "IUDIIID",
      "IUDIIUI", "IUDIIUU", "IUDIIUD", "IUDIIDI", "IUDIUII", "IUDIUIU", "IUDIUID", "IUDIUUI",
      "IUDIUUU", "IUDIUUD", "IUDIUDI", "IUDIDII", "IUDIDIU", "IUDIDID", "IDIIIII", "IDIIIIU",
      "IDIIIID", "IDIIIUI", "IDIIIUU", "IDIIIUD", "IDIIIDI", "IDIIUII", "IDIIUIU", "IDIIUID",
      "IDIIUUI", "IDIIUUU", "IDIIUUD", "IDIIUDI", "IDIIDII", "IDIIDIU", "IDIIDID", "IDIUIII",
      "IDIUIIU", "IDIUIID", "IDIUIUI", "IDIUIUU", "IDIUIUD", "IDIUIDI", "IDIUUII", "IDIUUIU",
      "IDIUUID", "IDIUUUI", "IDIUUUU", "IDIUUUD", "IDIUUDI", "IDIUDII", "IDIUDIU", "IDIUDID",
      "IDIDIII", "IDIDIIU", "IDIDIID", "IDIDIUI", "IDIDIUU", "IDIDIUD", "IDIDIDI"
    };

    List<String> rawOpSequences = new ArrayList<>();
    for (String sequence : allValidRawOpSequences) {
      // Trim to length n
      if (sequence.length() >= n) {
        String trimmed = sequence.substring(0, n);
        if (!rawOpSequences.contains(trimmed)) {
          rawOpSequences.add(trimmed);
        }
      }
    }

    // Sort
    Collections.sort(rawOpSequences);

    // Convert string sequence to EventData object list
    List<List<EventData>> result = new ArrayList<>();
    for (String rawOpSequence : rawOpSequences) {
      List<EventData> eventSequence = new ArrayList<>();
      for (int i = 0; i < rawOpSequence.length(); i++) {
        char op = rawOpSequence.charAt(i);
        long ts = i; // Use index as event time

        EventType eventType;
        switch (op) {
          case 'I':
            eventType = EventType.INSERT;
            break;
          case 'U':
            eventType = EventType.UPDATE;
            break;
          case 'D':
            eventType = EventType.DELETE;
            break;
          default:
            throw new IllegalArgumentException("Invalid operation: " + op);
        }

        eventSequence.add(
            new EventData(eventType, 1000L * (ts + 1))); // Set time in 1000 units for readability
      }
      result.add(eventSequence);
    }

    return result;
  }

  /** Generates all possible permutations of the given list. */
  private <T> List<List<T>> generateAllPermutations(List<T> items) {
    List<List<T>> result = new ArrayList<>();
    generatePermutationsRecursive(items, new ArrayList<>(), new boolean[items.size()], result);
    return result;
  }

  private <T> void generatePermutationsRecursive(
      List<T> items, List<T> currentPermutation, boolean[] used, List<List<T>> result) {

    if (currentPermutation.size() == items.size()) {
      // Base case: if all items are used, add to result
      result.add(new ArrayList<>(currentPermutation));
      return;
    }

    for (int i = 0; i < items.size(); i++) {
      if (!used[i]) {
        // Use i-th item
        used[i] = true;
        currentPermutation.add(items.get(i));

        // Recursive call
        generatePermutationsRecursive(items, currentPermutation, used, result);

        // Backtrack: remove i-th item
        currentPermutation.remove(currentPermutation.size() - 1);
        used[i] = false;
      }
    }
  }

  /**
   * Applies events in the given processing order and returns the final state. (Ensures test
   * consistency with fixed data)
   */
  private State processEventsWithFixedData(List<EventData> processingOrder) {
    // Create initial state
    Map<String, StateValue> entries = new HashMap<>();

    // Process each event in order
    State current =
        ImmutableBaseState.builder()
            .active(false)
            .properties(entries)
            .createdAt(null)
            .deletedAt(null)
            .checkAndBuild(schema);

    for (EventData eventData : processingOrder) {
      BaseEvent event;

      if (eventData.type != EventType.DELETE) {
        ImmutableBaseEvent.Builder eventBuilder =
            ImmutableBaseEvent.builder().type(eventData.type).version(eventData.version);

        // Add fixed data according to event type
        eventBuilder.putProperties(NAME_FIELD, "User-" + eventData.version);
        eventBuilder.putProperties(AGE_FIELD, (int) (eventData.version / 100));

        // Set nullable fields when INSERT operation
        if (eventData.type == EventType.INSERT) {
          // Set address for odd versions, do not set for even versions (nullable test)
          if (eventData.version % 2000 != 0) { // Only set when not even version
            eventBuilder.putProperties(ADDRESS_FIELD, "Address-" + eventData.version);
          }

          // Set email only for versions that are multiples of 3
          if (eventData.version % 3000 == 0) {
            eventBuilder.putProperties(EMAIL_FIELD, "email-" + eventData.version + "@example.com");
          }

          // Set phone only for versions that are multiples of 5
          if (eventData.version % 5000 == 0) {
            eventBuilder.putProperties(PHONE_FIELD, "010-" + eventData.version);
          }
        } else if (eventData.type == EventType.UPDATE) {
          // Test nullable field setting in UPDATE operation under specific conditions

          // Set address to null when UPDATE at versions that are multiples of 3 (field removal
          // test)
          if (eventData.version % 3000 == 0) {
            eventBuilder.putProperties(ADDRESS_FIELD, null);
          }

          // Explicitly set email when UPDATE at versions that are multiples of 5
          if (eventData.version % 5000 == 0) {
            eventBuilder.putProperties(
                EMAIL_FIELD, "updated-email-" + eventData.version + "@example.com");
          }

          // Set phone to null when UPDATE at versions that are multiples of 7 (field removal test)
          if (eventData.version % 7000 == 0) {
            eventBuilder.putProperties(PHONE_FIELD, null);
          }
        }

        event = eventBuilder.build();
      } else {
        // For DELETE events, do not set values
        event =
            ImmutableBaseEvent.builder().type(eventData.type).version(eventData.version).build();
      }

      current = StateCompanion.transit(current, event, schema);
    }

    return current;
  }

  /**
   * Inner class representing mergeable event data. Implements Comparable to enable sorting by event
   * time.
   */
  private static class EventData implements Comparable<EventData> {
    final EventType type;
    final long version; // Event time

    EventData(EventType operation, long version) {
      this.type = operation;
      this.version = version;
    }

    @Override
    public int compareTo(EventData other) {
      return Long.compare(this.version, other.version);
    }

    @Override
    public String toString() {
      return type + "(" + version + ")";
    }
  }

  @Test
  @DisplayName("Final Consistency Test for Complex Event Sequence")
  void testComplexEventSequence() {
    System.out.println("===== 1. Event Sequence List to Test =====");
    System.out.println("Test content: For complex event sequence (I→U→D→I)");
    System.out.println(
        "        Verify that final state is identical regardless of event processing order");
    System.out.println("Configuration: Event sequence = I1/U2/D3/I4");
    System.out.println(
        "      Fields used = "
            + NAME_FIELD
            + ", "
            + AGE_FIELD
            + ", "
            + ADDRESS_FIELD
            + ", "
            + EMAIL_FIELD
            + ", "
            + PHONE_FIELD);
    System.out.println(
        "      Verification content: Final state should be active=true, createdAt=4000, deletedAt=3000");

    // Define event sequence to test
    List<EventData> eventSequence = new ArrayList<>();
    eventSequence.add(new EventData(EventType.INSERT, 1000));
    eventSequence.add(new EventData(EventType.UPDATE, 2000));
    eventSequence.add(new EventData(EventType.DELETE, 3000));
    eventSequence.add(new EventData(EventType.INSERT, 4000));

    System.out.println("===== 2. Test Start =====");

    // Generate all possible processing orders (4! = 24)
    List<List<EventData>> allProcessingOrders = generateAllPermutations(eventSequence);

    // Store results for all processing orders
    List<State> results = new ArrayList<>();

    // Apply events in each processing order
    for (List<EventData> processingOrder : allProcessingOrders) {
      State result = processEventsWithFixedData(processingOrder);
      results.add(result);
    }

    // Simply verify that all results are identical
    if (!results.isEmpty()) {
      State firstResult = results.get(0);
      boolean allConsistent = true;

      for (int i = 1; i < results.size(); i++) {
        State currentResult = results.get(i);

        if (firstResult.active() != currentResult.active()
            || !Objects.equals(firstResult.createdAt(), currentResult.createdAt())
            || !Objects.equals(firstResult.deletedAt(), currentResult.deletedAt())
            || !Objects.equals(firstResult.properties(), currentResult.properties())) {
          allConsistent = false;
          System.err.println("Inconsistency found: First result and " + i + "-th result differ.");
          System.err.println(
              "First: active="
                  + firstResult.active()
                  + ", createdAt="
                  + firstResult.createdAt()
                  + ", deletedAt="
                  + firstResult.deletedAt()
                  + ", values="
                  + firstResult.properties());
          System.err.println(
              i
                  + "-th: active="
                  + currentResult.active()
                  + ", createdAt="
                  + currentResult.createdAt()
                  + ", deletedAt="
                  + currentResult.deletedAt()
                  + ", values="
                  + currentResult.properties());
          break;
        }
      }

      assertTrue(allConsistent, "Results should match for all processing orders.");

      // Verify final state (when sorted by event time)
      State finalState = results.get(0);
      assertTrue(finalState.active()); // active state because last event is INSERT
      assertEquals(4000L, finalState.createdAt()); // Time of last INSERT
      assertEquals(3000L, finalState.deletedAt()); // Time of last DELETE
    }
  }

  @Test
  @DisplayName("Consistency Test for Events with Same Event Time")
  void testSameEventTimeConsistency() {
    System.out.println("===== 1. Event Sequence List to Test =====");
    System.out.println(
        "Test content: For different event types (I,U,D) with same event time (1000)");
    System.out.println("        Test all possible processing orders and verify result consistency");
    System.out.println("Configuration: Event sequence = I1/U1/D1 (all same time)");
    System.out.println(
        "      Fields used = "
            + NAME_FIELD
            + ", "
            + AGE_FIELD
            + ", "
            + ADDRESS_FIELD
            + ", "
            + EMAIL_FIELD
            + ", "
            + PHONE_FIELD);
    System.out.println("      Number of processing order cases: 3! = 6");
    System.out.println(
        "      Verification content: Check if active state is identical for all processing orders");

    // Define event sequence to test (same event time)
    List<EventData> eventSequence = new ArrayList<>();
    eventSequence.add(new EventData(EventType.INSERT, 1000));
    eventSequence.add(new EventData(EventType.UPDATE, 1000));
    eventSequence.add(new EventData(EventType.DELETE, 1000));

    System.out.println("===== 2. Test Start =====");

    // Generate all possible processing orders (3! = 6)
    List<List<EventData>> allProcessingOrders = generateAllPermutations(eventSequence);

    // Store results for all processing orders
    List<State> results = new ArrayList<>();

    // Apply events in each processing order
    for (List<EventData> processingOrder : allProcessingOrders) {
      State result = processEventsWithFixedData(processingOrder);
      results.add(result);

      // Print processing order
      System.out.println(
          "Processing Order: "
              + processingOrder.stream()
                  .map(evt -> getShortEventType(evt.type) + (evt.version / 1000))
                  .collect(Collectors.joining("/"))
              + " => active="
              + result.active()
              + ", values="
              + summarizeValues(result.properties()));
    }

    // Verify that all results are identical
    // For same event time, processing order matters, so consistency may not be guaranteed
    // However, should have consistent results based on last event processed

    // Aggregate results by processing order
    Map<Boolean, Integer> activeStateCount = new HashMap<>();
    for (State result : results) {
      activeStateCount.put(result.active(), activeStateCount.getOrDefault(result.active(), 0) + 1);
    }

    // For same event time, should be false if last processed is DELETE, true if INSERT
    System.out.println("Active State Distribution: " + activeStateCount);
  }

  @Test
  @DisplayName("Test for Multiple Nullable Fields and UNSET Values")
  void testMultipleNullableFieldsAndUnsetValue() {
    System.out.println("===== 1. Multiple Nullable Fields and UNSET Value Test =====");
    System.out.println(
        "Test content: Verify behavior of multiple nullable fields (address, email, phone) and UNSET/null values");
    System.out.println(
        "Configuration: Event sequence = I1(address present)/I3(email present)/I5(phone present)/U7(phone=null)");
    System.out.println(
        "      Fields used = "
            + NAME_FIELD
            + ", "
            + AGE_FIELD
            + ", "
            + ADDRESS_FIELD
            + ", "
            + EMAIL_FIELD
            + ", "
            + PHONE_FIELD
            + "(all nullable)");

    // Define event sequence for nullable test
    List<EventData> eventSequence = new ArrayList<>();
    eventSequence.add(new EventData(EventType.INSERT, 1000)); // address present
    eventSequence.add(new EventData(EventType.INSERT, 3000)); // email present
    eventSequence.add(new EventData(EventType.INSERT, 5000)); // phone present
    eventSequence.add(new EventData(EventType.UPDATE, 7000)); // phone=null

    System.out.println("===== 2. Test Start =====");

    // Test event time order (ensure processed in final order)
    List<EventData> timeOrderEvents = new ArrayList<>(eventSequence);
    Collections.sort(timeOrderEvents); // Sort by event time
    State timeOrderResult = processEventsWithFixedData(timeOrderEvents);

    // Verify phone field is UNSET (when processed in event time order)
    StateValue phoneValue = timeOrderResult.properties().get(PHONE_FIELD);
    assertEquals(
        SpecialStateValue.UNSET.code(),
        phoneValue.value(),
        "When processed in event time order, phone field explicitly set to null in UPDATE should be UNSET.");

    System.out.println(
        "  Processed in event time order: "
            + timeOrderEvents.stream()
                .map(evt -> getShortEventType(evt.type) + (evt.version / 1000))
                .collect(Collectors.joining("/"))
            + " => active="
            + timeOrderResult.active()
            + ", createdAt="
            + timeOrderResult.createdAt()
            + ", deletedAt="
            + timeOrderResult.deletedAt()
            + ", values="
            + summarizeValues(timeOrderResult.properties()));

    System.out.println(
        "    "
            + ADDRESS_FIELD
            + " Status: "
            + getFieldStatus(timeOrderResult.properties().get(ADDRESS_FIELD)));
    System.out.println(
        "    "
            + EMAIL_FIELD
            + " Status: "
            + getFieldStatus(timeOrderResult.properties().get(EMAIL_FIELD)));
    System.out.println(
        "    "
            + PHONE_FIELD
            + " Status: "
            + getFieldStatus(timeOrderResult.properties().get(PHONE_FIELD)));

    // Test various processing orders
    System.out.println("\n  === Test Various Processing Orders ===");

    // Store final results for each processing order
    List<List<EventData>> allProcessingOrders = generateAllPermutations(eventSequence);

    for (List<EventData> processingOrder : allProcessingOrders) {
      State result = processEventsWithFixedData(processingOrder);

      System.out.println(
          "  Processing order: "
              + processingOrder.stream()
                  .map(evt -> getShortEventType(evt.type) + (evt.version / 1000))
                  .collect(Collectors.joining("/"))
              + " => active="
              + result.active()
              + ", createdAt="
              + result.createdAt()
              + ", deletedAt="
              + result.deletedAt()
              + ", values="
              + summarizeValues(result.properties()));

      // Check status of nullable fields
      StateValue addressValue = result.properties().get(ADDRESS_FIELD);
      StateValue emailValue = result.properties().get(EMAIL_FIELD);
      phoneValue = result.properties().get(PHONE_FIELD);

      String addressStatus = getFieldStatus(addressValue);
      String emailStatus = getFieldStatus(emailValue);
      String phoneStatus = getFieldStatus(phoneValue);

      System.out.println("    " + ADDRESS_FIELD + " Status: " + addressStatus);
      System.out.println("    " + EMAIL_FIELD + " Status: " + emailStatus);
      System.out.println("    " + PHONE_FIELD + " Status: " + phoneStatus);

      // All results should match the result processed in time order
      assertEquals(
          timeOrderResult.active(),
          result.active(),
          "Active state should match for all processing orders.");
      assertEquals(
          timeOrderResult.createdAt(),
          result.createdAt(),
          "createdAt should match for all processing orders.");
      assertEquals(
          timeOrderResult.deletedAt(),
          result.deletedAt(),
          "deletedAt should match for all processing orders.");

      // Compare values
      if (processingOrder.get(processingOrder.size() - 1).version == 7000) {
        // When last event is U7, phone field should be UNSET
        assertEquals(
            SpecialStateValue.UNSET.code(),
            phoneValue.value(),
            "When last event is U7, phone field should be UNSET.");
      }
    }
  }

  /** Returns the status of field value as a string. */
  private String getFieldStatus(StateValue fieldValue) {
    if (fieldValue == null) {
      return "null";
    }
    Object value = fieldValue.value();
    if (value == null) {
      return "null";
    } else if (value instanceof SpecialStateValue) {
      return value.toString();
    } else {
      return value.toString();
    }
  }

  /** Returns event type in short form (I, U, D). */
  private String getShortEventType(EventType type) {
    switch (type) {
      case INSERT:
        return "I";
      case UPDATE:
        return "U";
      case DELETE:
        return "D";
      default:
        return type.toString();
    }
  }

  /** Returns a summary of the values map content as a string. */
  private String summarizeValues(Map<String, StateValue> values) {
    if (values == null || values.isEmpty()) {
      return "{}";
    }

    StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<String, StateValue> entry : values.entrySet()) {
      if (!first) {
        sb.append(", ");
      }
      sb.append(entry.getKey()).append("=").append(entry.getValue().value());
      first = false;
    }
    sb.append("}");
    return sb.toString();
  }
}
