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
    @DisplayName("of with global and internal")
    inner class OfWithGlobalAndInternalTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            # internal overrides everything — SYNC
            - label: SYNC
              global: ASYNC
              internal: SYNC
              queue: false

            - label: ASYNC
              global: ASYNC
              internal: SYNC
              queue: false

            - label: IGNORE
              global: ASYNC
              internal: SYNC
              queue: false

            - label: SYNC
              global: null
              internal: SYNC
              queue: false

            # internal overrides everything — ASYNC
            - label: SYNC
              global: SYNC
              internal: ASYNC
              queue: true

            - label: SYNC
              global: null
              internal: ASYNC
              queue: true

            # internal overrides everything — IGNORE
            - label: SYNC
              global: SYNC
              internal: IGNORE
              queue: true

            # global overrides request and table — SYNC
            - label: ASYNC
              global: SYNC
              internal: null
              queue: false

            - label: IGNORE
              global: SYNC
              internal: null
              queue: false

            # global overrides request and table — ASYNC
            - label: SYNC
              global: ASYNC
              internal: null
              queue: true

            # global overrides request and table — IGNORE
            - label: SYNC
              global: IGNORE
              internal: null
              queue: true

            # no global, no internal — falls back to request/label
            - label: SYNC
              global: null
              internal: null
              queue: false

            - label: ASYNC
              global: null
              internal: null
              queue: true

            - label: IGNORE
              global: null
              internal: null
              queue: true
            """,
        )
        fun `returns correct queue flag with global and internal`(
            label: String,
            global: String?,
            internal: String?,
            queue: Boolean,
        ) {
            val result =
                MutationModeContext.of(
                    label = MutationMode.valueOf(label),
                    request = null,
                    global = global?.let { MutationMode.valueOf(it) },
                    internal = internal?.let { MutationMode.valueOf(it) },
                )
            assertEquals(queue, result.queue)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            # global=ASYNC overrides request=SYNC
            - label: SYNC
              request: SYNC
              global: ASYNC
              queue: true

            - label: ASYNC
              request: SYNC
              global: ASYNC
              queue: true

            # global=SYNC overrides request=ASYNC
            - label: ASYNC
              request: ASYNC
              global: SYNC
              queue: false
            """,
        )
        fun `global overrides request`(
            label: String,
            request: String,
            global: String,
            queue: Boolean,
        ) {
            val result =
                MutationModeContext.of(
                    label = MutationMode.valueOf(label),
                    request = MutationMode.valueOf(request),
                    global = MutationMode.valueOf(global),
                    internal = null,
                )
            assertEquals(queue, result.queue)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - request: SYNC
              internal: SYNC

            - request: SYNC
              internal: ASYNC

            - request: ASYNC
              internal: SYNC
            """,
        )
        fun `request and internal are mutually exclusive`(
            request: String,
            internal: String,
        ) {
            assertThrows<IllegalArgumentException> {
                MutationModeContext.of(
                    label = MutationMode.SYNC,
                    request = MutationMode.valueOf(request),
                    global = null,
                    internal = MutationMode.valueOf(internal),
                )
            }
        }
    }
}
