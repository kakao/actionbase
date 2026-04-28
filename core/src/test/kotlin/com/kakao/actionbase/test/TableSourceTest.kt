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
        - [1, foo]
        - [2, bar]
        - [3, baz]
        """,
    )
    fun `flow rows bind by parameter position`(
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
        - [IDLE,    START, RUNNING]
        - [RUNNING, STOP,  IDLE]
        """,
    )
    fun `enum cells bind by name`(
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

    @ObjectSourceParameterizedTest
    @TableSource(
        """
        - 1 | foo
        - 2 | bar
        - 3 | baz
        """,
    )
    fun `pipe rows split each item by '|'`(
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
        # category one
        - 1 | 2 | 3

        # category two
        - 4 | ~ | 6
        """,
    )
    fun `pipe rows allow yaml comments and blank lines, ~ maps to null`(
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

    @Nested
    inner class NestedClassTest {
        @ObjectSourceParameterizedTest
        @TableSource(
            """
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

        private val twoCols = listOf("a", "b")
        private val threeCols = listOf("a", "b", "c")

        @Test
        fun `blank value is rejected`() {
            val e =
                assertThrows<IllegalArgumentException> {
                    extension.parseTableSource(TableSource(""), twoCols)
                }
            assertTrue(e.message!!.contains("value"))
        }

        @Test
        fun `empty method parameters is rejected`() {
            val e =
                assertThrows<IllegalArgumentException> {
                    extension.parseTableSource(
                        TableSource(
                            """
                            - [1, 2]
                            """.trimIndent(),
                        ),
                        emptyList(),
                    )
                }
            assertTrue(e.message!!.contains("method"))
        }

        @Test
        fun `non-list root is rejected`() {
            assertThrows<Exception> {
                extension.parseTableSource(TableSource("columns: [a, b]"), twoCols)
            }
        }

        @Test
        fun `flow row size mismatch is rejected`() {
            val e =
                assertThrows<IllegalArgumentException> {
                    extension.parseTableSource(
                        TableSource(
                            """
                            - [1, 2]
                            """.trimIndent(),
                        ),
                        threeCols,
                    )
                }
            assertTrue(e.message!!.contains("2 cells"))
            assertTrue(e.message!!.contains("expected 3"))
        }

        @Test
        fun `pipe row with wrong cell count is rejected`() {
            val e =
                assertThrows<IllegalArgumentException> {
                    extension.parseTableSource(
                        TableSource(
                            """
                            - 1 | 2
                            """.trimIndent(),
                        ),
                        threeCols,
                    )
                }
            assertTrue(e.message!!.contains("2 cells"))
            assertTrue(e.message!!.contains("expected 3"))
        }

        @Test
        fun `pipe row with empty cell is rejected`() {
            val e =
                assertThrows<IllegalArgumentException> {
                    extension.parseTableSource(
                        TableSource(
                            """
                            - 1 |
                            """.trimIndent(),
                        ),
                        twoCols,
                    )
                }
            assertTrue(e.message!!.contains("empty cell"))
            assertTrue(e.message!!.contains("Use ~"))
        }

        @Test
        fun `unsupported row type is rejected`() {
            val e =
                assertThrows<IllegalStateException> {
                    extension.parseTableSource(
                        TableSource(
                            """
                            - 42
                            """.trimIndent(),
                        ),
                        twoCols,
                    )
                }
            assertTrue(e.message!!.contains("list or a pipe-delimited string"))
        }

        @Test
        fun `empty list produces no test cases`() {
            val result = extension.parseTableSource(TableSource("[]"), twoCols)
            assertEquals(0, result.size)
        }
    }

    enum class Status { IDLE, RUNNING }

    enum class Event { START, STOP }
}
