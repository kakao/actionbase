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
 * - V2 storage (string) = V3 storage (object)
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
              v2: |
                {"desc": "test database"}
              v3: |
                {"database": "db-v2v3-basic", "comment": "test database", "active": true}
            - name: db-v2v3-empty
              v2: |
                {"desc": ""}
              v3: |
                {"database": "db-v2v3-empty", "comment": "", "active": true}
            - name: db-v2v3-special
              v2: |
                {"desc": "test @#$%"}
              v3: |
                {"database": "db-v2v3-special", "comment": "test @#$%", "active": true}
            """,
        )
        fun `V2 create - V3 get`(
            name: String,
            v2: String,
            v3: String,
        ) {
            client
                .post()
                .uri("/graph/v2/service/$name")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(v2)
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
                .json(v3)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: db-v3v2-basic
              v3: |
                {"database": "db-v3v2-basic", "comment": "test database"}
              v2: |
                {"name": "db-v3v2-basic", "desc": "test database", "active": true}
            - name: db-v3v2-empty
              v3: |
                {"database": "db-v3v2-empty", "comment": ""}
              v2: |
                {"name": "db-v3v2-empty", "desc": "", "active": true}
            - name: db-v3v2-special
              v3: |
                {"database": "db-v3v2-special", "comment": "test @#$%"}
              v2: |
                {"name": "db-v3v2-special", "desc": "test @#$%", "active": true}
            """,
        )
        fun `V3 create - V2 get`(
            name: String,
            v3: String,
            v2: String,
        ) {
            client
                .post()
                .uri("/graph/v3/databases")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(v3)
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
                .json(v2)
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
              v2: |
                {
                  "desc": "direction out",
                  "type": "INDEXED",
                  "schema": {
                    "src": {"type": "STRING", "desc": "source"},
                    "tgt": {"type": "STRING", "desc": "target"},
                    "fields": []
                  },
                  "dirType": "OUT",
                  "storage": "datastore://hbase/tbl-v2v3-out"
                }
              v3: |
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
                  "storage": {"type": "hbase", "tableName": "tbl-v2v3-out"},
                  "active": true
                }

            # Direction: IN
            - name: tbl-v2v3-in
              v2: |
                {
                  "desc": "direction in",
                  "type": "INDEXED",
                  "schema": {
                    "src": {"type": "STRING", "desc": "source"},
                    "tgt": {"type": "STRING", "desc": "target"},
                    "fields": []
                  },
                  "dirType": "IN",
                  "storage": "datastore://hbase/tbl-v2v3-in"
                }
              v3: |
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
                  "storage": {"type": "hbase", "tableName": "tbl-v2v3-in"},
                  "active": true
                }

            # Direction: BOTH
            - name: tbl-v2v3-both
              v2: |
                {
                  "desc": "direction both",
                  "type": "INDEXED",
                  "schema": {
                    "src": {"type": "STRING", "desc": "source"},
                    "tgt": {"type": "STRING", "desc": "target"},
                    "fields": []
                  },
                  "dirType": "BOTH",
                  "storage": "datastore://hbase/tbl-v2v3-both"
                }
              v3: |
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
                  "storage": {"type": "hbase", "tableName": "tbl-v2v3-both"},
                  "active": true
                }

            # With properties
            - name: tbl-v2v3-props
              v2: |
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
                  "storage": "datastore://hbase/tbl-v2v3-props"
                }
              v3: |
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
                  "storage": {"type": "hbase", "tableName": "tbl-v2v3-props"},
                  "active": true
                }

            # LONG keys
            - name: tbl-v2v3-long
              v2: |
                {
                  "desc": "long keys",
                  "type": "INDEXED",
                  "schema": {
                    "src": {"type": "LONG", "desc": "uid"},
                    "tgt": {"type": "LONG", "desc": "iid"},
                    "fields": []
                  },
                  "dirType": "OUT",
                  "storage": "datastore://hbase/tbl-v2v3-long"
                }
              v3: |
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
                  "storage": {"type": "hbase", "tableName": "tbl-v2v3-long"},
                  "active": true
                }
            """,
        )
        fun `V2 create - V3 get`(
            name: String,
            v2: String,
            v3: String,
        ) {
            client
                .post()
                .uri("/graph/v2/service/$db/label/$name")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(v2)
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
                .json(v3)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            # Direction: OUT
            - name: tbl-v3v2-out
              v3: |
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
                  "storage": "datastore://hbase/tbl-v3v2-out",
                  "mode": "SYNC",
                  "comment": "direction out"
                }
              v2: |
                {
                  "name": "tbl-compat-db.tbl-v3v2-out",
                  "desc": "direction out",
                  "schema": {
                    "src": {"type": "STRING", "desc": "source"},
                    "tgt": {"type": "STRING", "desc": "target"},
                    "fields": []
                  },
                  "dirType": "OUT",
                  "storage": "datastore://hbase/tbl-v3v2-out",
                  "active": true
                }

            # Direction: IN
            - name: tbl-v3v2-in
              v3: |
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
                  "storage": "datastore://hbase/tbl-v3v2-in",
                  "mode": "SYNC",
                  "comment": "direction in"
                }
              v2: |
                {
                  "name": "tbl-compat-db.tbl-v3v2-in",
                  "desc": "direction in",
                  "schema": {
                    "src": {"type": "STRING", "desc": "source"},
                    "tgt": {"type": "STRING", "desc": "target"},
                    "fields": []
                  },
                  "dirType": "IN",
                  "storage": "datastore://hbase/tbl-v3v2-in",
                  "active": true
                }

            # Direction: BOTH
            - name: tbl-v3v2-both
              v3: |
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
                  "storage": "datastore://hbase/tbl-v3v2-both",
                  "mode": "SYNC",
                  "comment": "direction both"
                }
              v2: |
                {
                  "name": "tbl-compat-db.tbl-v3v2-both",
                  "desc": "direction both",
                  "schema": {
                    "src": {"type": "STRING", "desc": "source"},
                    "tgt": {"type": "STRING", "desc": "target"},
                    "fields": []
                  },
                  "dirType": "BOTH",
                  "storage": "datastore://hbase/tbl-v3v2-both",
                  "active": true
                }

            # With properties
            - name: tbl-v3v2-props
              v3: |
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
                  "storage": "datastore://hbase/tbl-v3v2-props",
                  "mode": "SYNC",
                  "comment": "with props"
                }
              v2: |
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
                  "storage": "datastore://hbase/tbl-v3v2-props",
                  "active": true
                }

            # LONG keys
            - name: tbl-v3v2-long
              v3: |
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
                  "storage": "datastore://hbase/tbl-v3v2-long",
                  "mode": "SYNC",
                  "comment": "long keys"
                }
              v2: |
                {
                  "name": "tbl-compat-db.tbl-v3v2-long",
                  "desc": "long keys",
                  "schema": {
                    "src": {"type": "LONG", "desc": "uid"},
                    "tgt": {"type": "LONG", "desc": "iid"},
                    "fields": []
                  },
                  "dirType": "OUT",
                  "storage": "datastore://hbase/tbl-v3v2-long",
                  "active": true
                }
            """,
        )
        fun `V3 create - V2 get`(
            name: String,
            v3: String,
            v2: String,
        ) {
            client
                .post()
                .uri("/graph/v3/databases/$db/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(v3)
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
                .json(v2)
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
                      "storage": "datastore://hbase/als-target-storage",
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
              v2: |
                {"desc": "test alias", "target": "als-compat-db.als-target"}
              v3: |
                {"alias": "als-v2v3-basic", "table": "als-target", "comment": "test alias", "active": true}
            - name: als-v2v3-empty
              v2: |
                {"desc": "", "target": "als-compat-db.als-target"}
              v3: |
                {"alias": "als-v2v3-empty", "table": "als-target", "comment": "", "active": true}
            - name: als-v2v3-special
              v2: |
                {"desc": "alias @#", "target": "als-compat-db.als-target"}
              v3: |
                {"alias": "als-v2v3-special", "table": "als-target", "comment": "alias @#", "active": true}
            """,
        )
        fun `V2 create - V3 get`(
            name: String,
            v2: String,
            v3: String,
        ) {
            client
                .post()
                .uri("/graph/v2/service/$db/alias/$name")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(v2)
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
                .json(v3)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: als-v3v2-basic
              v3: |
                {"alias": "als-v3v2-basic", "table": "als-target", "comment": "test alias"}
              v2: |
                {"name": "als-compat-db.als-v3v2-basic", "target": "als-compat-db.als-target", "desc": "test alias", "active": true}
            - name: als-v3v2-empty
              v3: |
                {"alias": "als-v3v2-empty", "table": "als-target", "comment": ""}
              v2: |
                {"name": "als-compat-db.als-v3v2-empty", "target": "als-compat-db.als-target", "desc": "", "active": true}
            - name: als-v3v2-special
              v3: |
                {"alias": "als-v3v2-special", "table": "als-target", "comment": "alias @#"}
              v2: |
                {"name": "als-compat-db.als-v3v2-special", "target": "als-compat-db.als-target", "desc": "alias @#", "active": true}
            """,
        )
        fun `V3 create - V2 get`(
            name: String,
            v3: String,
            v2: String,
        ) {
            client
                .post()
                .uri("/graph/v3/databases/$db/aliases")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(v3)
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
                .json(v2)
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
              v2: |
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
                  "storage": "datastore://hbase/me-v2v3-basic",
                  "readOnly": true
                }
              v3: |
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
                  "storage": {"type": "hbase", "tableName": "me-v2v3-basic"},
                  "active": true
                }

            # MultiEdge with properties
            - name: me-v2v3-props
              v2: |
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
                  "storage": "datastore://hbase/me-v2v3-props",
                  "readOnly": true
                }
              v3: |
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
                  "storage": {"type": "hbase", "tableName": "me-v2v3-props"},
                  "active": true
                }

            # MultiEdge with STRING keys
            - name: me-v2v3-string
              v2: |
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
                  "storage": "datastore://hbase/me-v2v3-string",
                  "readOnly": true
                }
              v3: |
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
                  "storage": {"type": "hbase", "tableName": "me-v2v3-string"},
                  "active": true
                }
            """,
        )
        fun `V2 create - V3 get`(
            name: String,
            v2: String,
            v3: String,
        ) {
            client
                .post()
                .uri("/graph/v2/service/$db/label/$name")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(v2)
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
                .json(v3)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            # Basic MultiEdge - V3 create -> V2 get
            - name: me-v3v2-basic
              v3: |
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
                  "storage": "datastore://hbase/me-v3v2-basic",
                  "mode": "SYNC",
                  "comment": "basic multiedge"
                }
              v2: |
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
                  "storage": "datastore://hbase/me-v3v2-basic",
                  "active": true
                }

            # MultiEdge with properties - V3 create -> V2 get
            - name: me-v3v2-props
              v3: |
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
                  "storage": "datastore://hbase/me-v3v2-props",
                  "mode": "SYNC",
                  "comment": "multiedge with props"
                }
              v2: |
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
                  "storage": "datastore://hbase/me-v3v2-props",
                  "active": true
                }

            # MultiEdge with STRING keys - V3 create -> V2 get
            - name: me-v3v2-string
              v3: |
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
                  "storage": "datastore://hbase/me-v3v2-string",
                  "mode": "SYNC",
                  "comment": "string key multiedge"
                }
              v2: |
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
                  "storage": "datastore://hbase/me-v3v2-string",
                  "active": true
                }
            """,
        )
        fun `V3 create - V2 get`(
            name: String,
            v3: String,
            v2: String,
        ) {
            client
                .post()
                .uri("/graph/v3/databases/$db/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(v3)
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
                .json(v2)
        }
    }
}
