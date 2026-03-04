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

    @Nested
    @DisplayName("of with system and force")
    inner class OfWithSystemAndForceTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            # system > label
            - label: ASYNC
              request: null
              system: SYNC
              queue: false

            - label: IGNORE
              request: null
              system: SYNC
              queue: false

            - label: SYNC
              request: null
              system: ASYNC
              queue: true

            - label: SYNC
              request: null
              system: IGNORE
              queue: true

            # system > request
            - label: SYNC
              request: SYNC
              system: ASYNC
              queue: true

            - label: ASYNC
              request: ASYNC
              system: SYNC
              queue: false
            """,
        )
        fun `system takes priority when force is false`(
            label: String,
            request: String?,
            system: String,
            queue: Boolean,
        ) {
            val result =
                MutationModeContext.of(
                    label = MutationMode.valueOf(label),
                    request = request?.let { MutationMode.valueOf(it) },
                    system = MutationMode.valueOf(system),
                    force = false,
                )
            assertEquals(queue, result.queue)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - label: SYNC
              request: SYNC
              system: ASYNC
              queue: false

            - label: SYNC
              request: ASYNC
              system: SYNC
              queue: true

            - label: SYNC
              request: IGNORE
              system: SYNC
              queue: true
            """,
        )
        fun `request takes priority when force is true`(
            label: String,
            request: String,
            system: String,
            queue: Boolean,
        ) {
            val result =
                MutationModeContext.of(
                    label = MutationMode.valueOf(label),
                    request = MutationMode.valueOf(request),
                    system = MutationMode.valueOf(system),
                    force = true,
                )
            assertEquals(queue, result.queue)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            # force=true bypasses IGNORE table constraint
            - label: IGNORE
              request: SYNC
              force: true
              queue: false
            """,
        )
        fun `force=true allows SYNC request on IGNORE table`(
            label: String,
            request: String,
            force: Boolean,
            queue: Boolean,
        ) {
            val result =
                MutationModeContext.of(
                    label = MutationMode.valueOf(label),
                    request = MutationMode.valueOf(request),
                    force = force,
                )
            assertEquals(queue, result.queue)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - label: SYNC
            - label: ASYNC
            - label: IGNORE
            """,
        )
        fun `force=true with null request throws`(label: String) {
            val ex =
                assertThrows<IllegalArgumentException> {
                    MutationModeContext.of(
                        label = MutationMode.valueOf(label),
                        request = null,
                        force = true,
                    )
                }
            assertTrue(ex.message!!.contains("force"))
        }
    }
}
