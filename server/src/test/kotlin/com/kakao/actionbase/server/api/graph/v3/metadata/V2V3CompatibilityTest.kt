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
 * Tests that V2 and V3 APIs are fully compatible:
 * - Create with V2 → Update with V3 → Verify consistency
 * - Create with V3 → Update with V2 → Verify consistency
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
            - scenario: V2 create, V3 update
              v2Name: compat-db-v2v3
              v3Name: compat-db-v2v3
              v2CreateReq: |
                {"desc": "created by v2"}
              v3UpdateReq: |
                {"comment": "updated by v3"}
              expectedValue: updated by v3

            - scenario: V3 create, V2 update
              v2Name: compat-db-v3v2
              v3Name: compat-db-v3v2
              v3CreateReq: |
                {
                  "database": "compat-db-v3v2",
                  "comment": "created by v3"
                }
              v2UpdateReq: |
                {"active": true, "desc": "updated by v2"}
              expectedValue: updated by v2
            """,
        )
        fun `database compatibility - create and update across API versions`(
            scenario: String,
            v2Name: String,
            v3Name: String,
            v2CreateReq: String?,
            v3CreateReq: String?,
            v2UpdateReq: String?,
            v3UpdateReq: String?,
            expectedValue: String,
        ) {
            // Create
            if (!v2CreateReq.isNullOrBlank()) {
                client.post()
                    .uri("/graph/v2/service/$v2Name")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(v2CreateReq)
                    .exchange()
                    .expectStatus().isOk
            }
            if (!v3CreateReq.isNullOrBlank()) {
                client.post()
                    .uri("/graph/v3/databases/$v3Name")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(v3CreateReq)
                    .exchange()
                    .expectStatus().isOk
            }

            // Update
            if (!v3UpdateReq.isNullOrBlank()) {
                client.put()
                    .uri("/graph/v3/databases/$v3Name")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(v3UpdateReq)
                    .exchange()
                    .expectStatus().isOk
            }
            if (!v2UpdateReq.isNullOrBlank()) {
                client.put()
                    .uri("/graph/v2/service/$v2Name")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(v2UpdateReq)
                    .exchange()
                    .expectStatus().isOk
            }

            // Verify via V2 API
            client.get()
                .uri("/graph/v2/service/$v2Name")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.desc").isEqualTo(expectedValue)

            // Verify via V3 API
            client.get()
                .uri("/graph/v3/databases/$v3Name")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comment").isEqualTo(expectedValue)
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class TableCompatibilityTest {
        private val db = "compat-table-db"

        @BeforeAll
        fun setup() {
            client.post()
                .uri("/graph/v3/databases/$db")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"database": "$db", "comment": "test db"}""")
                .exchange()
                .expectStatus().isOk
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - scenario: V2 create, V3 update
              name: compat-tbl-v2v3
              v2CreateReq: |
                {
                  "desc": "created by v2",
                  "type": "HASH",
                  "schema": {
                    "src": {"type": "STRING", "desc": "source"},
                    "tgt": {"type": "STRING", "desc": "target"},
                    "fields": []
                  },
                  "dirType": "OUT",
                  "storage": "datastore://hbase/compat-tbl-v2v3-storage"
                }
              v3UpdateReq: |
                {"comment": "updated by v3"}
              expectedValue: updated by v3

            - scenario: V3 create, V2 update
              name: compat-tbl-v3v2
              v3CreateReq: |
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
                  "storage": "datastore://hbase/compat-tbl-v3v2-storage",
                  "mode": "SYNC",
                  "comment": "created by v3"
                }
              v2UpdateReq: |
                {"active": true, "desc": "updated by v2"}
              expectedValue: updated by v2
            """,
        )
        fun `table compatibility - create and update across API versions`(
            scenario: String,
            name: String,
            v2CreateReq: String?,
            v3CreateReq: String?,
            v2UpdateReq: String?,
            v3UpdateReq: String?,
            expectedValue: String,
        ) {
            // Create
            if (!v2CreateReq.isNullOrBlank()) {
                client.post()
                    .uri("/graph/v2/service/$db/label/$name")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(v2CreateReq)
                    .exchange()
                    .expectStatus().isOk
            }
            if (!v3CreateReq.isNullOrBlank()) {
                client.post()
                    .uri("/graph/v3/databases/$db/tables/$name")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(v3CreateReq)
                    .exchange()
                    .expectStatus().isOk
            }

            // Update
            if (!v3UpdateReq.isNullOrBlank()) {
                client.put()
                    .uri("/graph/v3/databases/$db/tables/$name")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(v3UpdateReq)
                    .exchange()
                    .expectStatus().isOk
            }
            if (!v2UpdateReq.isNullOrBlank()) {
                client.put()
                    .uri("/graph/v2/service/$db/label/$name")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(v2UpdateReq)
                    .exchange()
                    .expectStatus().isOk
            }

            // Verify via V2 API
            client.get()
                .uri("/graph/v2/service/$db/label/$name")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.desc").isEqualTo(expectedValue)

            // Verify via V3 API
            client.get()
                .uri("/graph/v3/databases/$db/tables/$name")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comment").isEqualTo(expectedValue)
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class AliasCompatibilityTest {
        private val db = "compat-alias-db"
        private val table = "compat-alias-target"

        @BeforeAll
        fun setup() {
            // Create database
            client.post()
                .uri("/graph/v3/databases/$db")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"database": "$db", "comment": "test db"}""")
                .exchange()
                .expectStatus().isOk

            // Create table (alias target)
            client.post()
                .uri("/graph/v3/databases/$db/tables/$table")
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
                      "storage": "datastore://hbase/compat-alias-target-storage",
                      "mode": "SYNC",
                      "comment": "target table"
                    }
                    """.trimIndent(),
                )
                .exchange()
                .expectStatus().isOk
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - scenario: V2 create, V3 update
              name: compat-als-v2v3
              v2CreateReq: |
                {
                  "desc": "created by v2",
                  "target": "compat-alias-db.compat-alias-target"
                }
              v3UpdateReq: |
                {"comment": "updated by v3"}
              expectedValue: updated by v3

            - scenario: V3 create, V2 update
              name: compat-als-v3v2
              v3CreateReq: |
                {
                  "table": "compat-alias-target",
                  "comment": "created by v3"
                }
              v2UpdateReq: |
                {"active": true, "desc": "updated by v2"}
              expectedValue: updated by v2
            """,
        )
        fun `alias compatibility - create and update across API versions`(
            scenario: String,
            name: String,
            v2CreateReq: String?,
            v3CreateReq: String?,
            v2UpdateReq: String?,
            v3UpdateReq: String?,
            expectedValue: String,
        ) {
            // Create
            if (!v2CreateReq.isNullOrBlank()) {
                client.post()
                    .uri("/graph/v2/service/$db/alias/$name")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(v2CreateReq)
                    .exchange()
                    .expectStatus().isOk
            }
            if (!v3CreateReq.isNullOrBlank()) {
                client.post()
                    .uri("/graph/v3/databases/$db/aliases/$name")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(v3CreateReq)
                    .exchange()
                    .expectStatus().isOk
            }

            // Update
            if (!v3UpdateReq.isNullOrBlank()) {
                client.put()
                    .uri("/graph/v3/databases/$db/aliases/$name")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(v3UpdateReq)
                    .exchange()
                    .expectStatus().isOk
            }
            if (!v2UpdateReq.isNullOrBlank()) {
                client.put()
                    .uri("/graph/v2/service/$db/alias/$name")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(v2UpdateReq)
                    .exchange()
                    .expectStatus().isOk
            }

            // Verify via V2 API
            client.get()
                .uri("/graph/v2/service/$db/alias/$name")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.desc").isEqualTo(expectedValue)

            // Verify via V3 API
            client.get()
                .uri("/graph/v3/databases/$db/aliases/$name")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comment").isEqualTo(expectedValue)
        }
    }
}
