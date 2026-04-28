package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest
import com.kakao.actionbase.test.documentations.params.TableSource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested

class MetadataStatusTest {
    @Nested
    inner class MatchesTest {
        @ObjectSourceParameterizedTest
        @TableSource(
            """
            columns: [status, active, expected]
            rows:
              - [ACTIVE,   true,  true]
              - [ACTIVE,   false, false]
              - [INACTIVE, true,  false]
              - [INACTIVE, false, true]
              - [ALL,      true,  true]
              - [ALL,      false, true]
            """,
        )
        fun `matches reflects status against active flag`(
            status: MetadataStatus,
            active: Boolean,
            expected: Boolean,
        ) {
            assertThat(status.matches(active = active)).isEqualTo(expected)
        }
    }
}
