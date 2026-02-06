package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.springframework.web.server.ResponseStatusException

class V3NameValidatorTest {
    @Nested
    inner class ValidNamesTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: mydb
            - name: MyDB
            - name: my-db
            - name: my_db
            - name: db123
            - name: a
            - name: A
            # 64 chars (max valid length)
            - name: abbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
            """,
        )
        fun `valid database names`(name: String) {
            assertThat(V3NameValidator.validateDatabase(name)).isEqualTo(name)
        }
    }

    @Nested
    inner class InvalidNamesTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: 123db
            - name: "1"
            - name: -db
            - name: _db
            """,
        )
        fun `name starting with non-letter should fail`(name: String) {
            assertThatThrownBy { V3NameValidator.validateDatabase(name) }
                .isInstanceOf(ResponseStatusException::class.java)
                .hasMessageContaining("must start with a letter")
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: db.name
            - name: "db:name"
            - name: db/name
            - name: 'db\name'
            - name: "db name"
            - name: mydb.othertable
            """,
        )
        fun `name with invalid characters should fail`(name: String) {
            assertThatThrownBy { V3NameValidator.validateDatabase(name) }
                .isInstanceOf(ResponseStatusException::class.java)
                .hasMessageContaining("Invalid database")
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            # empty
            - name: ""
            # 65 chars (exceeds max length)
            - name: abbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
            """,
        )
        fun `edge case names should fail`(name: String) {
            assertThatThrownBy { V3NameValidator.validateDatabase(name) }
                .isInstanceOf(ResponseStatusException::class.java)
        }
    }

    @Nested
    inner class FieldNameVariantsTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: 123invalid
            - name: .dotname
            """,
        )
        fun `validateTable error message`(name: String) {
            assertThatThrownBy { V3NameValidator.validateTable(name) }
                .isInstanceOf(ResponseStatusException::class.java)
                .hasMessageContaining("Invalid table")
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: 123invalid
            - name: .dotname
            """,
        )
        fun `validateAlias error message`(name: String) {
            assertThatThrownBy { V3NameValidator.validateAlias(name) }
                .isInstanceOf(ResponseStatusException::class.java)
                .hasMessageContaining("Invalid alias")
        }
    }
}
