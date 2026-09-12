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
    private val db = "v3_table_test_db"
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
            - name: v3_edge_crud
              create: |
                {
                  "table": "v3_edge_crud",
                  "type": "EDGE",
                  "schema": {
                    "source": {"type": "STRING", "comment": "src"},
                    "target": {"type": "STRING", "comment": "tgt"},
                    "properties": [
                      {"name": "score", "type": "INT", "comment": "score", "nullable": true}
                    ]
                  },
                  "direction": "OUT",
                  "indexes": [],
                  "groups": [],
                  "storage": "datastore://test_namespace/v3_edge_crud",
                  "mode": "SYNC",
                  "comment": "edge table"
                }
              expected: |
                {
                  "table": "v3_edge_crud",
                  "database": "v3_table_test_db",
                  "comment": "edge table",
                  "type": "EDGE",
                  "schema": {
                    "source": {"type": "STRING", "comment": "src"},
                    "target": {"type": "STRING", "comment": "tgt"},
                    "properties": [
                      {"name": "score", "type": "INT", "comment": "score", "nullable": true}
                    ]
                  },
                  "direction": "OUT",
                  "storage": "datastore://test_namespace/v3_edge_crud",
                  "active": true
                }

            # MultiEdge table - basic
            - name: v3_multiedge_crud
              create: |
                {
                  "table": "v3_multiedge_crud",
                  "type": "MULTI_EDGE",
                  "schema": {
                    "source": {"type": "LONG", "comment": "sender"},
                    "target": {"type": "LONG", "comment": "receiver"},
                    "properties": [
                      {"name": "_id", "type": "LONG", "comment": "order id", "nullable": false}
                    ]
                  },
                  "direction": "BOTH",
                  "indexes": [],
                  "groups": [],
                  "storage": "datastore://test_namespace/v3_multiedge_crud",
                  "mode": "SYNC",
                  "comment": "multiedge table"
                }
              expected: |
                {
                  "table": "v3_multiedge_crud",
                  "database": "v3_table_test_db",
                  "comment": "multiedge table",
                  "type": "MULTI_EDGE",
                  "schema": {
                    "source": {"type": "LONG", "comment": "sender"},
                    "target": {"type": "LONG", "comment": "receiver"},
                    "properties": [
                      {"name": "_id", "type": "LONG", "comment": "order id", "nullable": false}
                    ]
                  },
                  "direction": "BOTH",
                  "storage": "datastore://test_namespace/v3_multiedge_crud",
                  "active": true
                }

            # Edge table with properties
            - name: v3_edge_full
              create: |
                {
                  "table": "v3_edge_full",
                  "type": "EDGE",
                  "schema": {
                    "source": {"type": "LONG", "comment": "user"},
                    "target": {"type": "LONG", "comment": "item"},
                    "properties": [
                      {"name": "rating", "type": "INT", "comment": "rating", "nullable": true},
                      {"name": "createdat", "type": "LONG", "comment": "time", "nullable": false}
                    ]
                  },
                  "direction": "BOTH",
                  "indexes": [],
                  "groups": [],
                  "storage": "datastore://test_namespace/v3_edge_full",
                  "mode": "SYNC",
                  "comment": "full edge table"
                }
              expected: |
                {
                  "table": "v3_edge_full",
                  "database": "v3_table_test_db",
                  "comment": "full edge table",
                  "type": "EDGE",
                  "schema": {
                    "source": {"type": "LONG", "comment": "user"},
                    "target": {"type": "LONG", "comment": "item"},
                    "properties": [
                      {"name": "rating", "type": "INT", "comment": "rating", "nullable": true},
                      {"name": "createdat", "type": "LONG", "comment": "time", "nullable": false}
                    ]
                  },
                  "direction": "BOTH",
                  "storage": "datastore://test_namespace/v3_edge_full",
                  "active": true
                }

            # MultiEdge table with properties
            - name: v3_multiedge_full
              create: |
                {
                  "table": "v3_multiedge_full",
                  "type": "MULTI_EDGE",
                  "schema": {
                    "source": {"type": "LONG", "comment": "buyer"},
                    "target": {"type": "LONG", "comment": "product"},
                    "properties": [
                      {"name": "_id", "type": "LONG", "comment": "txn id", "nullable": false},
                      {"name": "amount", "type": "INT", "comment": "purchase amount", "nullable": false},
                      {"name": "timestamp", "type": "LONG", "comment": "txn time", "nullable": false}
                    ]
                  },
                  "direction": "BOTH",
                  "indexes": [],
                  "groups": [],
                  "storage": "datastore://test_namespace/v3_multiedge_full",
                  "mode": "SYNC",
                  "comment": "full multiedge table"
                }
              expected: |
                {
                  "table": "v3_multiedge_full",
                  "database": "v3_table_test_db",
                  "comment": "full multiedge table",
                  "type": "MULTI_EDGE",
                  "schema": {
                    "source": {"type": "LONG", "comment": "buyer"},
                    "target": {"type": "LONG", "comment": "product"},
                    "properties": [
                      {"name": "_id", "type": "LONG", "comment": "txn id", "nullable": false},
                      {"name": "amount", "type": "INT", "comment": "purchase amount", "nullable": false},
                      {"name": "timestamp", "type": "LONG", "comment": "txn time", "nullable": false}
                    ]
                  },
                  "direction": "BOTH",
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
                .json("""{"status": "CREATED", "result": $expected}""")

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
            - name: v3_edge_upd
              create: |
                {
                  "table": "v3_edge_upd",
                  "type": "EDGE",
                  "schema": {
                    "source": {"type": "STRING", "comment": "src"},
                    "target": {"type": "STRING", "comment": "tgt"},
                    "properties": []
                  },
                  "direction": "OUT",
                  "indexes": [],
                  "groups": [],
                  "storage": "datastore://test_namespace/v3_edge_upd",
                  "mode": "SYNC",
                  "comment": "edge table"
                }
              update: |
                {"comment": "updated edge"}
              expected: |
                {"table": "v3_edge_upd", "comment": "updated edge", "active": true}
            - name: v3_multiedge_upd
              create: |
                {
                  "table": "v3_multiedge_upd",
                  "type": "MULTI_EDGE",
                  "schema": {
                    "source": {"type": "LONG", "comment": "src"},
                    "target": {"type": "LONG", "comment": "tgt"},
                    "properties": [
                      {"name": "_id", "type": "LONG", "comment": "id", "nullable": false}
                    ]
                  },
                  "direction": "BOTH",
                  "indexes": [],
                  "groups": [],
                  "storage": "datastore://test_namespace/v3_multiedge_upd",
                  "mode": "SYNC",
                  "comment": "multiedge table"
                }
              update: |
                {"comment": "updated multiedge"}
              expected: |
                {"table": "v3_multiedge_upd", "comment": "updated multiedge", "active": true}
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
                .json("""{"status": "UPDATED", "result": $expected}""")
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            shared = """
              deactivate: |
                {"active": false}
            """,
            cases = """
            - name: v3_edge_deact
              create: |
                {
                  "table": "v3_edge_deact",
                  "type": "EDGE",
                  "schema": {
                    "source": {"type": "STRING", "comment": "src"},
                    "target": {"type": "STRING", "comment": "tgt"},
                    "properties": []
                  },
                  "direction": "OUT",
                  "indexes": [],
                  "groups": [],
                  "storage": "datastore://test_namespace/v3_edge_deact",
                  "mode": "SYNC",
                  "comment": "edge table"
                }
              expected: |
                {"table": "v3_edge_deact", "active": false}
            - name: v3_multiedge_deact
              create: |
                {
                  "table": "v3_multiedge_deact",
                  "type": "MULTI_EDGE",
                  "schema": {
                    "source": {"type": "LONG", "comment": "src"},
                    "target": {"type": "LONG", "comment": "tgt"},
                    "properties": [
                      {"name": "_id", "type": "LONG", "comment": "id", "nullable": false}
                    ]
                  },
                  "direction": "BOTH",
                  "indexes": [],
                  "groups": [],
                  "storage": "datastore://test_namespace/v3_multiedge_deact",
                  "mode": "SYNC",
                  "comment": "multiedge table"
                }
              expected: |
                {"table": "v3_multiedge_deact", "active": false}
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
                .json("""{"status": "UPDATED", "result": $expected}""")
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
            - name: v3_edge_react
              create: |
                {
                  "table": "v3_edge_react",
                  "type": "EDGE",
                  "schema": {
                    "source": {"type": "STRING", "comment": "src"},
                    "target": {"type": "STRING", "comment": "tgt"},
                    "properties": []
                  },
                  "direction": "OUT",
                  "indexes": [],
                  "groups": [],
                  "storage": "datastore://test_namespace/v3_edge_react",
                  "mode": "SYNC",
                  "comment": "edge table"
                }
              expected: |
                {"table": "v3_edge_react", "active": true}
            - name: v3_multiedge_react
              create: |
                {
                  "table": "v3_multiedge_react",
                  "type": "MULTI_EDGE",
                  "schema": {
                    "source": {"type": "LONG", "comment": "src"},
                    "target": {"type": "LONG", "comment": "tgt"},
                    "properties": [
                      {"name": "_id", "type": "LONG", "comment": "id", "nullable": false}
                    ]
                  },
                  "direction": "BOTH",
                  "indexes": [],
                  "groups": [],
                  "storage": "datastore://test_namespace/v3_multiedge_react",
                  "mode": "SYNC",
                  "comment": "multiedge table"
                }
              expected: |
                {"table": "v3_multiedge_react", "active": true}
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
                .json("""{"status": "UPDATED", "result": $expected}""")
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            shared = """
              deactivate: |
                {"active": false}
            """,
            cases = """
            - name: v3_edge_del
              create: |
                {
                  "table": "v3_edge_del",
                  "type": "EDGE",
                  "schema": {
                    "source": {"type": "STRING", "comment": "src"},
                    "target": {"type": "STRING", "comment": "tgt"},
                    "properties": []
                  },
                  "direction": "OUT",
                  "indexes": [],
                  "groups": [],
                  "storage": "datastore://test_namespace/v3_edge_del",
                  "mode": "SYNC",
                  "comment": "edge table"
                }
            - name: v3_multiedge_del
              create: |
                {
                  "table": "v3_multiedge_del",
                  "type": "MULTI_EDGE",
                  "schema": {
                    "source": {"type": "LONG", "comment": "src"},
                    "target": {"type": "LONG", "comment": "tgt"},
                    "properties": [
                      {"name": "_id", "type": "LONG", "comment": "id", "nullable": false}
                    ]
                  },
                  "direction": "BOTH",
                  "indexes": [],
                  "groups": [],
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
                .isOk
                .expectBody()
                .json("""{"status": "DELETED"}""")
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class StatusFilterTest {
        private val tableName = "v3_tbl_status_filter"

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
                      "type": "EDGE",
                      "schema": {
                        "source": {"type": "STRING", "comment": "src"},
                        "target": {"type": "STRING", "comment": "tgt"},
                        "properties": []
                      },
                      "direction": "OUT",
                      "indexes": [],
                      "groups": [],
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
                .jsonPath("$.content[?(@.table == '$tableName')]")
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
                .jsonPath("$.content[?(@.table == '$tableName')]")
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
                .jsonPath("$.content[?(@.table == '$tableName')]")
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
                .jsonPath("$.content[?(@.table == '$tableName')]")
                .exists()
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class CacheTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: v3_edge_cache_crud
              create: |
                {
                  "table": "v3_edge_cache_crud",
                  "type": "EDGE",
                  "schema": {
                    "source": {"type": "LONG", "comment": "user"},
                    "target": {"type": "LONG", "comment": "item"},
                    "properties": [
                      {"name": "score", "type": "INT", "comment": "score", "nullable": true}
                    ]
                  },
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
                  ],
                  "storage": "datastore://test_namespace/v3_edge_cache_crud",
                  "mode": "SYNC",
                  "comment": "edge table with cache"
                }
              expected: |
                {
                  "table": "v3_edge_cache_crud",
                  "type": "EDGE",
                  "caches": [
                    {
                      "cache": "top_items",
                      "fields": [{"field": "score", "order": "DESC"}],
                      "limit": 50,
                      "comment": "top 50 items"
                    }
                  ],
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
                .json("""{"status": "CREATED", "result": $expected}""")

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
            - name: v3_edge_cache_upd
              create: |
                {
                  "table": "v3_edge_cache_upd",
                  "type": "EDGE",
                  "schema": {
                    "source": {"type": "LONG", "comment": "user"},
                    "target": {"type": "LONG", "comment": "item"},
                    "properties": [
                      {"name": "score", "type": "INT", "comment": "score", "nullable": true}
                    ]
                  },
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
                  ],
                  "storage": "datastore://test_namespace/v3_edge_cache_upd",
                  "mode": "SYNC",
                  "comment": "edge table"
                }
              update: |
                {
                  "type": "EDGE",
                  "schema": {
                    "source": {"type": "LONG", "comment": "user"},
                    "target": {"type": "LONG", "comment": "item"},
                    "properties": [
                      {"name": "score", "type": "INT", "comment": "score", "nullable": true}
                    ]
                  },
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
              expected: |
                {
                  "table": "v3_edge_cache_upd",
                  "caches": [
                    {
                      "cache": "new_cache",
                      "fields": [{"field": "score", "order": "DESC"}],
                      "limit": 100,
                      "comment": "new"
                    }
                  ],
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
                .json("""{"status": "UPDATED", "result": $expected}""")

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
                .uri("$baseUri/non_existent")
                .exchange()
                .expectStatus()
                .isNotFound
        }

        @Test
        fun `invalid table name returns 400`() {
            client
                .post()
                .uri(baseUri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"table": "123invalid", "schema": null, "storage": "", "comment": ""}""")
                .exchange()
                .expectStatus()
                .isBadRequest
        }

        @Test
        fun `table name with hyphen returns 400`() {
            client
                .post()
                .uri(baseUri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"table": "my-table", "schema": null, "storage": "", "comment": ""}""")
                .exchange()
                .expectStatus()
                .isBadRequest
        }

        @Test
        fun `table name with dot returns 400`() {
            client
                .post()
                .uri(baseUri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"table": "table.injection", "schema": null, "storage": "", "comment": ""}""")
                .exchange()
                .expectStatus()
                .isBadRequest
        }
    }
}
