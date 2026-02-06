package com.kakao.actionbase.test

import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest

import java.util.concurrent.atomic.AtomicInteger

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested

import com.fasterxml.jackson.module.kotlin.readValue

class ObjectSourceTest {
    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - number:  1
          string: foo
        """,
    )
    fun test1(
        number: Int,
        string: String,
    ) {
        assertEquals(1, number)
        assertEquals("foo", string)
    }

    @Disabled
    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - number: 1
        """,
    )
    fun test2(number: Int) {
    }

    companion object {
        private val testCount = AtomicInteger(0)
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - number1: 1
          number2: 10
          string: foo
        - number2: 20
          number1: 2
          string: bar
        - string: baz
          number1: 3
          number2: 30
        """,
    )
    fun test3(
        number1: Int,
        number2: Int,
        string: String,
    ) {
        val parsedArguments: List<Map<String, Any>> =
            ObjectMappers.YAML.readValue(
                """
                - number1: 1
                  number2: 10
                  string: foo
                - number2: 20
                  number1: 2
                  string: bar
                - string: baz
                  number1: 3
                  number2: 30
                """.trimIndent(),
            )
        val invocationNumber = testCount.getAndIncrement()
        val argument = parsedArguments[invocationNumber]

        assertEquals(argument["number1"], number1)
        assertEquals(argument["number2"], number2)
        assertEquals(argument["string"], string)
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - number1: 1
          number2: 10
        """,
    )
    fun test4(
        number1: Int,
        number2: Int,
        string: String?,
    ) {
        assertEquals(1, number1)
        assertEquals(10, number2)
        assertNull(string)
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - number: 1
          enum : FOO
          list:
             - 1
             - 2
          map:
             foo: foo
             bar: bar
             baz: baz
          testClasses: |
              [
                {
                  "number": 1,
                  "string": "foo"
                }
              ]
          testClass: |
            {
              testClass: {
                "number": 1,
                "string": "foo"
              }
            }
        """,
    )
    fun test5(
        number: Int,
        enum: TestEnum,
        list: List<Int>,
        map: Map<String, String>,
        testClasses: List<TestClass>,
        testClass: Map<String, TestClass>,
    ) {
        assertEquals(1, number)
        assertEquals(TestEnum.FOO, enum)
        assertEquals(2, list.size)
        assertEquals(listOf(1, 2), list)
        assertEquals(3, map.size)
        assertEquals(mapOf("foo" to "foo", "bar" to "bar", "baz" to "baz"), map)
        assertEquals(1, testClasses.size)
        assertEquals(ObjectMappers.JSON.writeValueAsString(listOf(TestClass(1, "foo"))), ObjectMappers.JSON.writeValueAsString(testClasses))
        assertEquals(1, testClass.size)
        ObjectAssertions.assertEquals(testClass, mapOf("testClass" to TestClass(1, "foo")))
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - testClasses:
           - number: 1
             string : foo
        """,
    )
    fun test6(testClasses: List<TestClass>) {
        assertEquals(1, testClasses[0].number)
        assertEquals("foo", testClasses[0].string)
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        [
          {
            "number": 1,
            "string": "foo"
          }
        ]
        """,
    )
    fun test7(
        number: Int,
        string: String,
    ) {
        assertEquals(1, number)
        assertEquals("foo", string)
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        [
          {
            "number1": 1,
            "number2": 10
          }
        ]
        """,
    )
    fun test8(
        number1: Int,
        number2: Int,
        string: String?,
    ) {
        assertEquals(1, number1)
        assertEquals(10, number2)
        assertNull(string)
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        [
          {
            "number": 1,
            "enum": "FOO",
            "list": [
              1,
              2
            ],
            "map": {
              "foo": "foo",
              "bar": "bar",
              "baz": "baz"
            }
          }
        ]
        """,
    )
    fun test9(
        number: Int,
        enum: TestEnum,
        list: List<Int>,
        map: Map<String, String>,
    ) {
        assertEquals(1, number)
        assertEquals(TestEnum.FOO, enum)
        assertEquals(2, list.size)
        assertEquals(listOf(1, 2), list)
        assertEquals(3, map.size)
        assertEquals(mapOf("foo" to "foo", "bar" to "bar", "baz" to "baz"), map)
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        [
          {
            "testClasses": [
              {
                "number": 1,
                "string": "foo"
              }
            ]
          }
        ]
        """,
    )
    fun test10(testClasses: List<TestClass>) {
        assertEquals(1, testClasses[0].number)
        assertEquals("foo", testClasses[0].string)
    }

    @Nested
    inner class InnerClassTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - number: 1000
              string: foo
            """,
        )
        fun test1(
            number: Int,
            string: String,
        ) {
            assertEquals(1000, number)
            assertEquals("foo", string)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            [
              {
                "number": 1000,
                "string": "foo"
              }
            ]
            """,
        )
        fun test2(
            number: Int,
            string: String,
        ) {
            assertEquals(1000, number)
            assertEquals("foo", string)
        }
    }

    @Nested
    inner class TestsAliasTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            tests = """
            - number: 42
              string: hello
            """,
        )
        fun `tests parameter works as alias for value`(
            number: Int,
            string: String,
        ) {
            assertEquals(42, number)
            assertEquals("hello", string)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            tests = """
            - number: 1
              string: first
            - number: 2
              string: second
            """,
        )
        fun `tests parameter with multiple cases`(
            number: Int,
            string: String,
        ) {
            assert(number in 1..2)
            assert(string in listOf("first", "second"))
        }
    }

    @Nested
    inner class AllFieldsTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            all = """
              shared: common-value
            """,
            tests = """
            - name: case1
            - name: case2
            """,
        )
        fun `all fields are merged into every test case`(
            shared: String,
            name: String,
        ) {
            assertEquals("common-value", shared)
            assert(name in listOf("case1", "case2"))
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            all = """
              setup: |
                {"database": "test-db", "comment": "test"}
            """,
            tests = """
            - name: alias-basic
              update: |
                {"comment": "updated"}
            - name: alias-empty
              update: |
                {"comment": ""}
            """,
        )
        fun `all with JSON block scalars`(
            setup: String,
            name: String,
            update: String,
        ) {
            assertEquals("{\"database\": \"test-db\", \"comment\": \"test\"}\n", setup)
            assert(name in listOf("alias-basic", "alias-empty"))
            assert(update.contains("comment"))
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            all = """
              base: default
            """,
            tests = """
            - base: overridden
              extra: value
            """,
        )
        fun `per-case fields override all fields`(
            base: String,
            extra: String,
        ) {
            assertEquals("overridden", base)
            assertEquals("value", extra)
        }
    }

    @Nested
    inner class BackwardCompatibilityTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - number: 99
              string: backward
            """,
        )
        fun `value parameter still works`(
            number: Int,
            string: String,
        ) {
            assertEquals(99, number)
            assertEquals("backward", string)
        }
    }

    class TestClass(
        val number: Int,
        val string: String,
    )

    enum class TestEnum {
        FOO,
        BAR,
    }
}
