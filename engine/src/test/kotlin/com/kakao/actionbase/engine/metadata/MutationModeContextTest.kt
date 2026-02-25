package com.kakao.actionbase.engine.metadata

import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest

import kotlin.test.assertEquals
import kotlin.test.assertTrue

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows

class MutationModeContextTest {
    @Nested
    @DisplayName("of")
    inner class OfTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            # ASYNC label — default queues
            - label: ASYNC
              request: null
              queue: true

            # ASYNC label — SYNC request overrides to non-queue
            - label: ASYNC
              request: SYNC
              queue: false

            - label: ASYNC
              request: ASYNC
              queue: true

            - label: ASYNC
              request: IGNORE
              queue: true

            # SYNC label — default non-queue
            - label: SYNC
              request: null
              queue: false

            - label: SYNC
              request: SYNC
              queue: false

            # SYNC label — ASYNC request overrides to queue
            - label: SYNC
              request: ASYNC
              queue: true

            - label: SYNC
              request: IGNORE
              queue: true

            # IGNORE label — default queues
            - label: IGNORE
              request: null
              queue: true

            - label: IGNORE
              request: ASYNC
              queue: true

            - label: IGNORE
              request: IGNORE
              queue: true
            """,
        )
        fun `returns correct queue flag`(
            label: String,
            request: String?,
            queue: Boolean,
        ) {
            val requestMode = request?.let { MutationMode.valueOf(it) }
            val result = MutationModeContext.of(MutationMode.valueOf(label), requestMode)

            assertEquals(MutationMode.valueOf(label), result.label)
            assertEquals(requestMode, result.request)
            assertEquals(queue, result.queue)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            # IGNORE label rejects SYNC request
            - label: IGNORE
              request: SYNC
            """,
        )
        fun `rejects SYNC request in IGNORE mode`(
            label: String,
            request: String,
        ) {
            val ex =
                assertThrows<IllegalArgumentException> {
                    MutationModeContext.of(MutationMode.valueOf(label), MutationMode.valueOf(request))
                }
            assertTrue(ex.message!!.contains("SYNC"))
        }
    }
}
