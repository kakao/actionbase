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

    // Test data classes for Given-When-Then structure
    data class Given(val name: String)
    data class When(val createApi: String, val createReq: String, val updateApi: String, val updateReq: String)
    data class Then(val expectedComment: String)

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class DatabaseCompatibilityTest {

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - scenario: V2 create, V3 update
              given:
                name: compat-db-v2v3
              when:
                createApi: V2
                createReq: |
                  {"desc": "created by v2"}
                updateApi: V3
                updateReq: |
                  {"comment": "updated by v3"}
              then:
                expectedComment: updated by v3

            - scenario: V3 create, V2 update
              given:
                name: compat-db-v3v2
              when:
                createApi: V3
                createReq: |
                  {
                    "database": "compat-db-v3v2",
                    "comment": "created by v3"
                  }
                updateApi: V2
                updateReq: |
                  {"active": true, "desc": "updated by v2"}
              then:
                expectedComment: updated by v2
            """,
        )
        fun `database compatibility - create and update across API versions`(
            scenario: String,
            given: Given,
            `when`: When,
            then: Then,
        ) {
            // Create
            val createUri = when (`when`.createApi) {
                "V2" -> "/graph/v2/service/${given.name}"
                "V3" -> "/graph/v3/databases/${given.name}"
                else -> error("Unknown API: ${`when`.createApi}")
            }
            client.post()
                .uri(createUri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(`when`.createReq)
                .exchange()
                .expectStatus().isOk

            // Update
            val updateUri = when (`when`.updateApi) {
                "V2" -> "/graph/v2/service/${given.name}"
                "V3" -> "/graph/v3/databases/${given.name}"
                else -> error("Unknown API: ${`when`.updateApi}")
            }
            client.put()
                .uri(updateUri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(`when`.updateReq)
                .exchange()
                .expectStatus().isOk

            // Verify via V2 API
            client.get()
                .uri("/graph/v2/service/${given.name}")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.desc").isEqualTo(then.expectedComment)

            // Verify via V3 API
            client.get()
                .uri("/graph/v3/databases/${given.name}")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comment").isEqualTo(then.expectedComment)
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
              given:
                name: compat-tbl-v2v3
              when:
                createApi: V2
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
                    "storage": "datastore://hbase/compat-tbl-v2v3-storage"
                  }
                updateApi: V3
                updateReq: |
                  {"comment": "updated by v3"}
              then:
                expectedComment: updated by v3

            - scenario: V3 create, V2 update
              given:
                name: compat-tbl-v3v2
              when:
                createApi: V3
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
                    "storage": "datastore://hbase/compat-tbl-v3v2-storage",
                    "mode": "SYNC",
                    "comment": "created by v3"
                  }
                updateApi: V2
                updateReq: |
                  {"active": true, "desc": "updated by v2"}
              then:
                expectedComment: updated by v2
            """,
        )
        fun `table compatibility - create and update across API versions`(
            scenario: String,
            given: Given,
            `when`: When,
            then: Then,
        ) {
            // Create
            val createUri = when (`when`.createApi) {
                "V2" -> "/graph/v2/service/$db/label/${given.name}"
                "V3" -> "/graph/v3/databases/$db/tables/${given.name}"
                else -> error("Unknown API: ${`when`.createApi}")
            }
            client.post()
                .uri(createUri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(`when`.createReq)
                .exchange()
                .expectStatus().isOk

            // Update
            val updateUri = when (`when`.updateApi) {
                "V2" -> "/graph/v2/service/$db/label/${given.name}"
                "V3" -> "/graph/v3/databases/$db/tables/${given.name}"
                else -> error("Unknown API: ${`when`.updateApi}")
            }
            client.put()
                .uri(updateUri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(`when`.updateReq)
                .exchange()
                .expectStatus().isOk

            // Verify via V2 API
            client.get()
                .uri("/graph/v2/service/$db/label/${given.name}")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.desc").isEqualTo(then.expectedComment)

            // Verify via V3 API
            client.get()
                .uri("/graph/v3/databases/$db/tables/${given.name}")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comment").isEqualTo(then.expectedComment)
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
              given:
                name: compat-als-v2v3
              when:
                createApi: V2
                createReq: |
                  {
                    "desc": "created by v2",
                    "target": "compat-alias-db.compat-alias-target"
                  }
                updateApi: V3
                updateReq: |
                  {"comment": "updated by v3"}
              then:
                expectedComment: updated by v3

            - scenario: V3 create, V2 update
              given:
                name: compat-als-v3v2
              when:
                createApi: V3
                createReq: |
                  {
                    "table": "compat-alias-target",
                    "comment": "created by v3"
                  }
                updateApi: V2
                updateReq: |
                  {"active": true, "desc": "updated by v2"}
              then:
                expectedComment: updated by v2
            """,
        )
        fun `alias compatibility - create and update across API versions`(
            scenario: String,
            given: Given,
            `when`: When,
            then: Then,
        ) {
            // Create
            val createUri = when (`when`.createApi) {
                "V2" -> "/graph/v2/service/$db/alias/${given.name}"
                "V3" -> "/graph/v3/databases/$db/aliases/${given.name}"
                else -> error("Unknown API: ${`when`.createApi}")
            }
            client.post()
                .uri(createUri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(`when`.createReq)
                .exchange()
                .expectStatus().isOk

            // Update
            val updateUri = when (`when`.updateApi) {
                "V2" -> "/graph/v2/service/$db/alias/${given.name}"
                "V3" -> "/graph/v3/databases/$db/aliases/${given.name}"
                else -> error("Unknown API: ${`when`.updateApi}")
            }
            client.put()
                .uri(updateUri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(`when`.updateReq)
                .exchange()
                .expectStatus().isOk

            // Verify via V2 API
            client.get()
                .uri("/graph/v2/service/$db/alias/${given.name}")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.desc").isEqualTo(then.expectedComment)

            // Verify via V3 API
            client.get()
                .uri("/graph/v3/databases/$db/aliases/${given.name}")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comment").isEqualTo(then.expectedComment)
        }
    }
}
