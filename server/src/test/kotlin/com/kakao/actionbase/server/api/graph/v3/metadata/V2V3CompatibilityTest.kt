package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
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

        @ParameterizedTest(name = "[{index}] {0}: create={1}, update={2}")
        @CsvSource(
            delimiter = '|',
            value = [
                // scenario                  | createApi | updateApi | name              | createValue     | updateValue
                "V2 create, V3 update        | V2        | V3        | compat-db-v2v3    | created by v2   | updated by v3",
                "V3 create, V2 update        | V3        | V2        | compat-db-v3v2    | created by v3   | updated by v2",
            ],
        )
        fun `database compatibility - create and update across API versions`(
            scenario: String,
            createApi: String,
            updateApi: String,
            name: String,
            createValue: String,
            updateValue: String,
        ) {
            // Create
            if (createApi == "V2") {
                client.post()
                    .uri("/graph/v2/service/$name")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"desc": "$createValue"}""")
                    .exchange()
                    .expectStatus().isOk
                    .expectBody()
                    .jsonPath("$.result.desc").isEqualTo(createValue)
            } else {
                client.post()
                    .uri("/graph/v3/databases/$name")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"database": "$name", "comment": "$createValue"}""")
                    .exchange()
                    .expectStatus().isOk
                    .expectBody()
                    .jsonPath("$.comment").isEqualTo(createValue)
            }

            // Update
            if (updateApi == "V3") {
                client.put()
                    .uri("/graph/v3/databases/$name")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"comment": "$updateValue"}""")
                    .exchange()
                    .expectStatus().isOk
                    .expectBody()
                    .jsonPath("$.comment").isEqualTo(updateValue)
            } else {
                client.put()
                    .uri("/graph/v2/service/$name")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"active": true, "desc": "$updateValue"}""")
                    .exchange()
                    .expectStatus().isOk
                    .expectBody()
                    .jsonPath("$.result.desc").isEqualTo(updateValue)
            }

            // Verify via V2 API (desc)
            client.get()
                .uri("/graph/v2/service/$name")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.desc").isEqualTo(updateValue)

            // Verify via V3 API (comment)
            client.get()
                .uri("/graph/v3/databases/$name")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comment").isEqualTo(updateValue)
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

        @ParameterizedTest(name = "[{index}] {0}: create={1}, update={2}")
        @CsvSource(
            delimiter = '|',
            value = [
                // scenario                  | createApi | updateApi | name              | storage                    | createValue     | updateValue
                "V2 create, V3 update        | V2        | V3        | compat-tbl-v2v3   | compat-tbl-v2v3-storage    | created by v2   | updated by v3",
                "V3 create, V2 update        | V3        | V2        | compat-tbl-v3v2   | compat-tbl-v3v2-storage    | created by v3   | updated by v2",
            ],
        )
        fun `table compatibility - create and update across API versions`(
            scenario: String,
            createApi: String,
            updateApi: String,
            name: String,
            storage: String,
            createValue: String,
            updateValue: String,
        ) {
            // Create
            if (createApi == "V2") {
                val v2Request = """
                    {
                        "desc": "$createValue",
                        "type": "HASH",
                        "schema": {
                            "src": {"type": "STRING", "desc": "source"},
                            "tgt": {"type": "STRING", "desc": "target"},
                            "fields": []
                        },
                        "dirType": "OUT",
                        "storage": "datastore://hbase/$storage"
                    }
                """.trimIndent()

                client.post()
                    .uri("/graph/v2/service/$db/label/$name")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(v2Request)
                    .exchange()
                    .expectStatus().isOk
                    .expectBody()
                    .jsonPath("$.result.desc").isEqualTo(createValue)
            } else {
                val v3Request = """
                    {
                        "schema": {
                            "source": {"type": "STRING", "comment": "source"},
                            "target": {"type": "STRING", "comment": "target"},
                            "properties": [],
                            "direction": "OUT"
                        },
                        "storage": "datastore://hbase/$storage",
                        "mode": "SYNC",
                        "comment": "$createValue"
                    }
                """.trimIndent()

                client.post()
                    .uri("/graph/v3/databases/$db/tables/$name")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(v3Request)
                    .exchange()
                    .expectStatus().isOk
                    .expectBody()
                    .jsonPath("$.comment").isEqualTo(createValue)
            }

            // Update
            if (updateApi == "V3") {
                client.put()
                    .uri("/graph/v3/databases/$db/tables/$name")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"comment": "$updateValue"}""")
                    .exchange()
                    .expectStatus().isOk
                    .expectBody()
                    .jsonPath("$.comment").isEqualTo(updateValue)
            } else {
                client.put()
                    .uri("/graph/v2/service/$db/label/$name")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"active": true, "desc": "$updateValue"}""")
                    .exchange()
                    .expectStatus().isOk
                    .expectBody()
                    .jsonPath("$.result.desc").isEqualTo(updateValue)
            }

            // Verify via V2 API (desc)
            client.get()
                .uri("/graph/v2/service/$db/label/$name")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.desc").isEqualTo(updateValue)

            // Verify via V3 API (comment)
            client.get()
                .uri("/graph/v3/databases/$db/tables/$name")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comment").isEqualTo(updateValue)
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

        @ParameterizedTest(name = "[{index}] {0}: create={1}, update={2}")
        @CsvSource(
            delimiter = '|',
            value = [
                // scenario                  | createApi | updateApi | name              | createValue     | updateValue
                "V2 create, V3 update        | V2        | V3        | compat-als-v2v3   | created by v2   | updated by v3",
                "V3 create, V2 update        | V3        | V2        | compat-als-v3v2   | created by v3   | updated by v2",
            ],
        )
        fun `alias compatibility - create and update across API versions`(
            scenario: String,
            createApi: String,
            updateApi: String,
            name: String,
            createValue: String,
            updateValue: String,
        ) {
            // Create
            if (createApi == "V2") {
                client.post()
                    .uri("/graph/v2/service/$db/alias/$name")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"desc": "$createValue", "target": "$db.$table"}""")
                    .exchange()
                    .expectStatus().isOk
                    .expectBody()
                    .jsonPath("$.result.desc").isEqualTo(createValue)
            } else {
                client.post()
                    .uri("/graph/v3/databases/$db/aliases/$name")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"table": "$table", "comment": "$createValue"}""")
                    .exchange()
                    .expectStatus().isOk
                    .expectBody()
                    .jsonPath("$.comment").isEqualTo(createValue)
            }

            // Update
            if (updateApi == "V3") {
                client.put()
                    .uri("/graph/v3/databases/$db/aliases/$name")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"comment": "$updateValue"}""")
                    .exchange()
                    .expectStatus().isOk
                    .expectBody()
                    .jsonPath("$.comment").isEqualTo(updateValue)
            } else {
                client.put()
                    .uri("/graph/v2/service/$db/alias/$name")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"active": true, "desc": "$updateValue"}""")
                    .exchange()
                    .expectStatus().isOk
                    .expectBody()
                    .jsonPath("$.result.desc").isEqualTo(updateValue)
            }

            // Verify via V2 API (desc)
            client.get()
                .uri("/graph/v2/service/$db/alias/$name")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.desc").isEqualTo(updateValue)

            // Verify via V3 API (comment)
            client.get()
                .uri("/graph/v3/databases/$db/aliases/$name")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comment").isEqualTo(updateValue)
        }
    }
}
