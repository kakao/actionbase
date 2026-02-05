package com.kakao.actionbase.server.api.graph.v3.metadata

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.web.server.ResponseStatusException

class V3NameValidatorTest {
    @Nested
    inner class ValidNamesTest {
        @ParameterizedTest
        @ValueSource(strings = ["mydb", "MyDB", "my-db", "my_db", "db123", "a", "A"])
        fun `valid names should pass validation`(name: String) {
            val result = V3NameValidator.validateDatabase(name)
            assertThat(result).isEqualTo(name)
        }

        @Test
        fun `max length name should pass`() {
            val maxLengthName = "a" + "b".repeat(63)
            val result = V3NameValidator.validateDatabase(maxLengthName)
            assertThat(result).isEqualTo(maxLengthName)
        }
    }

    @Nested
    inner class InvalidNamesTest {
        @ParameterizedTest
        @ValueSource(strings = ["123db", "1", "-db", "_db"])
        fun `name starting with non-letter should fail`(name: String) {
            assertThatThrownBy { V3NameValidator.validateDatabase(name) }
                .isInstanceOf(ResponseStatusException::class.java)
                .hasMessageContaining("must start with a letter")
        }

        @ParameterizedTest
        @ValueSource(strings = ["db.name", "db:name", "db/name", "db\\name", "db name"])
        fun `name with invalid characters should fail`(name: String) {
            assertThatThrownBy { V3NameValidator.validateDatabase(name) }
                .isInstanceOf(ResponseStatusException::class.java)
                .hasMessageContaining("Invalid database")
        }

        @Test
        fun `empty name should fail`() {
            assertThatThrownBy { V3NameValidator.validateDatabase("") }
                .isInstanceOf(ResponseStatusException::class.java)
        }

        @Test
        fun `name exceeding max length should fail`() {
            val tooLongName = "a" + "b".repeat(64)
            assertThatThrownBy { V3NameValidator.validateDatabase(tooLongName) }
                .isInstanceOf(ResponseStatusException::class.java)
        }

        @Test
        fun `injection attempt with dot should fail`() {
            assertThatThrownBy { V3NameValidator.validateDatabase("mydb.othertable") }
                .isInstanceOf(ResponseStatusException::class.java)
        }
    }

    @Nested
    inner class FieldNameVariantsTest {
        @Test
        fun `validateTable returns correct field name in error`() {
            assertThatThrownBy { V3NameValidator.validateTable("123invalid") }
                .isInstanceOf(ResponseStatusException::class.java)
                .hasMessageContaining("Invalid table")
        }

        @Test
        fun `validateAlias returns correct field name in error`() {
            assertThatThrownBy { V3NameValidator.validateAlias("123invalid") }
                .isInstanceOf(ResponseStatusException::class.java)
                .hasMessageContaining("Invalid alias")
        }
    }
}
