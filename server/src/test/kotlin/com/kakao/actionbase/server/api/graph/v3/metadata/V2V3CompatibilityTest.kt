package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType

/**
 * V2-V3 API Compatibility E2E Tests
 *
 * Tests that V2 and V3 APIs are fully compatible:
 * - Create with V2 → Update with V3 → Verify consistency
 * - Create with V3 → Update with V2 → Verify consistency
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V2V3CompatibilityTest : E2ETestBase() {

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class DatabaseCompatibilityTest {
        private val db = "compat-db-test"

        @Test
        fun `create with V2, update with V3, verify consistency`() {
            // Create with V2 API
            client.post()
                .uri("/graph/v2/service/$db")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"desc": "created by v2"}""")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.result.desc").isEqualTo("created by v2")

            // Update with V3 API
            client.put()
                .uri("/graph/v3/databases/$db")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"comment": "updated by v3"}""")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comment").isEqualTo("updated by v3")

            // Verify via V2 API
            client.get()
                .uri("/graph/v2/service/$db")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.desc").isEqualTo("updated by v3")

            // Verify via V3 API
            client.get()
                .uri("/graph/v3/databases/$db")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comment").isEqualTo("updated by v3")
        }

        @Test
        fun `create with V3, update with V2, verify consistency`() {
            val db2 = "compat-db-test-2"

            // Create with V3 API
            client.post()
                .uri("/graph/v3/databases/$db2")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"database": "$db2", "comment": "created by v3"}""")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comment").isEqualTo("created by v3")

            // Update with V2 API
            client.put()
                .uri("/graph/v2/service/$db2")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"active": true, "desc": "updated by v2"}""")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.result.desc").isEqualTo("updated by v2")

            // Verify via V2 API
            client.get()
                .uri("/graph/v2/service/$db2")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.desc").isEqualTo("updated by v2")

            // Verify via V3 API
            client.get()
                .uri("/graph/v3/databases/$db2")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comment").isEqualTo("updated by v2")
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class TableCompatibilityTest {
        private val db = "compat-table-db"
        private val table = "compat-table"

        @BeforeAll
        fun setup() {
            // Create database first
            client.post()
                .uri("/graph/v3/databases/$db")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"database": "$db", "comment": "test db"}""")
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `create with V2, update with V3, verify consistency`() {
            val v2LabelRequest = """
                {
                    "desc": "created by v2",
                    "type": "HASH",
                    "schema": {
                        "src": {"type": "STRING", "desc": "source"},
                        "tgt": {"type": "STRING", "desc": "target"},
                        "fields": []
                    },
                    "dirType": "OUT",
                    "storage": "datastore://hbase/compat-table-storage"
                }
            """.trimIndent()

            // Create with V2 API (label)
            client.post()
                .uri("/graph/v2/service/$db/label/$table")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(v2LabelRequest)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.result.desc").isEqualTo("created by v2")

            // Update with V3 API (table)
            client.put()
                .uri("/graph/v3/databases/$db/tables/$table")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"comment": "updated by v3"}""")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comment").isEqualTo("updated by v3")

            // Verify via V2 API
            client.get()
                .uri("/graph/v2/service/$db/label/$table")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.desc").isEqualTo("updated by v3")

            // Verify via V3 API
            client.get()
                .uri("/graph/v3/databases/$db/tables/$table")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comment").isEqualTo("updated by v3")
        }

        @Test
        fun `create with V3, update with V2, verify consistency`() {
            val table2 = "compat-table-2"

            val v3TableRequest = """
                {
                    "schema": {
                        "source": {"type": "STRING", "comment": "source"},
                        "target": {"type": "STRING", "comment": "target"},
                        "properties": [],
                        "direction": "OUT"
                    },
                    "storage": "datastore://hbase/compat-table2-storage",
                    "mode": "SYNC",
                    "comment": "created by v3"
                }
            """.trimIndent()

            // Create with V3 API (table)
            client.post()
                .uri("/graph/v3/databases/$db/tables/$table2")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(v3TableRequest)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comment").isEqualTo("created by v3")

            // Update with V2 API (label)
            client.put()
                .uri("/graph/v2/service/$db/label/$table2")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"active": true, "desc": "updated by v2"}""")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.result.desc").isEqualTo("updated by v2")

            // Verify via V2 API
            client.get()
                .uri("/graph/v2/service/$db/label/$table2")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.desc").isEqualTo("updated by v2")

            // Verify via V3 API
            client.get()
                .uri("/graph/v3/databases/$db/tables/$table2")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comment").isEqualTo("updated by v2")
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class AliasCompatibilityTest {
        private val db = "compat-alias-db"
        private val table = "compat-alias-target"
        private val alias = "compat-alias"

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
            val tableRequest = """
                {
                    "schema": {
                        "source": {"type": "STRING", "comment": "src"},
                        "target": {"type": "STRING", "comment": "tgt"},
                        "properties": [],
                        "direction": "OUT"
                    },
                    "storage": "datastore://hbase/compat-alias-target-storage",
                    "mode": "SYNC",
                    "comment": "target table"
                }
            """.trimIndent()

            client.post()
                .uri("/graph/v3/databases/$db/tables/$table")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(tableRequest)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `create with V2, update with V3, verify consistency`() {
            // Create with V2 API
            client.post()
                .uri("/graph/v2/service/$db/alias/$alias")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"desc": "created by v2", "target": "$db.$table"}""")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.result.desc").isEqualTo("created by v2")

            // Update with V3 API
            client.put()
                .uri("/graph/v3/databases/$db/aliases/$alias")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"comment": "updated by v3"}""")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comment").isEqualTo("updated by v3")

            // Verify via V2 API
            client.get()
                .uri("/graph/v2/service/$db/alias/$alias")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.desc").isEqualTo("updated by v3")

            // Verify via V3 API
            client.get()
                .uri("/graph/v3/databases/$db/aliases/$alias")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comment").isEqualTo("updated by v3")
        }

        @Test
        fun `create with V3, update with V2, verify consistency`() {
            val alias2 = "compat-alias-2"

            // Create with V3 API
            client.post()
                .uri("/graph/v3/databases/$db/aliases/$alias2")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"table": "$table", "comment": "created by v3"}""")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comment").isEqualTo("created by v3")

            // Update with V2 API
            client.put()
                .uri("/graph/v2/service/$db/alias/$alias2")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"active": true, "desc": "updated by v2"}""")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.result.desc").isEqualTo("updated by v2")

            // Verify via V2 API
            client.get()
                .uri("/graph/v2/service/$db/alias/$alias2")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.desc").isEqualTo("updated by v2")

            // Verify via V3 API
            client.get()
                .uri("/graph/v3/databases/$db/aliases/$alias2")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comment").isEqualTo("updated by v2")
        }
    }
}
