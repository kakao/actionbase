package com.kakao.actionbase.core.state

import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest
import com.kakao.actionbase.test.documentations.params.TableSource
import com.kakao.actionbase.test.handleSpecialValue
import com.kakao.actionbase.test.state.StateTestFixture
import com.kakao.actionbase.test.toBooleanFlexible
import com.kakao.actionbase.test.toEventSequence
import com.kakao.actionbase.test.toEventType
import com.kakao.actionbase.test.toStateValue

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.assertThrows

/**
 * Golden tests for `transit(insertMerge = true)`: INSERT merges like UPDATE (omitted kept, null
 * clears) and only activates the row. Snapshot goldens live in [SingleTransitionTest]/[ProcessingOrderTest].
 */
class InsertMergeTest {
    @ObjectSourceParameterizedTest
    @TableSource(
        """
          #    |                                                                 | active | v  | c  | d | name  | age | email  | comment | event | v  | type | name    | age | email    | comment | expected | ex | active | v  | c  | d  | name    | age | email    | comment | n  | a  | e  | c  | size
          #----|-----------------------------------------------------------------|--------|----|----|---|-------|-----|--------|---------|-------|----|------|---------|-----|----------|---------|----------|----|--------|----|----|----|---------|-----|----------|---------|----|----|----|----|-----
          - 1  | Empty state INSERT keeps omitted fields absent                  | F      | 0  | ~  | ~ | ~     | ~   | ~      | ~       | event | 1  | I    | Alice   | 30  | ~        | ~       | expected | F  | T      | 1  | 1  | ~  | Alice   | 30  | ~        | ~       | 1  | 1  | ~  | ~  | 2
          - 2  | Empty state INSERT with required + EMAIL                        | F      | 0  | ~  | ~ | ~     | ~   | ~      | ~       | event | 1  | I    | Alice   | 30  | alice@   | ~       | expected | F  | T      | 1  | 1  | ~  | Alice   | 30  | alice@   | ~       | 1  | 1  | 1  | ~  | 3
          - 3  | Empty state INSERT with required + COMMENT                      | F      | 0  | ~  | ~ | ~     | ~   | ~      | ~       | event | 1  | I    | Alice   | 30  | ~        | Nice    | expected | F  | T      | 1  | 1  | ~  | Alice   | 30  | ~        | Nice    | 1  | 1  | ~  | 1  | 3
          - 4  | INSERT missing required field NAME on empty state (Exception)   | F      | 0  | ~  | ~ | ~     | ~   | ~      | ~       | event | 1  | I    | ~       | 30  | ~        | ~       | expected | T  | ~      | ~  | ~  | ~  | ~       | ~   | ~        | ~       | ~  | ~  | ~  | ~  | ~
          - 5  | Partial INSERT on active state keeps omitted fields (fan-in)    | T      | 5  | 3  | ~ | Alice | 30  | alice@ | Nice    | event | 10 | I    | Bob     | ~   | ~        | ~       | expected | F  | T      | 10 | 10 | ~  | Bob     | 30  | alice@   | Nice    | 10 | 5  | 5  | 5  | 4
          - 6  | Reactivating INSERT keeps tombstones on omitted fields          | F      | 8  | 3  | 7 | D     | D   | D      | D       | event | 15 | I    | Bob     | 25  | ~        | ~       | expected | F  | T      | 15 | 15 | 7  | Bob     | 25  | D        | D       | 15 | 15 | 8  | 8  | 4
        """,
    )
    @DisplayName("Single transition with INSERT_MERGE")
    fun `single transition with insert merge`(
        testIndex: Int,
        testName: String,
        initialActive: String,
        initialVersion: Long,
        initialCreatedAt: Long?,
        initialDeletedAt: Long?,
        initialName: String?,
        initialAge: String?,
        initialEmail: String?,
        initialComment: String?,
        eventMark: String,
        eventVersion: Long,
        eventType: String,
        eventName: String?,
        eventAge: String?,
        eventEmail: String?,
        eventComment: String?,
        expectedMark: String,
        expectedException: String,
        expectedActive: String?,
        expectedVersion: Long?,
        expectedCreatedAt: Long?,
        expectedDeletedAt: Long?,
        expectedName: String?,
        expectedAge: String?,
        expectedEmail: String?,
        expectedComment: String?,
        expectedNameVersion: Long?,
        expectedAgeVersion: Long?,
        expectedEmailVersion: Long?,
        expectedCommentVersion: Long?,
        expectedPropertiesSize: Int?,
    ) {
        with(StateTestFixture) {
            // given - Initial State
            val state =
                State.createNotNull(
                    active = initialActive.toBooleanFlexible(),
                    version = initialVersion,
                    createdAt = initialCreatedAt,
                    deletedAt = initialDeletedAt,
                    initialName?.let { NAME_KEY to it.handleSpecialValue().toStateValue(initialVersion) },
                    initialAge?.let { AGE_KEY to it.handleSpecialValue { toInt() }.toStateValue(initialVersion) },
                    initialEmail?.let { EMAIL_KEY to it.handleSpecialValue().toStateValue(initialVersion) },
                    initialComment?.let { COMMENT_KEY to it.handleSpecialValue().toStateValue(initialVersion) },
                )

            // given - Event
            val event =
                Event.createNotNull(
                    type = eventType.toEventType(),
                    version = eventVersion,
                    eventName?.let { NAME_KEY to it },
                    eventAge?.let { AGE_KEY to it.toInt() },
                    eventEmail?.let { EMAIL_KEY to it },
                    eventComment?.let { COMMENT_KEY to it },
                )

            if (expectedException.toBooleanFlexible()) {
                // when - State Transition
                assertThrows<Exception> {
                    state.transit(event, fields, insertMerge = true)
                }
            } else {
                // when - State Transition
                val nextState = state.transit(event, fields, insertMerge = true)

                // then
                assertAll(
                    nextState,
                    expectedActive,
                    expectedVersion,
                    expectedCreatedAt,
                    expectedDeletedAt,
                    expectedName,
                    expectedAge,
                    expectedEmail,
                    expectedComment,
                    expectedNameVersion,
                    expectedAgeVersion,
                    expectedEmailVersion,
                    expectedCommentVersion,
                    expectedPropertiesSize,
                )
            }
        }
    }

    @ObjectSourceParameterizedTest
    @TableSource(
        """
          #                    | v | n | a | e | c | c | d | a | n | a | e | c | size
          - I1                 | 1 | 1 | 1 | ~ | ~ | 1 | ~ | T | n | 7 | ~ | ~ | 2
          - A1                 | 1 | ~ | 1 | ~ | ~ | ~ | ~ | F | ~ | 8 | ~ | ~ | 1
          - E1                 | 1 | ~ | ~ | 1 | ~ | ~ | ~ | F | ~ | ~ | e | ~ | 1
          - C1                 | 1 | ~ | ~ | ~ | 1 | ~ | ~ | F | ~ | ~ | ~ | c | 1
          - D1                 | 1 | 1 | 1 | 1 | 1 | ~ | 1 | F | D | D | D | D | 4

          # insert and update both merge; only insert refreshes createdAt/version
          - I1; A1             | 1 | 1 | 1 | ~ | ~ | 1 | ~ | T | n | 8 | ~ | ~ | 2   # UPDATE wins on age
          - A1; I1             | 1 | 1 | 1 | ~ | ~ | 1 | ~ | T | n | 7 | ~ | ~ | 2   # INSERT wins on age
          - I1; A1; E1; C1     | 1 | 1 | 1 | 1 | 1 | 1 | ~ | T | n | 8 | e | c | 4   # UPDATE wins on age
          - A1; I1; E1; C1     | 1 | 1 | 1 | 1 | 1 | 1 | ~ | T | n | 7 | e | c | 4   # INSERT wins on age
          - A1; E1; I1; C1     | 1 | 1 | 1 | 1 | 1 | 1 | ~ | T | n | 7 | e | c | 4   # INSERT wins on age
          - A1; E1; C1; I1     | 1 | 1 | 1 | 1 | 1 | 1 | ~ | T | n | 7 | e | c | 4   # INSERT wins on age

          # normal case (all values are updated)
          - I1; A2; E2; C2     | 2 | 1 | 2 | 2 | 2 | 1 | ~ | T | n | 8 | e | c | 4
          - A2; I1; E2; C2     | 2 | 1 | 2 | 2 | 2 | 1 | ~ | T | n | 8 | e | c | 4
          - A2; E2; I1; C2     | 2 | 1 | 2 | 2 | 2 | 1 | ~ | T | n | 8 | e | c | 4
          - A2; E2; C2; I1     | 2 | 1 | 2 | 2 | 2 | 1 | ~ | T | n | 8 | e | c | 4

          # insert merges: it refreshes name/age and version but does not clobber fields it omits
          - I2; A1; E1; C1     | 2 | 2 | 2 | 1 | 1 | 2 | ~ | T | n | 7 | e | c | 4
          - A1; I2; E1; C1     | 2 | 2 | 2 | 1 | 1 | 2 | ~ | T | n | 7 | e | c | 4
          - A1; E1; I2; C1     | 2 | 2 | 2 | 1 | 1 | 2 | ~ | T | n | 7 | e | c | 4
          - A1; E1; C1; I2     | 2 | 2 | 2 | 1 | 1 | 2 | ~ | T | n | 7 | e | c | 4

          # overwrite by delete
          - D1; I1             | 1 | 1 | 1 | 1 | 1 | 1 | ~ | T | n | 7 | D | D | 4   # INSERT reactivates; omitted email/comment keep the tombstone
          - I1; D1             | 1 | 1 | 1 | 1 | 1 | ~ | 1 | F | D | D | D | D | 4   # DELETE wins
          - D1; I1; A1; E1; C1 | 1 | 1 | 1 | 1 | 1 | 1 | ~ | T | n | 8 | e | c | 4   # INSERT wins
          - I1; D1; A1; E1; C1 | 1 | 1 | 1 | 1 | 1 | ~ | 1 | F | D | 8 | e | c | 4   # DELETE wins
          - I1; A1; D1; E1; C1 | 1 | 1 | 1 | 1 | 1 | ~ | 1 | F | D | D | e | c | 4   # DELETE wins
          - I1; A1; E1; D1; C1 | 1 | 1 | 1 | 1 | 1 | ~ | 1 | F | D | D | D | c | 4   # DELETE wins
          - I1; A1; E1; C1; D1 | 1 | 1 | 1 | 1 | 1 | ~ | 1 | F | D | D | D | D | 4   # DELETE wins

          # pairs
          - I1; E1             | 1 | 1 | 1 | 1 | ~ | 1 | ~ | T | n | 7 | e | ~ | 3
          - E1; I1             | 1 | 1 | 1 | 1 | ~ | 1 | ~ | T | n | 7 | e | ~ | 3
          - I1; C1             | 1 | 1 | 1 | ~ | 1 | 1 | ~ | T | n | 7 | ~ | c | 3
          - C1; I1             | 1 | 1 | 1 | ~ | 1 | 1 | ~ | T | n | 7 | ~ | c | 3

          # update comment to null
          - I1; C1             | 1 | 1 | 1 | ~ | 1 | 1 | ~ | T | n | 7 | ~ | c | 3
          - I1; C1; N1         | 1 | 1 | 1 | ~ | 1 | 1 | ~ | T | n | 7 | ~ | U | 3
          - I1; N1; C1         | 1 | 1 | 1 | ~ | 1 | 1 | ~ | T | n | 7 | ~ | c | 3

          # normal case (set comment to null)
          - I1; C2; N3         | 3 | 1 | 1 | ~ | 3 | 1 | ~ | T | n | 7 | ~ | U | 3
          - I1; N3; C2         | 3 | 1 | 1 | ~ | 3 | 1 | ~ | T | n | 7 | ~ | U | 3
          - C2; I1; N3         | 3 | 1 | 1 | ~ | 3 | 1 | ~ | T | n | 7 | ~ | U | 3
          - C2; N3; I1         | 3 | 1 | 1 | ~ | 3 | 1 | ~ | T | n | 7 | ~ | U | 3
          - N3; I1; C2         | 3 | 1 | 1 | ~ | 3 | 1 | ~ | T | n | 7 | ~ | U | 3
          - N3; C2; I1         | 3 | 1 | 1 | ~ | 3 | 1 | ~ | T | n | 7 | ~ | U | 3

          # normal case (set comment to c)
          - I1; N2; C3         | 3 | 1 | 1 | ~ | 3 | 1 | ~ | T | n | 7 | ~ | c | 3

          # eventual consistency (these cases are covered by StateCompanion Test)
          - I1; A2             | 2 | 1 | 2 | ~ | ~ | 1 | ~ | T | n | 8 | ~ | ~ | 2
          - A2; I1             | 2 | 1 | 2 | ~ | ~ | 1 | ~ | T | n | 8 | ~ | ~ | 2

          # ISSUE-3233 see [com.kakao.actionbase.v2.engine.IssueSpec]
          # under merge semantics, an insert that omits "c" no longer invalidates it
          - I2; C1             | 2 | 2 | 2 | ~ | 1 | 2 | ~ | T | n | 7 | ~ | c | 3
          - C1; I2             | 2 | 2 | 2 | ~ | 1 | 2 | ~ | T | n | 7 | ~ | c | 3
        """,
    )
    @DisplayName("Processing order with INSERT_MERGE")
    fun `processing order with insert merge`(
        notation: String,
        expectedVersion: Long,
        expectedNameVersion: Long?,
        expectedAgeVersion: Long?,
        expectedEmailVersion: Long?,
        expectedCommentVersion: Long?,
        expectedCreatedAt: Long?,
        expectedDeletedAt: Long?,
        expectedActive: String,
        expectedName: String?,
        expectedAge: String?,
        expectedEmail: String?,
        expectedComment: String?,
        expectedPropertiesSize: Int,
    ) {
        with(StateTestFixture) {
            // given
            val processingSequence =
                notation.toEventSequence { event, version ->
                    when (event) {
                        "I" -> insertEvent.copy(version = version)
                        "A" -> updateAgeEvent.copy(version = version)
                        "E" -> updateEmailEvent.copy(version = version)
                        "C" -> updateCommentEvent.copy(version = version)
                        "N" -> updateCommentNullEvent.copy(version = version)
                        "D" -> deleteEvent.copy(version = version)
                        else -> throw IllegalArgumentException("Unknown event type: $event")
                    }
                }

            // when
            val state =
                processingSequence.fold(State.initial) { state, event ->
                    state.transit(event, StateTestFixture.fields, insertMerge = true)
                }

            // then
            assertAll(
                state,
                expectedActive,
                expectedVersion,
                expectedCreatedAt,
                expectedDeletedAt,
                expectedName,
                expectedAge,
                expectedEmail,
                expectedComment,
                expectedNameVersion,
                expectedAgeVersion,
                expectedEmailVersion,
                expectedCommentVersion,
                expectedPropertiesSize,
            )
        }
    }

    companion object {
        private const val VERSION = 0L
        private const val INSERT_NAME_VALUE = "n"
        private const val INSERT_AGE_VALUE = 7
        private const val UPDATE_AGE_VALUE = 8
        private const val UPDATE_EMAIL_VALUE = "e"
        private const val UPDATE_COMMENT_VALUE = "c"

        private val insertEvent =
            with(StateTestFixture) {
                Event.create(
                    EventType.INSERT,
                    VERSION,
                    NAME_KEY to INSERT_NAME_VALUE,
                    AGE_KEY to INSERT_AGE_VALUE,
                )
            }

        private val updateAgeEvent =
            with(StateTestFixture) {
                Event.create(
                    EventType.UPDATE,
                    VERSION,
                    AGE_KEY to UPDATE_AGE_VALUE,
                )
            }

        private val updateEmailEvent =
            with(StateTestFixture) {
                Event.create(
                    EventType.UPDATE,
                    VERSION,
                    EMAIL_KEY to UPDATE_EMAIL_VALUE,
                )
            }

        private val updateCommentEvent =
            with(StateTestFixture) {
                Event.create(
                    EventType.UPDATE,
                    VERSION,
                    COMMENT_KEY to UPDATE_COMMENT_VALUE,
                )
            }

        private val updateCommentNullEvent =
            with(StateTestFixture) {
                Event.create(
                    EventType.UPDATE,
                    VERSION,
                    COMMENT_KEY to null,
                )
            }

        private val deleteEvent = Event.create(EventType.DELETE, VERSION)
    }
}
