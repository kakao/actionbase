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
    inner class CrudTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            # Edge table - basic
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
                  "storage": "datastore://test_namespace/v3_edge_crud",
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
                  "storage": "datastore://test_namespace/v3_edge_crud",
                  "active": true
                }

            # MultiEdge table - basic
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
                  "storage": "datastore://test_namespace/v3_multiedge_crud",
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
                  "storage": "datastore://test_namespace/v3_multiedge_crud",
                  "active": true
                }

            # Edge table with properties
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
                  "storage": "datastore://test_namespace/v3_edge_full",
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
                  "storage": "datastore://test_namespace/v3_edge_full",
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
                  "storage": "datastore://test_namespace/v3_multiedge_full",
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
                  "storage": "datastore://test_namespace/v3_multiedge_full",
                  "active": true
                }
            """,
        )
        fun `create table`(
            name: String,
            create: String,
            expected: String,
        ) {
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

            client
                .get()
                .uri("$baseUri/$name")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: v3-edge-upd
              create: |
                {
                  "table": "v3-edge-upd",
                  "schema": {
                    "type": "EDGE",
                    "source": {"type": "string", "comment": "src"},
                    "target": {"type": "string", "comment": "tgt"},
                    "properties": [],
                    "direction": "OUT",
                    "indexes": [],
                    "groups": []
                  },
                  "storage": "datastore://test_namespace/v3_edge_upd",
                  "mode": "SYNC",
                  "comment": "edge table"
                }
              update: |
                {"comment": "updated edge"}
              expected: |
                {"table": "v3-edge-upd", "comment": "updated edge", "active": true}
            - name: v3-multiedge-upd
              create: |
                {
                  "table": "v3-multiedge-upd",
                  "schema": {
                    "type": "MULTI_EDGE",
                    "id": {"type": "long", "comment": "id"},
                    "source": {"type": "long", "comment": "src"},
                    "target": {"type": "long", "comment": "tgt"},
                    "properties": [],
                    "direction": "BOTH",
                    "indexes": [],
                    "groups": []
                  },
                  "storage": "datastore://test_namespace/v3_multiedge_upd",
                  "mode": "SYNC",
                  "comment": "multiedge table"
                }
              update: |
                {"comment": "updated multiedge"}
              expected: |
                {"table": "v3-multiedge-upd", "comment": "updated multiedge", "active": true}
            """,
        )
        fun `update table`(
            name: String,
            create: String,
            update: String,
            expected: String,
        ) {
            // precondition
            client
                .post()
                .uri(baseUri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(create)
                .exchange()
                .expectStatus()
                .isOk

            client
                .put()
                .uri("$baseUri/$name")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(update)
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            shared = """
              deactivate: |
                {"active": false}
            """,
            cases = """
            - name: v3-edge-deact
              create: |
                {
                  "table": "v3-edge-deact",
                  "schema": {
                    "type": "EDGE",
                    "source": {"type": "string", "comment": "src"},
                    "target": {"type": "string", "comment": "tgt"},
                    "properties": [],
                    "direction": "OUT",
                    "indexes": [],
                    "groups": []
                  },
                  "storage": "datastore://test_namespace/v3_edge_deact",
                  "mode": "SYNC",
                  "comment": "edge table"
                }
              expected: |
                {"table": "v3-edge-deact", "active": false}
            - name: v3-multiedge-deact
              create: |
                {
                  "table": "v3-multiedge-deact",
                  "schema": {
                    "type": "MULTI_EDGE",
                    "id": {"type": "long", "comment": "id"},
                    "source": {"type": "long", "comment": "src"},
                    "target": {"type": "long", "comment": "tgt"},
                    "properties": [],
                    "direction": "BOTH",
                    "indexes": [],
                    "groups": []
                  },
                  "storage": "datastore://test_namespace/v3_multiedge_deact",
                  "mode": "SYNC",
                  "comment": "multiedge table"
                }
              expected: |
                {"table": "v3-multiedge-deact", "active": false}
            """,
        )
        fun `deactivate table`(
            name: String,
            create: String,
            deactivate: String,
            expected: String,
        ) {
            // precondition
            client
                .post()
                .uri(baseUri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(create)
                .exchange()
                .expectStatus()
                .isOk

            client
                .put()
                .uri("$baseUri/$name")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(deactivate)
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            shared = """
              deactivate: |
                {"active": false}
              reactivate: |
                {"active": true}
            """,
            cases = """
            - name: v3-edge-react
              create: |
                {
                  "table": "v3-edge-react",
                  "schema": {
                    "type": "EDGE",
                    "source": {"type": "string", "comment": "src"},
                    "target": {"type": "string", "comment": "tgt"},
                    "properties": [],
                    "direction": "OUT",
                    "indexes": [],
                    "groups": []
                  },
                  "storage": "datastore://test_namespace/v3_edge_react",
                  "mode": "SYNC",
                  "comment": "edge table"
                }
              expected: |
                {"table": "v3-edge-react", "active": true}
            - name: v3-multiedge-react
              create: |
                {
                  "table": "v3-multiedge-react",
                  "schema": {
                    "type": "MULTI_EDGE",
                    "id": {"type": "long", "comment": "id"},
                    "source": {"type": "long", "comment": "src"},
                    "target": {"type": "long", "comment": "tgt"},
                    "properties": [],
                    "direction": "BOTH",
                    "indexes": [],
                    "groups": []
                  },
                  "storage": "datastore://test_namespace/v3_multiedge_react",
                  "mode": "SYNC",
                  "comment": "multiedge table"
                }
              expected: |
                {"table": "v3-multiedge-react", "active": true}
            """,
        )
        fun `reactivate table`(
            name: String,
            create: String,
            deactivate: String,
            reactivate: String,
            expected: String,
        ) {
            // precondition: create + deactivate
            client
                .post()
                .uri(baseUri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(create)
                .exchange()
                .expectStatus()
                .isOk

            client
                .put()
                .uri("$baseUri/$name")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(deactivate)
                .exchange()
                .expectStatus()
                .isOk

            client
                .put()
                .uri("$baseUri/$name")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(reactivate)
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            shared = """
              deactivate: |
                {"active": false}
            """,
            cases = """
            - name: v3-edge-del
              create: |
                {
                  "table": "v3-edge-del",
                  "schema": {
                    "type": "EDGE",
                    "source": {"type": "string", "comment": "src"},
                    "target": {"type": "string", "comment": "tgt"},
                    "properties": [],
                    "direction": "OUT",
                    "indexes": [],
                    "groups": []
                  },
                  "storage": "datastore://test_namespace/v3_edge_del",
                  "mode": "SYNC",
                  "comment": "edge table"
                }
            - name: v3-multiedge-del
              create: |
                {
                  "table": "v3-multiedge-del",
                  "schema": {
                    "type": "MULTI_EDGE",
                    "id": {"type": "long", "comment": "id"},
                    "source": {"type": "long", "comment": "src"},
                    "target": {"type": "long", "comment": "tgt"},
                    "properties": [],
                    "direction": "BOTH",
                    "indexes": [],
                    "groups": []
                  },
                  "storage": "datastore://test_namespace/v3_multiedge_del",
                  "mode": "SYNC",
                  "comment": "multiedge table"
                }
            """,
        )
        fun `delete table`(
            name: String,
            create: String,
            deactivate: String,
        ) {
            // precondition: create + deactivate
            client
                .post()
                .uri(baseUri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(create)
                .exchange()
                .expectStatus()
                .isOk

            client
                .put()
                .uri("$baseUri/$name")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(deactivate)
                .exchange()
                .expectStatus()
                .isOk

            client
                .delete()
                .uri("$baseUri/$name")
                .exchange()
                .expectStatus()
                .isNoContent
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class StatusFilterTest {
        private val tableName = "v3-tbl-status-filter"

        @BeforeAll
        fun setup() {
            client
                .post()
                .uri(baseUri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    """
                    {
                      "table": "$tableName",
                      "schema": {
                        "type": "EDGE",
                        "source": {"type": "string", "comment": "src"},
                        "target": {"type": "string", "comment": "tgt"},
                        "properties": [],
                        "direction": "OUT",
                        "indexes": [],
                        "groups": []
                      },
                      "storage": "datastore://test_namespace/v3_tbl_status_filter",
                      "mode": "SYNC",
                      "comment": "status filter test"
                    }
                    """.trimIndent(),
                ).exchange()
                .expectStatus()
                .isOk

            client
                .put()
                .uri("$baseUri/$tableName")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"active": false}""")
                .exchange()
                .expectStatus()
                .isOk
        }

        @Test
        fun `default status excludes inactive tables`() {
            client
                .get()
                .uri(baseUri)
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$[?(@.table == '$tableName')]")
                .doesNotExist()
        }

        @Test
        fun `status=ACTIVE excludes inactive tables`() {
            client
                .get()
                .uri("$baseUri?status=ACTIVE")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$[?(@.table == '$tableName')]")
                .doesNotExist()
        }

        @Test
        fun `status=INACTIVE returns only inactive tables`() {
            client
                .get()
                .uri("$baseUri?status=INACTIVE")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$[?(@.table == '$tableName')]")
                .exists()
        }

        @Test
        fun `status=ALL returns both active and inactive tables`() {
            client
                .get()
                .uri("$baseUri?status=ALL")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$[?(@.table == '$tableName')]")
                .exists()
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class CacheTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: v3-edge-cache-crud
              create: |
                {
                  "table": "v3-edge-cache-crud",
                  "schema": {
                    "type": "EDGE",
                    "source": {"type": "long", "comment": "user"},
                    "target": {"type": "long", "comment": "item"},
                    "properties": [
                      {"name": "score", "type": "int", "comment": "score", "nullable": true}
                    ],
                    "direction": "OUT",
                    "indexes": [],
                    "groups": [],
                    "caches": [
                      {
                        "cache": "top_items",
                        "fields": [{"field": "score", "order": "DESC"}],
                        "limit": 50,
                        "comment": "top 50 items"
                      }
                    ]
                  },
                  "storage": "datastore://test_namespace/v3_edge_cache_crud",
                  "mode": "SYNC",
                  "comment": "edge table with cache"
                }
              expected: |
                {
                  "type": "edge",
                  "table": "v3-edge-cache-crud",
                  "schema": {
                    "type": "edge",
                    "caches": [
                      {
                        "cache": "top_items",
                        "fields": [{"field": "score", "order": "DESC"}],
                        "limit": 50,
                        "comment": "top 50 items"
                      }
                    ]
                  },
                  "active": true
                }
            """,
        )
        fun `create table preserves caches on response and get`(
            name: String,
            create: String,
            expected: String,
        ) {
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

            client
                .get()
                .uri("$baseUri/$name")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: v3-edge-cache-upd
              create: |
                {
                  "table": "v3-edge-cache-upd",
                  "schema": {
                    "type": "EDGE",
                    "source": {"type": "long", "comment": "user"},
                    "target": {"type": "long", "comment": "item"},
                    "properties": [
                      {"name": "score", "type": "int", "comment": "score", "nullable": true}
                    ],
                    "direction": "OUT",
                    "indexes": [],
                    "groups": [],
                    "caches": [
                      {
                        "cache": "old_cache",
                        "fields": [{"field": "score", "order": "ASC"}],
                        "limit": 10,
                        "comment": "old"
                      }
                    ]
                  },
                  "storage": "datastore://test_namespace/v3_edge_cache_upd",
                  "mode": "SYNC",
                  "comment": "edge table"
                }
              update: |
                {
                  "schema": {
                    "type": "EDGE",
                    "source": {"type": "long", "comment": "user"},
                    "target": {"type": "long", "comment": "item"},
                    "properties": [
                      {"name": "score", "type": "int", "comment": "score", "nullable": true}
                    ],
                    "direction": "OUT",
                    "indexes": [],
                    "groups": [],
                    "caches": [
                      {
                        "cache": "new_cache",
                        "fields": [{"field": "score", "order": "DESC"}],
                        "limit": 100,
                        "comment": "new"
                      }
                    ]
                  }
                }
              expected: |
                {
                  "table": "v3-edge-cache-upd",
                  "schema": {
                    "caches": [
                      {
                        "cache": "new_cache",
                        "fields": [{"field": "score", "order": "DESC"}],
                        "limit": 100,
                        "comment": "new"
                      }
                    ]
                  },
                  "active": true
                }
            """,
        )
        fun `update table changes caches and reflects on get`(
            name: String,
            create: String,
            update: String,
            expected: String,
        ) {
            // precondition: create with initial cache
            client
                .post()
                .uri(baseUri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(create)
                .exchange()
                .expectStatus()
                .isOk

            // update cache
            client
                .put()
                .uri("$baseUri/$name")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(update)
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected)

            // verify persistence via get
            client
                .get()
                .uri("$baseUri/$name")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected)
        }
    }

    @Nested
    inner class ValidationTest {
        @Test
        fun `invalid status value returns 400`() {
            client
                .get()
                .uri("$baseUri?status=BOGUS")
                .exchange()
                .expectStatus()
                .isBadRequest
        }

        @Test
        fun `lowercase status value returns 400`() {
            client
                .get()
                .uri("$baseUri?status=active")
                .exchange()
                .expectStatus()
                .isBadRequest
        }

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
