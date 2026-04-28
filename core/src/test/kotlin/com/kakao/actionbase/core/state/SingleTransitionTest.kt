package com.kakao.actionbase.core.state

import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest
import com.kakao.actionbase.test.documentations.params.TableSource
import com.kakao.actionbase.test.handleSpecialValue
import com.kakao.actionbase.test.state.StateTestFixture
import com.kakao.actionbase.test.toBooleanFlexible
import com.kakao.actionbase.test.toEventType
import com.kakao.actionbase.test.toStateValue

import org.junit.jupiter.api.assertThrows

class SingleTransitionTest {
    @ObjectSourceParameterizedTest
    @TableSource(
        """
          #    |                                                                 | active | v  | c  | d | name  | age | email  | comment | event | v  | type | name    | age | email    | comment | expected | ex | active | v  | c  | d  | name    | age | email    | comment | n  | a  | e  | c  | size
          #----|-----------------------------------------------------------------|--------|----|----|---|-------|-----|--------|---------|-------|----|------|---------|-----|----------|---------|----------|----|--------|----|----|----|---------|-----|----------|---------|----|----|----|----|-----
          - 1  | Empty state INSERT with required fields only                    | F      | 0  | ~  | ~ | ~     | ~   | ~      | ~       | event | 1  | I    | Alice   | 30  | ~        | ~       | expected | F  | T      | 1  | 1  | ~  | Alice   | 30  | U        | U       | 1  | 1  | 1  | 1  | 4
          - 2  | Empty state INSERT with required + EMAIL                        | F      | 0  | ~  | ~ | ~     | ~   | ~      | ~       | event | 1  | I    | Alice   | 30  | alice@   | ~       | expected | F  | T      | 1  | 1  | ~  | Alice   | 30  | alice@   | U       | 1  | 1  | 1  | 1  | 4
          - 3  | Empty state INSERT with required + COMMENT                      | F      | 0  | ~  | ~ | ~     | ~   | ~      | ~       | event | 1  | I    | Alice   | 30  | ~        | Nice    | expected | F  | T      | 1  | 1  | ~  | Alice   | 30  | U        | Nice    | 1  | 1  | 1  | 1  | 4
          - 4  | Empty state INSERT with all fields                              | F      | 0  | ~  | ~ | ~     | ~   | ~      | ~       | event | 1  | I    | Alice   | 30  | alice@   | Nice    | expected | F  | T      | 1  | 1  | ~  | Alice   | 30  | alice@   | Nice    | 1  | 1  | 1  | 1  | 4
          - 5  | INSERT missing required field NAME (Exception)                  | F      | 0  | ~  | ~ | ~     | ~   | ~      | ~       | event | 1  | I    | ~       | 30  | ~        | ~       | expected | T  | ~      | ~  | ~  | ~  | ~       | ~   | ~        | ~       | ~  | ~  | ~  | ~  | ~
          - 6  | INSERT missing required field AGE (Exception)                   | F      | 0  | ~  | ~ | ~     | ~   | ~      | ~       | event | 1  | I    | Alice   | ~   | ~        | ~       | expected | T  | ~      | ~  | ~  | ~  | ~       | ~   | ~        | ~       | ~  | ~  | ~  | ~  | ~
          - 7  | INSERT missing all required fields (Exception)                  | F      | 0  | ~  | ~ | ~     | ~   | ~      | ~       | event | 1  | I    | ~       | ~   | ~        | ~       | expected | T  | ~      | ~  | ~  | ~  | ~       | ~   | ~        | ~       | ~  | ~  | ~  | ~  | ~
          - 8  | INSERT with higher version on existing data                     | T      | 5  | 3  | ~ | Bob   | 25  | bob@   | U       | event | 10 | I    | Charlie | 35  | charlie@ | Good    | expected | F  | T      | 10 | 10 | ~  | Charlie | 35  | charlie@ | Good    | 10 | 10 | 10 | 10 | 4
          - 9  | INSERT with lower version on existing data (ignored)            | T      | 10 | 5  | ~ | Alice | 30  | alice@ | Nice    | event | 3  | I    | Bob     | 25  | bob@     | ~       | expected | F  | T      | 10 | 5  | ~  | Alice   | 30  | alice@   | Nice    | 10 | 10 | 10 | 10 | 4
          - 10 | INSERT on deleted state (restore)                               | F      | 8  | 3  | 7 | Alice | 30  | alice@ | ~       | event | 15 | I    | Alice   | 31  | alice@n  | Good    | expected | F  | T      | 15 | 15 | 7  | Alice   | 31  | alice@n  | Good    | 15 | 15 | 15 | 15 | 4
          - 11 | INSERT with same version (overwrite)                            | T      | 5  | 3  | ~ | Alice | 30  | U      | U       | event | 5  | I    | Bob     | 25  | bob@     | ~       | expected | F  | T      | 5  | 5  | ~  | Bob     | 25  | bob@     | U       | 5  | 5  | 5  | 5  | 4
          - 12 | INSERT with same version on deleted state (restore + overwrite) | F      | 5  | 3  | 5 | D     | D   | D      | D       | event | 5  | I    | Alice   | 30  | alice@   | Nice    | expected | F  | T      | 5  | 5  | ~  | Alice   | 30  | alice@   | Nice    | 5  | 5  | 5  | 5  | 4
          - 13 | UPDATE NAME only on active state                                | T      | 5  | 3  | ~ | Alice | 30  | alice@ | Nice    | event | 10 | U    | Bob     | ~   | ~        | ~       | expected | F  | T      | 10 | 3  | ~  | Bob     | 30  | alice@   | Nice    | 10 | 5  | 5  | 5  | 4
          - 14 | UPDATE AGE only on active state                                 | T      | 5  | 3  | ~ | Alice | 30  | alice@ | Nice    | event | 10 | U    | ~       | 35  | ~        | ~       | expected | F  | T      | 10 | 3  | ~  | Alice   | 35  | alice@   | Nice    | 5  | 10 | 5  | 5  | 4
          - 15 | UPDATE EMAIL only on active state                               | T      | 5  | 3  | ~ | Alice | 30  | alice@ | Nice    | event | 10 | U    | ~       | ~   | alice@n  | ~       | expected | F  | T      | 10 | 3  | ~  | Alice   | 30  | alice@n  | Nice    | 5  | 5  | 10 | 5  | 4
          - 16 | UPDATE COMMENT only on active state                             | T      | 5  | 3  | ~ | Alice | 30  | alice@ | Nice    | event | 10 | U    | ~       | ~   | ~        | Good    | expected | F  | T      | 10 | 3  | ~  | Alice   | 30  | alice@   | Good    | 5  | 5  | 5  | 10 | 4
          - 17 | UPDATE NAME + AGE on active state                               | T      | 5  | 3  | ~ | Alice | 30  | alice@ | Nice    | event | 10 | U    | Bob     | 35  | ~        | ~       | expected | F  | T      | 10 | 3  | ~  | Bob     | 35  | alice@   | Nice    | 10 | 10 | 5  | 5  | 4
          - 18 | UPDATE EMAIL + COMMENT on active state                          | T      | 5  | 3  | ~ | Alice | 30  | alice@ | Nice    | event | 10 | U    | ~       | ~   | alice@n  | Good    | expected | F  | T      | 10 | 3  | ~  | Alice   | 30  | alice@n  | Good    | 5  | 5  | 10 | 10 | 4
          - 19 | UPDATE all fields on active state                               | T      | 5  | 3  | ~ | Alice | 30  | alice@ | Nice    | event | 10 | U    | Bob     | 35  | bob@n    | Best    | expected | F  | T      | 10 | 3  | ~  | Bob     | 35  | bob@n    | Best    | 10 | 10 | 10 | 10 | 4
          - 20 | UPDATE on inactive state (data processing same)                 | F      | 8  | 3  | 7 | Alice | 30  | alice@ | Nice    | event | 10 | U    | Bob     | 35  | ~        | ~       | expected | F  | F      | 10 | 3  | 7  | Bob     | 35  | alice@   | Nice    | 10 | 10 | 8  | 8  | 4
          - 21 | UPDATE with lower version (ignored)                             | T      | 10 | 5  | ~ | Alice | 30  | alice@ | Nice    | event | 3  | U    | Bob     | 25  | ~        | ~       | expected | F  | T      | 10 | 5  | ~  | Alice   | 30  | alice@   | Nice    | 10 | 10 | 10 | 10 | 4
          - 22 | UPDATE on partial fields state                                  | T      | 5  | 3  | ~ | Alice | 30  | U      | U       | event | 10 | U    | ~       | ~   | alice@   | Nice    | expected | F  | T      | 10 | 3  | ~  | Alice   | 30  | alice@   | Nice    | 5  | 5  | 10 | 10 | 4
          - 23 | UPDATE with same version (overwrite)                            | T      | 5  | 3  | ~ | Alice | 30  | alice@ | Nice    | event | 5  | U    | Bob     | 25  | ~        | ~       | expected | F  | T      | 5  | 3  | ~  | Bob     | 25  | alice@   | Nice    | 5  | 5  | 5  | 5  | 4
          - 24 | UPDATE to add fields on missing field state                     | T      | 5  | 3  | ~ | Alice | 30  | U      | U       | event | 10 | U    | ~       | ~   | alice@   | Nice    | expected | F  | T      | 10 | 3  | ~  | Alice   | 30  | alice@   | Nice    | 5  | 5  | 10 | 10 | 4
          - 25 | UPDATE on deleted state (data processing same)                  | F      | 8  | 3  | 7 | Alice | 30  | alice@ | Nice    | event | 15 | U    | Bob     | 35  | ~        | ~       | expected | F  | F      | 15 | 3  | 7  | Bob     | 35  | alice@   | Nice    | 15 | 15 | 8  | 8  | 4
          - 26 | UPDATE on deleted data (D values)                               | F      | 8  | 3  | 7 | D     | D   | D      | D       | event | 15 | U    | Bob     | 35  | ~        | ~       | expected | F  | F      | 15 | 3  | 7  | Bob     | 35  | D        | D       | 15 | 15 | 8  | 8  | 4
          - 27 | DELETE on active state (all properties to D)                    | T      | 5  | 3  | ~ | Alice | 30  | alice@ | Nice    | event | 10 | D    | ~       | ~   | ~        | ~       | expected | F  | F      | 10 | 3  | 10 | D       | D   | D        | D       | 10 | 10 | 10 | 10 | 4
          - 28 | DELETE on already deleted state (higher version)                | F      | 8  | 3  | 7 | Alice | 30  | alice@ | Nice    | event | 10 | D    | ~       | ~   | ~        | ~       | expected | F  | F      | 10 | 3  | 10 | D       | D   | D        | D       | 10 | 10 | 10 | 10 | 4
          - 29 | DELETE on deleted state with lower version (ignored)            | F      | 10 | 5  | 8 | Alice | 30  | alice@ | Nice    | event | 3  | D    | ~       | ~   | ~        | ~       | expected | F  | F      | 10 | 5  | 8  | Alice   | 30  | alice@   | Nice    | 10 | 10 | 10 | 10 | 4
          - 30 | DELETE on empty state (create D properties)                     | F      | 0  | ~  | ~ | ~     | ~   | ~      | ~       | event | 5  | D    | ~       | ~   | ~        | ~       | expected | F  | F      | 5  | ~  | 5  | D       | D   | D        | D       | 5  | 5  | 5  | 5  | 4
          - 31 | DELETE with same version (overwrite)                            | T      | 5  | 3  | ~ | Alice | 30  | alice@ | Nice    | event | 5  | D    | ~       | ~   | ~        | ~       | expected | F  | F      | 5  | 3  | 5  | D       | D   | D        | D       | 5  | 5  | 5  | 5  | 4
          - 32 | DELETE on partial fields state                                  | T      | 5  | 3  | ~ | Alice | 30  | U      | U       | event | 10 | D    | ~       | ~   | ~        | ~       | expected | F  | F      | 10 | 3  | 10 | D       | D   | D        | D       | 10 | 10 | 10 | 10 | 4
          - 33 | INSERT starting with version 1                                  | F      | 0  | ~  | ~ | ~     | ~   | ~      | ~       | event | 1  | I    | Alice   | 30  | ~        | ~       | expected | F  | T      | 1  | 1  | ~  | Alice   | 30  | U        | U       | 1  | 1  | 1  | 1  | 4
          - 34 | Restore scenario after deletion (INSERT after DELETE)           | F      | 10 | 5  | 8 | Alice | 30  | alice@ | Nice    | event | 15 | I    | Bob     | 25  | bob@n    | Good    | expected | F  | T      | 15 | 15 | 8  | Bob     | 25  | bob@n    | Good    | 15 | 15 | 15 | 15 | 4
          - 35 | Simple DELETE on complex state                                  | T      | 10 | 5  | ~ | Alice | 30  | alice@ | Good    | event | 20 | D    | ~       | ~   | ~        | ~       | expected | F  | F      | 20 | 5  | 20 | D       | D   | D        | D       | 20 | 20 | 20 | 20 | 4
          - 36 | Partial field UPDATE on all fields state                        | T      | 5  | 3  | ~ | Alice | 30  | alice@ | Nice    | event | 10 | U    | Bob     | ~   | ~        | ~       | expected | F  | T      | 10 | 3  | ~  | Bob     | 30  | alice@   | Nice    | 10 | 5  | 5  | 5  | 4
          - 37 | UPDATE after INSERT (consecutive versions)                      | T      | 5  | 3  | ~ | Alice | 30  | U      | U       | event | 6  | U    | ~       | ~   | alice@   | Nice    | expected | F  | T      | 6  | 3  | ~  | Alice   | 30  | alice@   | Nice    | 5  | 5  | 6  | 6  | 4
          - 38 | Complete INSERT on partial fields state                         | T      | 5  | 3  | ~ | Alice | 30  | U      | U       | event | 10 | I    | Bob     | 25  | bob@     | Good    | expected | F  | T      | 10 | 10 | ~  | Bob     | 25  | bob@     | Good    | 10 | 10 | 10 | 10 | 4
          - 39 | INSERT restore from D state fields                              | F      | 8  | 3  | 7 | D     | D   | D      | D       | event | 15 | I    | Alice   | 30  | alice@   | Nice    | expected | F  | T      | 15 | 15 | 7  | Alice   | 30  | alice@   | Nice    | 15 | 15 | 15 | 15 | 4
          - 40 | INSERT attempt with lower version on higher version (ignored)   | T      | 50 | 40 | ~ | Alice | 30  | alice@ | Nice    | event | 20 | I    | Bob     | 25  | bob@     | Good    | expected | F  | T      | 50 | 40 | ~  | Alice   | 30  | alice@   | Nice    | 50 | 50 | 50 | 50 | 4
          - 41 | UPDATE attempt on version 0 (processed even without data)       | F      | 0  | ~  | ~ | ~     | ~   | ~      | ~       | event | 5  | U    | Alice   | 30  | ~        | ~       | expected | F  | F      | 5  | ~  | ~  | Alice   | 30  | ~        | ~       | 5  | 5  | ~  | ~  | 2
        """,
    )
    fun `test single transition`(
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
                    state.transit(event, fields)
                }
            } else {
                // when - State Transition
                val nextState = state.transit(event, fields)

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
}
