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
 * Verifies that the same data can be represented in V2/V3 JSON formats
 * and that cross-version operations work correctly.
 *
 * Terminology:
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
            - name: db-v2v3-basic
              create: |
                {"desc": "test database"}
              expected: |
                {"database": "db-v2v3-basic", "comment": "test database", "active": true}
            - name: db-v2v3-empty
              create: |
                {"desc": ""}
              expected: |
                {"database": "db-v2v3-empty", "comment": "", "active": true}
            - name: db-v2v3-special
              create: |
                {"desc": "test @#$%"}
              expected: |
                {"database": "db-v2v3-special", "comment": "test @#$%", "active": true}
            """,
        )
        fun `V2 create - V3 get`(
            name: String,
            create: String,
            expected: String,
        ) {
            client
                .post()
                .uri("/graph/v2/service/$name")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(create)
                .exchange()
                .expectStatus()
                .isOk

            client
                .get()
                .uri("/graph/v3/databases/$name")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: db-v3v2-basic
              create: |
                {"database": "db-v3v2-basic", "comment": "test database"}
              expected: |
                {"name": "db-v3v2-basic", "desc": "test database", "active": true}
            - name: db-v3v2-empty
              create: |
                {"database": "db-v3v2-empty", "comment": ""}
              expected: |
                {"name": "db-v3v2-empty", "desc": "", "active": true}
            - name: db-v3v2-special
              create: |
                {"database": "db-v3v2-special", "comment": "test @#$%"}
              expected: |
                {"name": "db-v3v2-special", "desc": "test @#$%", "active": true}
            """,
        )
        fun `V3 create - V2 get`(
            name: String,
            create: String,
            expected: String,
        ) {
            client
                .post()
                .uri("/graph/v3/databases")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(create)
                .exchange()
                .expectStatus()
                .isOk

            client
                .get()
                .uri("/graph/v2/service/$name")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected)
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class TableCompatibilityTest {
        private val db = "tbl-compat-db"

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

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            # Direction: OUT
            - name: tbl-v2v3-out
              create: |
                {
                  "desc": "direction out",
                  "type": "INDEXED",
                  "schema": {
                    "src": {"type": "STRING", "desc": "source"},
                    "tgt": {"type": "STRING", "desc": "target"},
                    "fields": []
                  },
                  "dirType": "OUT",
                  "storage": "datastore://test_namespace/tbl_v2v3_out"
                }
              expected: |
                {
                  "table": "tbl-v2v3-out",
                  "comment": "direction out",
                  "schema": {
                    "type": "edge",
                    "source": {"type": "string", "comment": "source"},
                    "target": {"type": "string", "comment": "target"},
                    "properties": [],
                    "direction": "OUT"
                  },
                  "storage": "datastore://test_namespace/tbl_v2v3_out",
                  "active": true
                }

            # Direction: IN
            - name: tbl-v2v3-in
              create: |
                {
                  "desc": "direction in",
                  "type": "INDEXED",
                  "schema": {
                    "src": {"type": "STRING", "desc": "source"},
                    "tgt": {"type": "STRING", "desc": "target"},
                    "fields": []
                  },
                  "dirType": "IN",
                  "storage": "datastore://test_namespace/tbl_v2v3_in"
                }
              expected: |
                {
                  "table": "tbl-v2v3-in",
                  "comment": "direction in",
                  "schema": {
                    "type": "edge",
                    "source": {"type": "string", "comment": "source"},
                    "target": {"type": "string", "comment": "target"},
                    "properties": [],
                    "direction": "IN"
                  },
                  "storage": "datastore://test_namespace/tbl_v2v3_in",
                  "active": true
                }

            # Direction: BOTH
            - name: tbl-v2v3-both
              create: |
                {
                  "desc": "direction both",
                  "type": "INDEXED",
                  "schema": {
                    "src": {"type": "STRING", "desc": "source"},
                    "tgt": {"type": "STRING", "desc": "target"},
                    "fields": []
                  },
                  "dirType": "BOTH",
                  "storage": "datastore://test_namespace/tbl_v2v3_both"
                }
              expected: |
                {
                  "table": "tbl-v2v3-both",
                  "comment": "direction both",
                  "schema": {
                    "type": "edge",
                    "source": {"type": "string", "comment": "source"},
                    "target": {"type": "string", "comment": "target"},
                    "properties": [],
                    "direction": "BOTH"
                  },
                  "storage": "datastore://test_namespace/tbl_v2v3_both",
                  "active": true
                }

            # With properties
            - name: tbl-v2v3-props
              create: |
                {
                  "desc": "with props",
                  "type": "INDEXED",
                  "schema": {
                    "src": {"type": "STRING", "desc": "user"},
                    "tgt": {"type": "STRING", "desc": "item"},
                    "fields": [
                      {"name": "rating", "type": "INT", "nullable": true, "desc": "rating"},
                      {"name": "createdat", "type": "LONG", "nullable": true, "desc": "time"}
                    ]
                  },
                  "dirType": "OUT",
                  "storage": "datastore://test_namespace/tbl_v2v3_props"
                }
              expected: |
                {
                  "table": "tbl-v2v3-props",
                  "comment": "with props",
                  "schema": {
                    "type": "edge",
                    "source": {"type": "string", "comment": "user"},
                    "target": {"type": "string", "comment": "item"},
                    "properties": [
                      {"name": "rating", "type": "int", "comment": "rating", "nullable": true},
                      {"name": "createdat", "type": "long", "comment": "time", "nullable": true}
                    ],
                    "direction": "OUT"
                  },
                  "storage": "datastore://test_namespace/tbl_v2v3_props",
                  "active": true
                }

            # LONG keys
            - name: tbl-v2v3-long
              create: |
                {
                  "desc": "long keys",
                  "type": "INDEXED",
                  "schema": {
                    "src": {"type": "LONG", "desc": "uid"},
                    "tgt": {"type": "LONG", "desc": "iid"},
                    "fields": []
                  },
                  "dirType": "OUT",
                  "storage": "datastore://test_namespace/tbl_v2v3_long"
                }
              expected: |
                {
                  "table": "tbl-v2v3-long",
                  "comment": "long keys",
                  "schema": {
                    "type": "edge",
                    "source": {"type": "long", "comment": "uid"},
                    "target": {"type": "long", "comment": "iid"},
                    "properties": [],
                    "direction": "OUT"
                  },
                  "storage": "datastore://test_namespace/tbl_v2v3_long",
                  "active": true
                }
            """,
        )
        fun `V2 create - V3 get`(
            name: String,
            create: String,
            expected: String,
        ) {
            client
                .post()
                .uri("/graph/v2/service/$db/label/$name")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(create)
                .exchange()
                .expectStatus()
                .isOk

            client
                .get()
                .uri("/graph/v3/databases/$db/tables/$name")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            # Direction: OUT
            - name: tbl-v3v2-out
              create: |
                {
                  "table": "tbl-v3v2-out",
                  "schema": {
                    "type": "edge",
                    "source": {"type": "string", "comment": "source"},
                    "target": {"type": "string", "comment": "target"},
                    "properties": [],
                    "direction": "OUT",
                    "indexes": [],
                    "groups": []
                  },
                  "storage": "datastore://test_namespace/tbl_v3v2_out",
                  "mode": "SYNC",
                  "comment": "direction out"
                }
              expected: |
                {
                  "name": "tbl-compat-db.tbl-v3v2-out",
                  "desc": "direction out",
                  "schema": {
                    "src": {"type": "STRING", "desc": "source"},
                    "tgt": {"type": "STRING", "desc": "target"},
                    "fields": []
                  },
                  "dirType": "OUT",
                  "storage": "datastore://test_namespace/tbl_v3v2_out",
                  "active": true
                }

            # Direction: IN
            - name: tbl-v3v2-in
              create: |
                {
                  "table": "tbl-v3v2-in",
                  "schema": {
                    "type": "edge",
                    "source": {"type": "string", "comment": "source"},
                    "target": {"type": "string", "comment": "target"},
                    "properties": [],
                    "direction": "IN",
                    "indexes": [],
                    "groups": []
                  },
                  "storage": "datastore://test_namespace/tbl_v3v2_in",
                  "mode": "SYNC",
                  "comment": "direction in"
                }
              expected: |
                {
                  "name": "tbl-compat-db.tbl-v3v2-in",
                  "desc": "direction in",
                  "schema": {
                    "src": {"type": "STRING", "desc": "source"},
                    "tgt": {"type": "STRING", "desc": "target"},
                    "fields": []
                  },
                  "dirType": "IN",
                  "storage": "datastore://test_namespace/tbl_v3v2_in",
                  "active": true
                }

            # Direction: BOTH
            - name: tbl-v3v2-both
              create: |
                {
                  "table": "tbl-v3v2-both",
                  "schema": {
                    "type": "edge",
                    "source": {"type": "string", "comment": "source"},
                    "target": {"type": "string", "comment": "target"},
                    "properties": [],
                    "direction": "BOTH",
                    "indexes": [],
                    "groups": []
                  },
                  "storage": "datastore://test_namespace/tbl_v3v2_both",
                  "mode": "SYNC",
                  "comment": "direction both"
                }
              expected: |
                {
                  "name": "tbl-compat-db.tbl-v3v2-both",
                  "desc": "direction both",
                  "schema": {
                    "src": {"type": "STRING", "desc": "source"},
                    "tgt": {"type": "STRING", "desc": "target"},
                    "fields": []
                  },
                  "dirType": "BOTH",
                  "storage": "datastore://test_namespace/tbl_v3v2_both",
                  "active": true
                }

            # With properties
            - name: tbl-v3v2-props
              create: |
                {
                  "table": "tbl-v3v2-props",
                  "schema": {
                    "type": "edge",
                    "source": {"type": "string", "comment": "user"},
                    "target": {"type": "string", "comment": "item"},
                    "properties": [
                      {"name": "rating", "type": "int", "comment": "rating", "nullable": true},
                      {"name": "createdat", "type": "long", "comment": "time", "nullable": true}
                    ],
                    "direction": "OUT",
                    "indexes": [],
                    "groups": []
                  },
                  "storage": "datastore://test_namespace/tbl_v3v2_props",
                  "mode": "SYNC",
                  "comment": "with props"
                }
              expected: |
                {
                  "name": "tbl-compat-db.tbl-v3v2-props",
                  "desc": "with props",
                  "schema": {
                    "src": {"type": "STRING", "desc": "user"},
                    "tgt": {"type": "STRING", "desc": "item"},
                    "fields": [
                      {"name": "rating", "type": "INT", "nullable": true, "desc": "rating"},
                      {"name": "createdat", "type": "LONG", "nullable": true, "desc": "time"}
                    ]
                  },
                  "dirType": "OUT",
                  "storage": "datastore://test_namespace/tbl_v3v2_props",
                  "active": true
                }

            # LONG keys
            - name: tbl-v3v2-long
              create: |
                {
                  "table": "tbl-v3v2-long",
                  "schema": {
                    "type": "edge",
                    "source": {"type": "long", "comment": "uid"},
                    "target": {"type": "long", "comment": "iid"},
                    "properties": [],
                    "direction": "OUT",
                    "indexes": [],
                    "groups": []
                  },
                  "storage": "datastore://test_namespace/tbl_v3v2_long",
                  "mode": "SYNC",
                  "comment": "long keys"
                }
              expected: |
                {
                  "name": "tbl-compat-db.tbl-v3v2-long",
                  "desc": "long keys",
                  "schema": {
                    "src": {"type": "LONG", "desc": "uid"},
                    "tgt": {"type": "LONG", "desc": "iid"},
                    "fields": []
                  },
                  "dirType": "OUT",
                  "storage": "datastore://test_namespace/tbl_v3v2_long",
                  "active": true
                }
            """,
        )
        fun `V3 create - V2 get`(
            name: String,
            create: String,
            expected: String,
        ) {
            client
                .post()
                .uri("/graph/v3/databases/$db/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(create)
                .exchange()
                .expectStatus()
                .isOk

            client
                .get()
                .uri("/graph/v2/service/$db/label/$name")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected)
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class AliasCompatibilityTest {
        private val db = "als-compat-db"
        private val table = "als-target"

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

            client
                .post()
                .uri("/graph/v3/databases/$db/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    """
                    {
                      "table": "$table",
                      "schema": {
                        "type": "edge",
                        "source": {"type": "string", "comment": "src"},
                        "target": {"type": "string", "comment": "tgt"},
                        "properties": [],
                        "direction": "OUT",
                        "indexes": [],
                        "groups": []
                      },
                      "storage": "datastore://test_namespace/als_target_storage",
                      "mode": "SYNC",
                      "comment": "target table"
                    }
                    """.trimIndent(),
                ).exchange()
                .expectStatus()
                .isOk
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: als-v2v3-basic
              create: |
                {"desc": "test alias", "target": "als-compat-db.als-target"}
              expected: |
                {"alias": "als-v2v3-basic", "table": "als-target", "comment": "test alias", "active": true}
            - name: als-v2v3-empty
              create: |
                {"desc": "", "target": "als-compat-db.als-target"}
              expected: |
                {"alias": "als-v2v3-empty", "table": "als-target", "comment": "", "active": true}
            - name: als-v2v3-special
              create: |
                {"desc": "alias @#", "target": "als-compat-db.als-target"}
              expected: |
                {"alias": "als-v2v3-special", "table": "als-target", "comment": "alias @#", "active": true}
            """,
        )
        fun `V2 create - V3 get`(
            name: String,
            create: String,
            expected: String,
        ) {
            client
                .post()
                .uri("/graph/v2/service/$db/alias/$name")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(create)
                .exchange()
                .expectStatus()
                .isOk

            client
                .get()
                .uri("/graph/v3/databases/$db/aliases/$name")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: als-v3v2-basic
              create: |
                {"alias": "als-v3v2-basic", "table": "als-target", "comment": "test alias"}
              expected: |
                {"name": "als-compat-db.als-v3v2-basic", "target": "als-compat-db.als-target", "desc": "test alias", "active": true}
            - name: als-v3v2-empty
              create: |
                {"alias": "als-v3v2-empty", "table": "als-target", "comment": ""}
              expected: |
                {"name": "als-compat-db.als-v3v2-empty", "target": "als-compat-db.als-target", "desc": "", "active": true}
            - name: als-v3v2-special
              create: |
                {"alias": "als-v3v2-special", "table": "als-target", "comment": "alias @#"}
              expected: |
                {"name": "als-compat-db.als-v3v2-special", "target": "als-compat-db.als-target", "desc": "alias @#", "active": true}
            """,
        )
        fun `V3 create - V2 get`(
            name: String,
            create: String,
            expected: String,
        ) {
            client
                .post()
                .uri("/graph/v3/databases/$db/aliases")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(create)
                .exchange()
                .expectStatus()
                .isOk

            client
                .get()
                .uri("/graph/v2/service/$db/alias/$name")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected)
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class MultiEdgeCompatibilityTest {
        private val db = "me-compat-db"

        @BeforeAll
        fun setup() {
            client
                .post()
                .uri("/graph/v3/databases")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"database": "$db", "comment": "multiedge test db"}""")
                .exchange()
                .expectStatus()
                .isOk
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            # Basic MultiEdge - direction BOTH
            - name: me-v2v3-basic
              create: |
                {
                  "desc": "basic multiedge",
                  "type": "MULTI_EDGE",
                  "schema": {
                    "src": {"type": "LONG", "desc": "sender"},
                    "tgt": {"type": "LONG", "desc": "receiver"},
                    "fields": [
                      {"name": "_id", "type": "LONG", "nullable": false, "desc": "order id"}
                    ]
                  },
                  "dirType": "BOTH",
                  "storage": "datastore://test_namespace/me_v2v3_basic",
                  "readOnly": true
                }
              expected: |
                {
                  "type": "multiEdge",
                  "table": "me-v2v3-basic",
                  "comment": "basic multiedge",
                  "schema": {
                    "type": "multiEdge",
                    "id": {"type": "long", "comment": "order id"},
                    "source": {"type": "long", "comment": "sender"},
                    "target": {"type": "long", "comment": "receiver"},
                    "properties": [],
                    "direction": "BOTH"
                  },
                  "storage": "datastore://test_namespace/me_v2v3_basic",
                  "active": true
                }

            # MultiEdge with properties
            - name: me-v2v3-props
              create: |
                {
                  "desc": "multiedge with props",
                  "type": "MULTI_EDGE",
                  "schema": {
                    "src": {"type": "LONG", "desc": "user"},
                    "tgt": {"type": "LONG", "desc": "item"},
                    "fields": [
                      {"name": "_id", "type": "LONG", "nullable": false, "desc": "txn id"},
                      {"name": "amount", "type": "INT", "nullable": false, "desc": "purchase amount"},
                      {"name": "timestamp", "type": "LONG", "nullable": false, "desc": "txn time"}
                    ]
                  },
                  "dirType": "BOTH",
                  "storage": "datastore://test_namespace/me_v2v3_props",
                  "readOnly": true
                }
              expected: |
                {
                  "type": "multiEdge",
                  "table": "me-v2v3-props",
                  "comment": "multiedge with props",
                  "schema": {
                    "type": "multiEdge",
                    "id": {"type": "long", "comment": "txn id"},
                    "source": {"type": "long", "comment": "user"},
                    "target": {"type": "long", "comment": "item"},
                    "properties": [
                      {"name": "amount", "type": "int", "comment": "purchase amount", "nullable": false},
                      {"name": "timestamp", "type": "long", "comment": "txn time", "nullable": false}
                    ],
                    "direction": "BOTH"
                  },
                  "storage": "datastore://test_namespace/me_v2v3_props",
                  "active": true
                }

            # MultiEdge with STRING keys
            - name: me-v2v3-string
              create: |
                {
                  "desc": "string key multiedge",
                  "type": "MULTI_EDGE",
                  "schema": {
                    "src": {"type": "STRING", "desc": "from"},
                    "tgt": {"type": "STRING", "desc": "to"},
                    "fields": [
                      {"name": "_id", "type": "LONG", "nullable": false, "desc": "msg id"}
                    ]
                  },
                  "dirType": "OUT",
                  "storage": "datastore://test_namespace/me_v2v3_string",
                  "readOnly": true
                }
              expected: |
                {
                  "type": "multiEdge",
                  "table": "me-v2v3-string",
                  "comment": "string key multiedge",
                  "schema": {
                    "type": "multiEdge",
                    "id": {"type": "long", "comment": "msg id"},
                    "source": {"type": "string", "comment": "from"},
                    "target": {"type": "string", "comment": "to"},
                    "properties": [],
                    "direction": "OUT"
                  },
                  "storage": "datastore://test_namespace/me_v2v3_string",
                  "active": true
                }
            """,
        )
        fun `V2 create - V3 get`(
            name: String,
            create: String,
            expected: String,
        ) {
            client
                .post()
                .uri("/graph/v2/service/$db/label/$name")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(create)
                .exchange()
                .expectStatus()
                .isOk

            client
                .get()
                .uri("/graph/v3/databases/$db/tables/$name")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            # Basic MultiEdge - V3 create -> V2 get
            - name: me-v3v2-basic
              create: |
                {
                  "table": "me-v3v2-basic",
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
                  "storage": "datastore://test_namespace/me_v3v2_basic",
                  "mode": "SYNC",
                  "comment": "basic multiedge"
                }
              expected: |
                {
                  "name": "me-compat-db.me-v3v2-basic",
                  "desc": "basic multiedge",
                  "schema": {
                    "src": {"type": "LONG", "desc": "sender"},
                    "tgt": {"type": "LONG", "desc": "receiver"},
                    "fields": [
                      {"name": "_id", "type": "LONG", "nullable": false, "desc": "order id"}
                    ]
                  },
                  "dirType": "BOTH",
                  "storage": "datastore://test_namespace/me_v3v2_basic",
                  "active": true
                }

            # MultiEdge with properties - V3 create -> V2 get
            - name: me-v3v2-props
              create: |
                {
                  "table": "me-v3v2-props",
                  "schema": {
                    "type": "MULTI_EDGE",
                    "id": {"type": "long", "comment": "txn id"},
                    "source": {"type": "long", "comment": "user"},
                    "target": {"type": "long", "comment": "item"},
                    "properties": [
                      {"name": "amount", "type": "int", "comment": "purchase amount", "nullable": false},
                      {"name": "timestamp", "type": "long", "comment": "txn time", "nullable": false}
                    ],
                    "direction": "BOTH",
                    "indexes": [],
                    "groups": []
                  },
                  "storage": "datastore://test_namespace/me_v3v2_props",
                  "mode": "SYNC",
                  "comment": "multiedge with props"
                }
              expected: |
                {
                  "name": "me-compat-db.me-v3v2-props",
                  "desc": "multiedge with props",
                  "schema": {
                    "src": {"type": "LONG", "desc": "user"},
                    "tgt": {"type": "LONG", "desc": "item"},
                    "fields": [
                      {"name": "_id", "type": "LONG", "nullable": false, "desc": "txn id"},
                      {"name": "amount", "type": "INT", "nullable": false, "desc": "purchase amount"},
                      {"name": "timestamp", "type": "LONG", "nullable": false, "desc": "txn time"}
                    ]
                  },
                  "dirType": "BOTH",
                  "storage": "datastore://test_namespace/me_v3v2_props",
                  "active": true
                }

            # MultiEdge with STRING keys - V3 create -> V2 get
            - name: me-v3v2-string
              create: |
                {
                  "table": "me-v3v2-string",
                  "schema": {
                    "type": "MULTI_EDGE",
                    "id": {"type": "long", "comment": "msg id"},
                    "source": {"type": "string", "comment": "from"},
                    "target": {"type": "string", "comment": "to"},
                    "properties": [],
                    "direction": "OUT",
                    "indexes": [],
                    "groups": []
                  },
                  "storage": "datastore://test_namespace/me_v3v2_string",
                  "mode": "SYNC",
                  "comment": "string key multiedge"
                }
              expected: |
                {
                  "name": "me-compat-db.me-v3v2-string",
                  "desc": "string key multiedge",
                  "schema": {
                    "src": {"type": "STRING", "desc": "from"},
                    "tgt": {"type": "STRING", "desc": "to"},
                    "fields": [
                      {"name": "_id", "type": "LONG", "nullable": false, "desc": "msg id"}
                    ]
                  },
                  "dirType": "OUT",
                  "storage": "datastore://test_namespace/me_v3v2_string",
                  "active": true
                }
            """,
        )
        fun `V3 create - V2 get`(
            name: String,
            create: String,
            expected: String,
        ) {
            client
                .post()
                .uri("/graph/v3/databases/$db/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(create)
                .exchange()
                .expectStatus()
                .isOk

            client
                .get()
                .uri("/graph/v2/service/$db/label/$name")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .json(expected)
        }
    }
}
