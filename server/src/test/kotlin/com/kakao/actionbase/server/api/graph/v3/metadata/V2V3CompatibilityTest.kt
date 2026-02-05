package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.server.test.E2ETestBase
import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType

/**
 * V2-V3 API Compatibility E2E Tests
 *
 * Terminology mapping:
 * - V2 Service = V3 Database
 * - V2 Label = V3 Table
 * - V2 desc = V3 comment
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V2V3CompatibilityTest : E2ETestBase() {

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class DatabaseCompatibilityTest {

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: db-v2v3
              createReq: |
                {"desc": "created by v2"}
              updateReq: |
                {"comment": "updated by v3"}
              expected: updated by v3
            """,
        )
        fun `V2 create, V3 update`(name: String, createReq: String, updateReq: String, expected: String) {
            // V2 create
            client.post().uri("/graph/v2/service/$name")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(createReq)
                .exchange().expectStatus().isOk

            // V3 update
            client.put().uri("/graph/v3/databases/$name")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(updateReq)
                .exchange().expectStatus().isOk

            // Verify
            verifyDatabase(name, expected)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: db-v3v2
              createReq: |
                {"database": "db-v3v2", "comment": "created by v3"}
              updateReq: |
                {"active": true, "desc": "updated by v2"}
              expected: updated by v2
            """,
        )
        fun `V3 create, V2 update`(name: String, createReq: String, updateReq: String, expected: String) {
            // V3 create
            client.post().uri("/graph/v3/databases/$name")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(createReq)
                .exchange().expectStatus().isOk

            // V2 update
            client.put().uri("/graph/v2/service/$name")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(updateReq)
                .exchange().expectStatus().isOk

            // Verify
            verifyDatabase(name, expected)
        }

        private fun verifyDatabase(name: String, expected: String) {
            client.get().uri("/graph/v2/service/$name")
                .exchange().expectStatus().isOk
                .expectBody().jsonPath("$.desc").isEqualTo(expected)

            client.get().uri("/graph/v3/databases/$name")
                .exchange().expectStatus().isOk
                .expectBody().jsonPath("$.comment").isEqualTo(expected)
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class TableCompatibilityTest {
        private val db = "compat-table-db"

        @BeforeAll
        fun setup() {
            client.post().uri("/graph/v3/databases/$db")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"database": "$db", "comment": "test db"}""")
                .exchange().expectStatus().isOk
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: tbl-v2v3
              createReq: |
                {
                  "desc": "created by v2",
                  "type": "HASH",
                  "schema": {
                    "src": {"type": "STRING", "desc": "source"},
                    "tgt": {"type": "STRING", "desc": "target"},
                    "fields": []
                  },
                  "dirType": "OUT",
                  "storage": "datastore://hbase/tbl-v2v3-storage"
                }
              updateReq: |
                {"comment": "updated by v3"}
              expected: updated by v3
            """,
        )
        fun `V2 create, V3 update`(name: String, createReq: String, updateReq: String, expected: String) {
            // V2 create
            client.post().uri("/graph/v2/service/$db/label/$name")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(createReq)
                .exchange().expectStatus().isOk

            // V3 update
            client.put().uri("/graph/v3/databases/$db/tables/$name")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(updateReq)
                .exchange().expectStatus().isOk

            // Verify
            verifyTable(name, expected)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: tbl-v3v2
              createReq: |
                {
                  "schema": {
                    "type": "edge",
                    "source": {"type": "string", "comment": "source"},
                    "target": {"type": "string", "comment": "target"},
                    "properties": [],
                    "direction": "OUT",
                    "indexes": [],
                    "groups": []
                  },
                  "storage": "datastore://hbase/tbl-v3v2-storage",
                  "mode": "SYNC",
                  "comment": "created by v3"
                }
              updateReq: |
                {"active": true, "desc": "updated by v2"}
              expected: updated by v2
            """,
        )
        fun `V3 create, V2 update`(name: String, createReq: String, updateReq: String, expected: String) {
            // V3 create
            client.post().uri("/graph/v3/databases/$db/tables/$name")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(createReq)
                .exchange().expectStatus().isOk

            // V2 update
            client.put().uri("/graph/v2/service/$db/label/$name")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(updateReq)
                .exchange().expectStatus().isOk

            // Verify
            verifyTable(name, expected)
        }

        private fun verifyTable(name: String, expected: String) {
            client.get().uri("/graph/v2/service/$db/label/$name")
                .exchange().expectStatus().isOk
                .expectBody().jsonPath("$.desc").isEqualTo(expected)

            client.get().uri("/graph/v3/databases/$db/tables/$name")
                .exchange().expectStatus().isOk
                .expectBody().jsonPath("$.comment").isEqualTo(expected)
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class AliasCompatibilityTest {
        private val db = "compat-alias-db"
        private val table = "alias-target"

        @BeforeAll
        fun setup() {
            client.post().uri("/graph/v3/databases/$db")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"database": "$db", "comment": "test db"}""")
                .exchange().expectStatus().isOk

            client.post().uri("/graph/v3/databases/$db/tables/$table")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    """
                    {
                      "schema": {
                        "type": "edge",
                        "source": {"type": "string", "comment": "src"},
                        "target": {"type": "string", "comment": "tgt"},
                        "properties": [],
                        "direction": "OUT",
                        "indexes": [],
                        "groups": []
                      },
                      "storage": "datastore://hbase/alias-target-storage",
                      "mode": "SYNC",
                      "comment": "target table"
                    }
                    """.trimIndent(),
                )
                .exchange().expectStatus().isOk
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: als-v2v3
              createReq: |
                {"desc": "created by v2", "target": "compat-alias-db.alias-target"}
              updateReq: |
                {"comment": "updated by v3"}
              expected: updated by v3
            """,
        )
        fun `V2 create, V3 update`(name: String, createReq: String, updateReq: String, expected: String) {
            // V2 create
            client.post().uri("/graph/v2/service/$db/alias/$name")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(createReq)
                .exchange().expectStatus().isOk

            // V3 update
            client.put().uri("/graph/v3/databases/$db/aliases/$name")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(updateReq)
                .exchange().expectStatus().isOk

            // Verify
            verifyAlias(name, expected)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: als-v3v2
              createReq: |
                {"table": "alias-target", "comment": "created by v3"}
              updateReq: |
                {"active": true, "desc": "updated by v2"}
              expected: updated by v2
            """,
        )
        fun `V3 create, V2 update`(name: String, createReq: String, updateReq: String, expected: String) {
            // V3 create
            client.post().uri("/graph/v3/databases/$db/aliases/$name")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(createReq)
                .exchange().expectStatus().isOk

            // V2 update
            client.put().uri("/graph/v2/service/$db/alias/$name")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(updateReq)
                .exchange().expectStatus().isOk

            // Verify
            verifyAlias(name, expected)
        }

        private fun verifyAlias(name: String, expected: String) {
            client.get().uri("/graph/v2/service/$db/alias/$name")
                .exchange().expectStatus().isOk
                .expectBody().jsonPath("$.desc").isEqualTo(expected)

            client.get().uri("/graph/v3/databases/$db/aliases/$name")
                .exchange().expectStatus().isOk
                .expectBody().jsonPath("$.comment").isEqualTo(expected)
        }
    }
}
