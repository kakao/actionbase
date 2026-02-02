package com.kakao.actionbase.core.java.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.kakao.actionbase.core.java.state.base.ImmutableBaseEvent;
import com.kakao.actionbase.core.java.state.base.StateCompanion;
import com.kakao.actionbase.core.java.types.DataType;
import com.kakao.actionbase.core.java.types.ImmutableStructType;
import com.kakao.actionbase.core.java.types.StructType;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProcessingTimeTest {

  private static final long VERSION = 0L;

  private static final String NAME_KEY = "name";

  private static final String AGE_KEY = "age";

  private static final String EMAIL_KEY = "email";

  private static final String COMMENT_KEY = "comment";

  private static final String INSERT_NAME_VALUE = "n";

  private static final int INSERT_AGE_VALUE = 7;

  private static final int UPDATE_AGE_VALUE = 8;

  private static final String UPDATE_EMAIL_VALUE = "e";

  private static final String UPDATE_COMMENT_VALUE = "c";

  private static final String UNSET = "U";

  private static final String DELETED = "D";

  StructType schema;

  State initialState;

  ImmutableBaseEvent insertEvent;

  ImmutableBaseEvent updateAgeEvent;

  ImmutableBaseEvent updateEmailEvent;

  ImmutableBaseEvent updateCommentEvent;

  ImmutableBaseEvent updateCommentNullEvent;

  ImmutableBaseEvent deleteEvent;

  @BeforeEach
  void setUp() {
    schema =
        ImmutableStructType.builder()
            .addField(NAME_KEY, DataType.STRING, false)
            .addField(AGE_KEY, DataType.INT, false)
            .addField(EMAIL_KEY, DataType.STRING, true)
            .addField(COMMENT_KEY, DataType.STRING, true)
            .build();

    initialState = StateCompanion.initialOf(schema);

    insertEvent =
        ImmutableBaseEvent.builder()
            .type(EventType.INSERT)
            .putProperties(NAME_KEY, INSERT_NAME_VALUE)
            .putProperties(AGE_KEY, INSERT_AGE_VALUE)
            .version(VERSION)
            .build();

    updateAgeEvent =
        ImmutableBaseEvent.builder()
            .type(EventType.UPDATE)
            .putProperties(AGE_KEY, UPDATE_AGE_VALUE)
            .version(VERSION)
            .build();

    updateEmailEvent =
        ImmutableBaseEvent.builder()
            .type(EventType.UPDATE)
            .putProperties(EMAIL_KEY, UPDATE_EMAIL_VALUE)
            .version(VERSION)
            .build();

    updateCommentEvent =
        ImmutableBaseEvent.builder()
            .type(EventType.UPDATE)
            .putProperties(COMMENT_KEY, UPDATE_COMMENT_VALUE)
            .version(VERSION)
            .build();

    updateCommentNullEvent =
        ImmutableBaseEvent.builder()
            .type(EventType.UPDATE)
            .putProperties(COMMENT_KEY, null)
            .version(VERSION)
            .build();

    deleteEvent = ImmutableBaseEvent.builder().type(EventType.DELETE).version(VERSION).build();
  }

  private List<Event> processSequenceOf(String events) {
    return Arrays.stream(events.split(";"))
        .map(
            s -> {
              char[] parts = s.trim().toCharArray();
              if (parts.length != 2) {
                throw new IllegalArgumentException(
                    "Event must be in format 'event:version' but was: " + s);
              }
              String event = String.valueOf(parts[0]);
              long version = Long.parseLong(String.valueOf(parts[1]));
              return eventOf(event, version);
            })
        .collect(Collectors.toList());
  }

  private ImmutableBaseEvent eventOf(String event, long version) {
    if ("I".equals(event)) {
      return insertEvent.withVersion(version);
    } else if ("A".equals(event)) {
      return updateAgeEvent.withVersion(version);
    } else if ("E".equals(event)) {
      return updateEmailEvent.withVersion(version);
    } else if ("C".equals(event)) {
      return updateCommentEvent.withVersion(version);
    } else if ("N".equals(event)) {
      return updateCommentNullEvent.withVersion(version);
    } else if ("D".equals(event)) {
      return deleteEvent.withVersion(version);
    } else {
      throw new IllegalArgumentException("Unknown event type: " + event);
    }
  }

  private State transitAll(State initialState, List<Event> events, StructType schema) {
    State state = initialState;
    System.out.println("initial: " + state);
    for (Event event : events) {
      state = StateCompanion.transit(state, event, schema);
      System.out.println("after transit: " + state);
    }
    return state;
  }

  private void checkVersion(
      State state,
      long expectedVersion,
      Long expectedNameVersion,
      Long expectedAgeVersion,
      Long expectedEmailVersion,
      Long expectedCommentVersion,
      Long expectedCreatedAt,
      Long expectedDeletedAt,
      boolean expectedActive) {
    assertEquals(expectedVersion, state.version());

    if (expectedNameVersion == null) {
      assertNull(state.properties().get(NAME_KEY));
    } else {
      assertEquals(expectedNameVersion, state.properties().get(NAME_KEY).version());
    }
    if (expectedAgeVersion == null) {
      assertNull(state.properties().get(AGE_KEY));
    } else {
      assertEquals(expectedAgeVersion, state.properties().get(AGE_KEY).version());
    }
    if (expectedEmailVersion == null) {
      assertNull(state.properties().get(EMAIL_KEY));
    } else {
      assertEquals(expectedEmailVersion, state.properties().get(EMAIL_KEY).version());
    }
    if (expectedCommentVersion == null) {
      assertNull(state.properties().get(COMMENT_KEY));
    } else {
      assertEquals(expectedCommentVersion, state.properties().get(COMMENT_KEY).version());
    }

    assertEquals(expectedCreatedAt, state.createdAt());
    assertEquals(expectedDeletedAt, state.deletedAt());
    assertEquals(expectedActive, state.active());
  }

  private String replaceShortenSpecialValues(String value) {
    if (value == null) {
      return null;
    }
    return value
        .replace(UNSET, SpecialStateValue.UNSET.code())
        .replace(DELETED, SpecialStateValue.DELETED.code());
  }

  private void checkValues(
      State state,
      String expectedName,
      String expectedAge,
      String expectedEmail,
      String expectedComment) {

    if (expectedName == null) {
      assertNull(state.properties().get(NAME_KEY));
    } else {
      assertEquals(
          replaceShortenSpecialValues(expectedName), state.properties().get(NAME_KEY).value());
    }
    if (expectedAge == null) {
      assertNull(state.properties().get(AGE_KEY));
    } else {
      assertEquals(
          replaceShortenSpecialValues(expectedAge),
          state.properties().get(AGE_KEY).value().toString());
    }
    if (expectedEmail == null) {
      assertNull(state.properties().get(EMAIL_KEY));
    } else {
      assertEquals(
          replaceShortenSpecialValues(expectedEmail), state.properties().get(EMAIL_KEY).value());
    }
    if (expectedComment == null) {
      assertNull(state.properties().get(COMMENT_KEY));
    } else {
      assertEquals(
          replaceShortenSpecialValues(expectedComment),
          state.properties().get(COMMENT_KEY).value());
    }
  }

  /**
   *
   *
   * <pre>
   * I1 interpretation
   *  - I: Insert event
   *  - 1: Version 1
   *
   * Event types
   *  - I: Insert event
   *  - A: Age update event
   *  - E: Email update event
   *  - C: Comment update event
   *  - N: Comment(= null) update event
   *  - D: Delete event
   * </pre>
   */
  @ParameterizedTest(name = "Processing - {0}")
  @CsvSource(
      value = {
        //                        v, n, a, e, c,    c, d, a,    n, a, e, c
        "I1                     , 1, 1, 1, 1, 1,    1, #, T,    n, 7, U, U",
        "A1                     , 1, #, 1, #, #,    #, #, F,    #, 8, #, #",
        "E1                     , 1, #, #, 1, #,    #, #, F,    #, #, e, #",
        "C1                     , 1, #, #, #, 1,    #, #, F,    #, #, #, c",
        "D1                     , 1, 1, 1, 1, 1,    #, 1, F,    D, D, D, D",

        // overwrite by insert
        "I1; A1                 , 1, 1, 1, 1, 1,    1, #, T,    n, 8, U, U", // UPDATE wins
        "A1; I1                 , 1, 1, 1, 1, 1,    1, #, T,    n, 7, U, U", // INSERT wins
        "I1; A1; E1; C1         , 1, 1, 1, 1, 1,    1, #, T,    n, 8, e, c", // UPDATE wins
        "A1; I1; E1; C1         , 1, 1, 1, 1, 1,    1, #, T,    n, 7, e, c", // INSERT wins
        "A1; E1; I1; C1         , 1, 1, 1, 1, 1,    1, #, T,    n, 7, e, c", // INSERT wins
        "A1; E1; C1; I1         , 1, 1, 1, 1, 1,    1, #, T,    n, 7, e, c", // INSERT wins

        // normal case (all values are updated)
        "I1; A2; E2; C2         , 2, 1, 2, 2, 2,    1, #, T,    n, 8, e, c",
        "A2; I1; E2; C2         , 2, 1, 2, 2, 2,    1, #, T,    n, 8, e, c",
        "A2; E2; I1; C2         , 2, 1, 2, 2, 2,    1, #, T,    n, 8, e, c",
        "A2; E2; C2; I1         , 2, 1, 2, 2, 2,    1, #, T,    n, 8, e, c",

        // normal case (latest insert wins)
        "I2; A1; E1; C1         , 2, 2, 2, 2, 2,    2, #, T,    n, 7, U, U",
        "A1; I2; E1; C1         , 2, 2, 2, 2, 2,    2, #, T,    n, 7, U, U",
        "A1; E1; I2; C1         , 2, 2, 2, 2, 2,    2, #, T,    n, 7, U, U",
        "A1; E1; C1; I2         , 2, 2, 2, 2, 2,    2, #, T,    n, 7, U, U",

        // overwrite by delete
        "D1; I1                 , 1, 1, 1, 1, 1,    1, #, T,    n, 7, U, U", // INSERT wins
        "I1; D1                 , 1, 1, 1, 1, 1,    #, 1, F,    D, D, D, D", // DELETE wins
        "D1; I1; A1; E1; C1     , 1, 1, 1, 1, 1,    1, #, T,    n, 8, e, c", // INSERT wins
        "I1; D1; A1; E1; C1     , 1, 1, 1, 1, 1,    #, 1, F,    D, 8, e, c", // DELETE wins
        "I1; A1; D1; E1; C1     , 1, 1, 1, 1, 1,    #, 1, F,    D, D, e, c", // DELETE wins
        "I1; A1; E1; D1; C1     , 1, 1, 1, 1, 1,    #, 1, F,    D, D, D, c", // DELETE wins
        "I1; A1; E1; C1; D1     , 1, 1, 1, 1, 1,    #, 1, F,    D, D, D, D", // DELETE wins

        // paris
        "I1; E1                 , 1, 1, 1, 1, 1,    1, #, T,    n, 7, e, U",
        "E1; I1                 , 1, 1, 1, 1, 1,    1, #, T,    n, 7, e, U",
        "I1; C1                 , 1, 1, 1, 1, 1,    1, #, T,    n, 7, U, c",
        "C1; I1                 , 1, 1, 1, 1, 1,    1, #, T,    n, 7, U, c",

        // update comment to null
        "I1; C1                 , 1, 1, 1, 1, 1,    1, #, T,    n, 7, U, c",
        "I1; C1; N1             , 1, 1, 1, 1, 1,    1, #, T,    n, 7, U, U",
        "I1; N1; C1             , 1, 1, 1, 1, 1,    1, #, T,    n, 7, U, c",

        // normal case (set comment to null)
        "I1; C2; N3             , 3, 1, 1, 1, 3,    1, #, T,    n, 7, U, U",
        "I1; N3; C2             , 3, 1, 1, 1, 3,    1, #, T,    n, 7, U, U",
        "C2; I1; N3             , 3, 1, 1, 1, 3,    1, #, T,    n, 7, U, U",
        "C2; N3; I1             , 3, 1, 1, 1, 3,    1, #, T,    n, 7, U, U",
        "N3; I1; C2             , 3, 1, 1, 1, 3,    1, #, T,    n, 7, U, U",
        "N3; C2; I1             , 3, 1, 1, 1, 3,    1, #, T,    n, 7, U, U",

        // normal case (set comment to c)
        "I1; N2; C3             , 3, 1, 1, 1, 3,    1, #, T,    n, 7, U, c",

        // eventual consistency (these cases are covered by StateCompanionTest)
        "I1; A2                 , 2, 1, 2, 1, 1,    1, #, T,    n, 8, U, U",
        "A2; I1                 , 2, 1, 2, 1, 1,    1, #, T,    n, 8, U, U",

        // ISSUE-3233 see [com.kakao.actionbase.v2.engine.IssueSpec]
        // in the v2 engine, can not invalidate "c"
        // I2; C1               , 2, 2, 2, 2, 2,    2, #, T,    n, 7, U, U
        // C1; I2               , 2, 2, 2, 2, 2,    2, #, T,    n, 7, U, **c**
        "I2; C1                 , 2, 2, 2, 2, 2,    2, #, T,    n, 7, U, U",
        "C1; I2                 , 2, 2, 2, 2, 2,    2, #, T,    n, 7, U, U",
      },
      nullValues = "#")
  @DisplayName("Processing Time Test")
  void testAndCheckVersion(
      String events,
      Long expectedVersion,
      Long expectedNameVersion,
      Long expectedAgeVersion,
      Long expectedEmailVersion,
      Long expectedCommentVersion,
      Long expectedCreatedAt,
      Long expectedDeletedAt,
      String expectedActive,
      String expectedName,
      String expectedAge,
      String expectedEmail,
      String expectedComment) {
    // given
    List<Event> processingSequence = processSequenceOf(events);

    // when
    State state = transitAll(initialState, processingSequence, schema);

    // then
    checkVersion(
        state,
        expectedVersion,
        expectedNameVersion,
        expectedAgeVersion,
        expectedEmailVersion,
        expectedCommentVersion,
        expectedCreatedAt,
        expectedDeletedAt,
        expectedActive.equals("T"));

    checkValues(state, expectedName, expectedAge, expectedEmail, expectedComment);
  }
}
