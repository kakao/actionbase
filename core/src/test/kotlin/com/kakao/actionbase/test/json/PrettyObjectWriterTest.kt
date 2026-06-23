package com.kakao.actionbase.test.json

import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest
import com.kakao.actionbase.test.documentations.params.TableSource

import kotlin.test.assertEquals

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PrettyObjectWriterTest {
    private val writer = PrettyObjectWriter.DEFAULT

    @Test
    fun `test empty object and array`() {
        assertEquals("{}", writer.format("{}"))
        assertEquals("[]", writer.format("[]"))
        assertEquals("{}", writer.writeValueAsString(emptyMap<String, Any>()))
        assertEquals("[]", writer.writeValueAsString(emptyList<Any>()))
    }

    @Test
    fun `test simple values`() {
        assertEquals("\"hello\"", writer.format("\"hello\""))
        assertEquals("123", writer.format("123"))
        assertEquals("true", writer.format("true"))
        assertEquals("null", writer.format("null"))
    }

    @Test
    fun `test short object inline`() {
        // given
        val shortJson = """{ "name" : "John", "age" : 30 }"""

        // when
        val actual = writer.format(shortJson)

        // then
        val expected = """{"name": "John", "age": 30}"""
        assertEquals(expected, actual)
    }

    @Test
    fun `test long object multiline`() {
        // given
        val longJson =
            """{"name": "John Doe", "age": 30, "address": {"street": "123 Main Street", "city": "New York", "zipcode": "10001"}, "hobbies": ["reading", "swimming", "coding"]}"""

        // when
        val actual = writer.format(longJson)

        // then
        val expected =
            """
            {
              "name": "John Doe",
              "age": 30,
              "address": {"street": "123 Main Street", "city": "New York", "zipcode": "10001"},
              "hobbies": ["reading", "swimming", "coding"]
            }
            """.trimIndent()

        assertEquals(expected, actual)
    }

    @Test
    fun `test array formatting`() {
        // given
        val arrayJson = """[ "apple", "banana", "cherry", "date", "elderberry" ]"""

        // when
        val actual = writer.format(arrayJson)

        // then
        val expected = """["apple", "banana", "cherry", "date", "elderberry"]"""
        assertEquals(expected, actual)
    }

    @Test
    fun `test nested structure`() {
        // given
        val nestedJson =
            """
            {
              "users": [
                {"id": 1, "name": "Alice"},
                {"id": 2, "name": "Bob"}
              ],
              "meta": {
                "total": 2,
                "page": 1
              }
            }
            """.trimIndent()

        // when
        val actual = writer.format(nestedJson)

        // then
        val expected =
            """
            {
              "users": [{"id": 1, "name": "Alice"}, {"id": 2, "name": "Bob"}],
              "meta": {"total": 2, "page": 1}
            }
            """.trimIndent()
        assertEquals(expected, actual)
    }

    @ObjectSourceParameterizedTest
    @TableSource(
        """
        #   sort | expected
        - 'false | {"z": 1, "a": 2, "m": 3}'
        - 'true  | {"a": 2, "m": 3, "z": 1}'
        """,
    )
    fun `format honors sort flag`(
        sort: Boolean,
        expected: String,
    ) {
        val json = """{"z": 1, "a": 2, "m": 3}"""
        assertEquals(expected, writer.format(json, sort = sort))
    }

    @ObjectSourceParameterizedTest
    @TableSource(
        """
        #   sort | expected
        - 'false | {"z": 1, "a": 2, "m": 3}'
        - 'true  | {"a": 2, "m": 3, "z": 1}'
        """,
    )
    fun `writeValueAsString honors sort flag`(
        sort: Boolean,
        expected: String,
    ) {
        val orderedMap =
            mapOf(
                "z" to 1,
                "a" to 2,
                "m" to 3,
            )
        assertEquals(expected, writer.writeValueAsString(orderedMap, sort = sort))
    }

    @Test
    fun `test custom indent size`() {
        // given
        val customWriter = PrettyObjectWriter(indentSize = 4, lineLengthLimit = 0) // 0 for everything in one line
        val json = """{"name": "test", "nested": {"key": "value"}}"""

        // when
        val actual = customWriter.format(json)

        // then
        val expected =
            """
            {
                "name": "test",
                "nested": {
                    "key": "value"
                }
            }
            """.trimIndent()
        assertEquals(expected, actual)
    }

    @Test
    fun `test line length limit`() {
        // given
        val shortLimitWriter = PrettyObjectWriter(indentSize = 2, lineLengthLimit = 30)
        val json = """{"key": "this is a very long value that should exceed the limit"}"""

        // when
        val actual = shortLimitWriter.format(json)

        // then
        val expected =
            """
            {
              "key": "this is a very long value that should exceed the limit"
            }
            """.trimIndent()
        assertEquals(expected, actual)
    }

    @Test
    fun `test special characters`() {
        // given
        val json = """{"message": "Hello\nWorld", "emoji": "😀", "unicode": "\u0041"}"""

        // when
        val actual = writer.format(json)

        // then
        val expected = """{"message": "Hello\nWorld", "emoji": "😀", "unicode": "A"}"""
        assertEquals(expected, actual)
    }

    @Test
    fun `test deep nesting`() {
        // given
        val deepJson =
            """
            {
              "level1": {
                "level2": {
                  "level3": {
                    "level4": {
                      "value": "deep"
                    }
                  }
                }
              }
            }
            """.trimIndent()

        // when
        val actual = writer.format(deepJson)

        // then
        val expected =
            """{"level1": {"level2": {"level3": {"level4": {"value": "deep"}}}}}"""
        assertEquals(expected, actual)
    }

    @Test
    fun `test invalid JSON handling`() {
        assertThrows<Exception> {
            writer.format("{invalid json}")
        }
    }

    @Test
    fun `test data class serialization`() {
        // given
        val company =
            Company(
                name = "Test Corp",
                employees =
                    listOf(
                        Person("Alice", 30, true),
                        Person("Bob", 25, false),
                    ),
            )

        // when
        val actual = writer.writeValueAsString(company)

        // then
        val expected =
            """
            {
              "name": "Test Corp",
              "employees": [
                {"name": "Alice", "age": 30, "active": true},
                {"name": "Bob", "age": 25, "active": false}
              ]
            }
            """.trimIndent()
        assertEquals(expected, actual)
    }

    @Test
    fun `test null value inclusion`() {
        // given
        val jsonWithNull = """{"key1": "value1", "key2": null, "key3": "value3"}"""

        // when
        val actual = writer.format(jsonWithNull)

        // then
        val expected = """{"key1": "value1", "key2": null, "key3": "value3"}"""
        assertEquals(expected, actual)
    }

    @Test
    fun `test number types`() {
        // given
        val numberJson = """{"int": 42, "double": 3.14, "long": 9223372036854775807}"""

        // when
        val actual = writer.format(numberJson)

        // then
        val expected = """{"int": 42, "double": 3.14, "long": 9223372036854775807}"""
        assertEquals(expected, actual)
    }
}

private data class Person(
    val name: String,
    val age: Int,
    val active: Boolean,
)

private data class Company(
    val name: String,
    val employees: List<Person>,
)
