package com.kakao.actionbase.core.state

import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest
import com.kakao.actionbase.test.documentations.params.TableSource
import com.kakao.actionbase.test.state.StateTestFixture
import com.kakao.actionbase.test.toEventSequence

import org.junit.jupiter.api.DisplayName

class ProcessingOrderTest {
    /**
     *
     *
     * ```
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
     * ```
     */
    @ObjectSourceParameterizedTest
    @TableSource(
        """
          #                    | v | n | a | e | c | c | d | a | n | a | e | c | size
          - I1                 | 1 | 1 | 1 | 1 | 1 | 1 | ~ | T | n | 7 | U | U | 4
          - A1                 | 1 | ~ | 1 | ~ | ~ | ~ | ~ | F | ~ | 8 | ~ | ~ | 1
          - E1                 | 1 | ~ | ~ | 1 | ~ | ~ | ~ | F | ~ | ~ | e | ~ | 1
          - C1                 | 1 | ~ | ~ | ~ | 1 | ~ | ~ | F | ~ | ~ | ~ | c | 1
          - D1                 | 1 | 1 | 1 | 1 | 1 | ~ | 1 | F | D | D | D | D | 4

          # overwrite by insert
          - I1; A1             | 1 | 1 | 1 | 1 | 1 | 1 | ~ | T | n | 8 | U | U | 4   # UPDATE wins
          - A1; I1             | 1 | 1 | 1 | 1 | 1 | 1 | ~ | T | n | 7 | U | U | 4   # INSERT wins
          - I1; A1; E1; C1     | 1 | 1 | 1 | 1 | 1 | 1 | ~ | T | n | 8 | e | c | 4   # UPDATE wins
          - A1; I1; E1; C1     | 1 | 1 | 1 | 1 | 1 | 1 | ~ | T | n | 7 | e | c | 4   # INSERT wins
          - A1; E1; I1; C1     | 1 | 1 | 1 | 1 | 1 | 1 | ~ | T | n | 7 | e | c | 4   # INSERT wins
          - A1; E1; C1; I1     | 1 | 1 | 1 | 1 | 1 | 1 | ~ | T | n | 7 | e | c | 4   # INSERT wins

          # normal case (all values are updated)
          - I1; A2; E2; C2     | 2 | 1 | 2 | 2 | 2 | 1 | ~ | T | n | 8 | e | c | 4
          - A2; I1; E2; C2     | 2 | 1 | 2 | 2 | 2 | 1 | ~ | T | n | 8 | e | c | 4
          - A2; E2; I1; C2     | 2 | 1 | 2 | 2 | 2 | 1 | ~ | T | n | 8 | e | c | 4
          - A2; E2; C2; I1     | 2 | 1 | 2 | 2 | 2 | 1 | ~ | T | n | 8 | e | c | 4

          # normal case (latest insert wins)
          - I2; A1; E1; C1     | 2 | 2 | 2 | 2 | 2 | 2 | ~ | T | n | 7 | U | U | 4
          - A1; I2; E1; C1     | 2 | 2 | 2 | 2 | 2 | 2 | ~ | T | n | 7 | U | U | 4
          - A1; E1; I2; C1     | 2 | 2 | 2 | 2 | 2 | 2 | ~ | T | n | 7 | U | U | 4
          - A1; E1; C1; I2     | 2 | 2 | 2 | 2 | 2 | 2 | ~ | T | n | 7 | U | U | 4

          # overwrite by delete
          - D1; I1             | 1 | 1 | 1 | 1 | 1 | 1 | ~ | T | n | 7 | U | U | 4   # INSERT wins
          - I1; D1             | 1 | 1 | 1 | 1 | 1 | ~ | 1 | F | D | D | D | D | 4   # DELETE wins
          - D1; I1; A1; E1; C1 | 1 | 1 | 1 | 1 | 1 | 1 | ~ | T | n | 8 | e | c | 4   # INSERT wins
          - I1; D1; A1; E1; C1 | 1 | 1 | 1 | 1 | 1 | ~ | 1 | F | D | 8 | e | c | 4   # DELETE wins
          - I1; A1; D1; E1; C1 | 1 | 1 | 1 | 1 | 1 | ~ | 1 | F | D | D | e | c | 4   # DELETE wins
          - I1; A1; E1; D1; C1 | 1 | 1 | 1 | 1 | 1 | ~ | 1 | F | D | D | D | c | 4   # DELETE wins
          - I1; A1; E1; C1; D1 | 1 | 1 | 1 | 1 | 1 | ~ | 1 | F | D | D | D | D | 4   # DELETE wins

          # pairs
          - I1; E1             | 1 | 1 | 1 | 1 | 1 | 1 | ~ | T | n | 7 | e | U | 4
          - E1; I1             | 1 | 1 | 1 | 1 | 1 | 1 | ~ | T | n | 7 | e | U | 4
          - I1; C1             | 1 | 1 | 1 | 1 | 1 | 1 | ~ | T | n | 7 | U | c | 4
          - C1; I1             | 1 | 1 | 1 | 1 | 1 | 1 | ~ | T | n | 7 | U | c | 4

          # update comment to null
          - I1; C1             | 1 | 1 | 1 | 1 | 1 | 1 | ~ | T | n | 7 | U | c | 4
          - I1; C1; N1         | 1 | 1 | 1 | 1 | 1 | 1 | ~ | T | n | 7 | U | U | 4
          - I1; N1; C1         | 1 | 1 | 1 | 1 | 1 | 1 | ~ | T | n | 7 | U | c | 4

          # normal case (set comment to null)
          - I1; C2; N3         | 3 | 1 | 1 | 1 | 3 | 1 | ~ | T | n | 7 | U | U | 4
          - I1; N3; C2         | 3 | 1 | 1 | 1 | 3 | 1 | ~ | T | n | 7 | U | U | 4
          - C2; I1; N3         | 3 | 1 | 1 | 1 | 3 | 1 | ~ | T | n | 7 | U | U | 4
          - C2; N3; I1         | 3 | 1 | 1 | 1 | 3 | 1 | ~ | T | n | 7 | U | U | 4
          - N3; I1; C2         | 3 | 1 | 1 | 1 | 3 | 1 | ~ | T | n | 7 | U | U | 4
          - N3; C2; I1         | 3 | 1 | 1 | 1 | 3 | 1 | ~ | T | n | 7 | U | U | 4

          # normal case (set comment to c)
          - I1; N2; C3         | 3 | 1 | 1 | 1 | 3 | 1 | ~ | T | n | 7 | U | c | 4

          # eventual consistency (these cases are covered by StateCompanion Test)
          - I1; A2             | 2 | 1 | 2 | 1 | 1 | 1 | ~ | T | n | 8 | U | U | 4
          - A2; I1             | 2 | 1 | 2 | 1 | 1 | 1 | ~ | T | n | 8 | U | U | 4

          # ISSUE-3233 see [com.kakao.actionbase.v2.engine.IssueSpec]
          # in the v2 engine, can not invalidate "c"
          - I2; C1             | 2 | 2 | 2 | 2 | 2 | 2 | ~ | T | n | 7 | U | U | 4
          - C1; I2             | 2 | 2 | 2 | 2 | 2 | 2 | ~ | T | n | 7 | U | U | 4
        """,
    )
    @DisplayName("Processing Time Test")
    fun `test processing order`(
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
                    state.transit(event, StateTestFixture.fields)
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
