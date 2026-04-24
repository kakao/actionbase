package com.kakao.actionbase.test

import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest
import com.kakao.actionbase.test.documentations.params.TableSource

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested

class TableSourceTest {
    @ObjectSourceParameterizedTest
    @TableSource(
        """
        columns: [number, string]
        rows:
          - [1, foo]
          - [2, bar]
          - [3, baz]
        """,
    )
    fun `columns become parameter names`(
        number: Int,
        string: String,
    ) {
        assertEquals(
            when (number) {
                1 -> "foo"
                2 -> "bar"
                3 -> "baz"
                else -> error("unexpected number $number")
            },
            string,
        )
    }

    @ObjectSourceParameterizedTest
    @TableSource(
        """
        columns: [a, b, c]
        rows:
          - [1, 2, 3]
          - [4, ~, 6]
        """,
    )
    fun `tilde maps to null for nullable parameters`(
        a: Int,
        b: Int?,
        c: Int,
    ) {
        when (a) {
            1 -> assertEquals(2, b)
            4 -> assertNull(b)
            else -> error("unexpected a=$a")
        }
        assertEquals(a + 2, c)
    }

    @ObjectSourceParameterizedTest
    @TableSource(
        """
        columns: [from, event, expected]
        rows:
          - [IDLE,    START, RUNNING]
          - [RUNNING, STOP,  IDLE]
        """,
    )
    fun `enum columns bind by name`(
        from: Status,
        event: Event,
        expected: Status,
    ) {
        val result =
            when (from to event) {
                Status.IDLE to Event.START -> Status.RUNNING
                Status.RUNNING to Event.STOP -> Status.IDLE
                else -> error("unexpected $from + $event")
            }
        assertEquals(expected, result)
    }

    @Nested
    inner class MisuseTest {
        @ObjectSourceParameterizedTest
        @TableSource(
            """
            columns: [a, b]
            rows:
              - [1, 2]
            """,
        )
        fun `nested test classes work`(
            a: Int,
            b: Int,
        ) {
            assertEquals(1, a)
            assertEquals(2, b)
        }
    }

    enum class Status { IDLE, RUNNING }

    enum class Event { START, STOP }
}
