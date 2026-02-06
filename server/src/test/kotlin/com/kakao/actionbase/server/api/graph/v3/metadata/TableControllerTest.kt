package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.server.test.E2ETestBase
import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TableControllerTest : E2ETestBase() {
    private val db = "v3-table-test-db"
    private val baseUri = "/graph/v3/databases/$db/tables"

    @BeforeAll
    fun setup() {
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$db", "comment": "test db"}""")
            .exchange()
            .expectStatus()
            .isOk
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class CrudLifecycleTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            # Edge table - basic CRUD
            - name: v3-edge-crud
              create: |
                {
                  "table": "v3-edge-crud",
                  "schema": {
                    "type": "EDGE",
                    "source": {"type": "string", "comment": "src"},
                    "target": {"type": "string", "comment": "tgt"},
                    "properties": [
                      {"name": "score", "type": "int", "comment": "score", "nullable": true}
                    ],
                    "direction": "OUT",
                    "indexes": [],
                    "groups": []
                  },
                  "storage": "datastore://hbase/v3-edge-crud",
                  "mode": "SYNC",
                  "comment": "edge table"
                }
              expected: |
                {
                  "type": "edge",
                  "table": "v3-edge-crud",
                  "database": "v3-table-test-db",
                  "comment": "edge table",
                  "schema": {
                    "type": "edge",
                    "source": {"type": "string", "comment": "src"},
                    "target": {"type": "string", "comment": "tgt"},
                    "properties": [
                      {"name": "score", "type": "int", "comment": "score", "nullable": true}
                    ],
                    "direction": "OUT"
                  },
                  "storage": {"type": "hbase", "tableName": "v3-edge-crud"},
                  "active": true
                }

            # MultiEdge table - basic CRUD
            - name: v3-multiedge-crud
              create: |
                {
                  "table": "v3-multiedge-crud",
                  "schema": {
                    "type": "MULTI_EDGE",
                    "id": {"type": "long", "comment": "order id"},
                    "source": {"type": "long", "comment": "sender"},
                    "target": {"type": "long", "comment": "receiver"},
                    "properties": [],
                    "direction": "BOTH",
                    "indexes": [],
                    "groups": []
                  },
                  "storage": "datastore://hbase/v3-multiedge-crud",
                  "mode": "SYNC",
                  "comment": "multiedge table"
                }
              expected: |
                {
                  "type": "multiEdge",
                  "table": "v3-multiedge-crud",
                  "database": "v3-table-test-db",
                  "comment": "multiedge table",
                  "schema": {
                    "type": "multiEdge",
                    "id": {"type": "long", "comment": "order id"},
                    "source": {"type": "long", "comment": "sender"},
                    "target": {"type": "long", "comment": "receiver"},
                    "properties": [],
                    "direction": "BOTH"
                  },
                  "storage": {"type": "hbase", "tableName": "v3-multiedge-crud"},
                  "active": true
                }

            # Edge table with properties and indexes
            - name: v3-edge-full
              create: |
                {
                  "table": "v3-edge-full",
                  "schema": {
                    "type": "EDGE",
                    "source": {"type": "long", "comment": "user"},
                    "target": {"type": "long", "comment": "item"},
                    "properties": [
                      {"name": "rating", "type": "int", "comment": "rating", "nullable": true},
                      {"name": "createdat", "type": "long", "comment": "time", "nullable": false}
                    ],
                    "direction": "BOTH",
                    "indexes": [],
                    "groups": []
                  },
                  "storage": "datastore://hbase/v3-edge-full",
                  "mode": "SYNC",
                  "comment": "full edge table"
                }
              expected: |
                {
                  "type": "edge",
                  "table": "v3-edge-full",
                  "database": "v3-table-test-db",
                  "comment": "full edge table",
                  "schema": {
                    "type": "edge",
                    "source": {"type": "long", "comment": "user"},
                    "target": {"type": "long", "comment": "item"},
                    "properties": [
                      {"name": "rating", "type": "int", "comment": "rating", "nullable": true},
                      {"name": "createdat", "type": "long", "comment": "time", "nullable": false}
                    ],
                    "direction": "BOTH"
                  },
                  "storage": {"type": "hbase", "tableName": "v3-edge-full"},
                  "active": true
                }

            # MultiEdge table with properties
            - name: v3-multiedge-full
              create: |
                {
                  "table": "v3-multiedge-full",
                  "schema": {
                    "type": "MULTI_EDGE",
                    "id": {"type": "long", "comment": "txn id"},
                    "source": {"type": "long", "comment": "buyer"},
                    "target": {"type": "long", "comment": "product"},
                    "properties": [
                      {"name": "amount", "type": "int", "comment": "purchase amount", "nullable": false},
                      {"name": "timestamp", "type": "long", "comment": "txn time", "nullable": false}
                    ],
                    "direction": "BOTH",
                    "indexes": [],
                    "groups": []
                  },
                  "storage": "datastore://hbase/v3-multiedge-full",
                  "mode": "SYNC",
                  "comment": "full multiedge table"
                }
              expected: |
                {
                  "type": "multiEdge",
                  "table": "v3-multiedge-full",
                  "database": "v3-table-test-db",
                  "comment": "full multiedge table",
                  "schema": {
                    "type": "multiEdge",
                    "id": {"type": "long", "comment": "txn id"},
                    "source": {"type": "long", "comment": "buyer"},
                    "target": {"type": "long", "comment": "product"},
                    "properties": [
                      {"name": "amount", "type": "int", "comment": "purchase amount", "nullable": false},
                      {"name": "timestamp", "type": "long", "comment": "txn time", "nullable": false}
                    ],
                    "direction": "BOTH"
                  },
                  "storage": {"type": "hbase", "tableName": "v3-multiedge-full"},
                  "active": true
                }
            """,
        )
        fun `create - get - update - deactivate - delete`(
            name: String,
            create: String,
            expected: String,
        ) {
            // Create
            client
                .post()
                .uri(baseUri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(create)
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected)

            // Get
            client
                .get()
                .uri("$baseUri/$name")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected)

            // Update comment
            client
                .put()
                .uri("$baseUri/$name")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"comment": "updated comment"}""")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json("""{"table": "$name", "comment": "updated comment", "active": true}""")

            // Deactivate
            client
                .put()
                .uri("$baseUri/$name")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"active": false}""")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json("""{"table": "$name", "active": false}""")

            // Delete
            client
                .delete()
                .uri("$baseUri/$name")
                .exchange()
                .expectStatus()
                .isNoContent
        }
    }

    @Nested
    inner class ValidationTest {
        @Test
        fun `get non-existent table returns 404`() {
            client
                .get()
                .uri("$baseUri/non-existent")
                .exchange()
                .expectStatus()
                .isNotFound
        }

        @Test
        fun `invalid table name returns 400`() {
            client
                .get()
                .uri("$baseUri/123-invalid")
                .exchange()
                .expectStatus()
                .isBadRequest
        }

        @Test
        fun `table name with dot returns 400`() {
            client
                .get()
                .uri("$baseUri/table.injection")
                .exchange()
                .expectStatus()
                .isBadRequest
        }
    }
}
