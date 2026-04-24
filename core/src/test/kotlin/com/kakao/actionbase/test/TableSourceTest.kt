package com.kakao.actionbase.test

import com.kakao.actionbase.test.documentations.params.ObjectSourceExtension
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest
import com.kakao.actionbase.test.documentations.params.TableSource

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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
    inner class NestedClassTest {
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

    @Nested
    inner class ErrorTest {
        private val extension = ObjectSourceExtension()

        @Test
        fun `blank value is rejected`() {
            val e =
                assertThrows<IllegalArgumentException> {
                    extension.parseTableSource(TableSource(""))
                }
            assertTrue(e.message!!.contains("value"))
        }

        @Test
        fun `missing columns is rejected`() {
            val e =
                assertThrows<IllegalStateException> {
                    extension.parseTableSource(
                        TableSource(
                            """
                            rows:
                              - [1, 2]
                            """.trimIndent(),
                        ),
                    )
                }
            assertTrue(e.message!!.contains("columns"))
        }

        @Test
        fun `missing rows is rejected`() {
            val e =
                assertThrows<IllegalStateException> {
                    extension.parseTableSource(
                        TableSource(
                            """
                            columns: [a, b]
                            """.trimIndent(),
                        ),
                    )
                }
            assertTrue(e.message!!.contains("rows"))
        }

        @Test
        fun `row size mismatch is rejected`() {
            val e =
                assertThrows<IllegalArgumentException> {
                    extension.parseTableSource(
                        TableSource(
                            """
                            columns: [a, b, c]
                            rows:
                              - [1, 2]
                            """.trimIndent(),
                        ),
                    )
                }
            assertTrue(e.message!!.contains("row size"))
            assertTrue(e.message!!.contains("columns size"))
        }

        @Test
        fun `non-string column entry is rejected`() {
            val e =
                assertThrows<IllegalArgumentException> {
                    extension.parseTableSource(
                        TableSource(
                            """
                            columns: [a, 2]
                            rows:
                              - [1, 2]
                            """.trimIndent(),
                        ),
                    )
                }
            assertTrue(e.message!!.contains("must be strings"))
        }

        @Test
        fun `row that is not a list is rejected`() {
            val e =
                assertThrows<IllegalArgumentException> {
                    extension.parseTableSource(
                        TableSource(
                            """
                            columns: [a, b]
                            rows:
                              - not-a-list
                            """.trimIndent(),
                        ),
                    )
                }
            assertTrue(e.message!!.contains("must be a list"))
        }

        @Test
        fun `empty rows list produces no test cases`() {
            val result =
                extension.parseTableSource(
                    TableSource(
                        """
                        columns: [a, b]
                        rows: []
                        """.trimIndent(),
                    ),
                )
            assertEquals(0, result.size)
        }
    }

    enum class Status { IDLE, RUNNING }

    enum class Event { START, STOP }
}
