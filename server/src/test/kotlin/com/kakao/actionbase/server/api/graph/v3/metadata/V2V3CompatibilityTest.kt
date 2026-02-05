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
        fun `V2 create - V3 get`(name: String, v2: String, v3: String) {
            client.post().uri("/graph/v2/service/$name")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(v2)
                .exchange().expectStatus().isOk

            client.get().uri("/graph/v3/databases/$name")
                .exchange().expectStatus().isOk
                .expectBody().json(v3)
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
        fun `V3 create - V2 get`(name: String, v3: String, v2: String) {
            client.post().uri("/graph/v3/databases/$name")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(v3)
                .exchange().expectStatus().isOk

            client.get().uri("/graph/v2/service/$name")
                .exchange().expectStatus().isOk
                .expectBody().json(v2)
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class TableCompatibilityTest {
        private val db = "tbl-compat-db"

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
            # Basic edge table (V2 HASH only supports OUT)
            - name: tbl-v2v3-basic
              v2: |
                {
                  "desc": "basic edge",
                  "type": "HASH",
                  "schema": {
                    "src": {"type": "STRING", "desc": "source"},
                    "tgt": {"type": "STRING", "desc": "target"},
                    "fields": []
                  },
                  "dirType": "OUT",
                  "storage": "datastore://hbase/tbl-v2v3-basic"
                }
              v3: |
                {
                  "table": "tbl-v2v3-basic",
                  "comment": "basic edge",
                  "schema": {
                    "type": "edge",
                    "source": {"type": "string", "comment": "source"},
                    "target": {"type": "string", "comment": "target"},
                    "properties": [],
                    "direction": "OUT"
                  },
                  "storage": {"type": "hbase", "tableName": "tbl-v2v3-basic"},
                  "active": true
                }

            # Edge with INT property
            - name: tbl-v2v3-int
              v2: |
                {
                  "desc": "with int",
                  "type": "HASH",
                  "schema": {
                    "src": {"type": "STRING", "desc": "src"},
                    "tgt": {"type": "STRING", "desc": "tgt"},
                    "fields": [{"name": "score", "type": "INT", "nullable": true, "desc": "score"}]
                  },
                  "dirType": "OUT",
                  "storage": "datastore://hbase/tbl-v2v3-int"
                }
              v3: |
                {
                  "table": "tbl-v2v3-int",
                  "comment": "with int",
                  "schema": {
                    "type": "edge",
                    "source": {"type": "string", "comment": "src"},
                    "target": {"type": "string", "comment": "tgt"},
                    "properties": [{"name": "score", "type": "int", "comment": "score", "nullable": true}],
                    "direction": "OUT"
                  },
                  "storage": {"type": "hbase", "tableName": "tbl-v2v3-int"},
                  "active": true
                }

            # Edge with multiple properties
            - name: tbl-v2v3-multi
              v2: |
                {
                  "desc": "multi props",
                  "type": "HASH",
                  "schema": {
                    "src": {"type": "STRING", "desc": "user"},
                    "tgt": {"type": "STRING", "desc": "item"},
                    "fields": [
                      {"name": "rating", "type": "INT", "nullable": true, "desc": "rating"},
                      {"name": "createdat", "type": "LONG", "nullable": true, "desc": "time"}
                    ]
                  },
                  "dirType": "OUT",
                  "storage": "datastore://hbase/tbl-v2v3-multi"
                }
              v3: |
                {
                  "table": "tbl-v2v3-multi",
                  "comment": "multi props",
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
                  "storage": {"type": "hbase", "tableName": "tbl-v2v3-multi"},
                  "active": true
                }

            # Edge with LONG keys
            - name: tbl-v2v3-long
              v2: |
                {
                  "desc": "long keys",
                  "type": "HASH",
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
        fun `V2 create - V3 get`(name: String, v2: String, v3: String) {
            client.post().uri("/graph/v2/service/$db/label/$name")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(v2)
                .exchange().expectStatus().isOk

            client.get().uri("/graph/v3/databases/$db/tables/$name")
                .exchange().expectStatus().isOk
                .expectBody().json(v3)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            # Basic edge table
            - name: tbl-v3v2-basic
              v3: |
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
                  "storage": "datastore://hbase/tbl-v3v2-basic",
                  "mode": "SYNC",
                  "comment": "basic edge"
                }
              v2: |
                {
                  "name": "tbl-compat-db.tbl-v3v2-basic",
                  "desc": "basic edge",
                  "schema": {
                    "src": {"type": "STRING", "desc": "source"},
                    "tgt": {"type": "STRING", "desc": "target"},
                    "fields": []
                  },
                  "dirType": "OUT",
                  "storage": "datastore://hbase/tbl-v3v2-basic",
                  "active": true
                }

            # Edge with properties
            - name: tbl-v3v2-props
              v3: |
                {
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

            # Edge with LONG keys
            - name: tbl-v3v2-long
              v3: |
                {
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
        fun `V3 create - V2 get`(name: String, v3: String, v2: String) {
            client.post().uri("/graph/v3/databases/$db/tables/$name")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(v3)
                .exchange().expectStatus().isOk

            client.get().uri("/graph/v2/service/$db/label/$name")
                .exchange().expectStatus().isOk
                .expectBody().json(v2)
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class AliasCompatibilityTest {
        private val db = "als-compat-db"
        private val table = "als-target"

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
                      "storage": "datastore://hbase/als-target-storage",
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
        fun `V2 create - V3 get`(name: String, v2: String, v3: String) {
            client.post().uri("/graph/v2/service/$db/alias/$name")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(v2)
                .exchange().expectStatus().isOk

            client.get().uri("/graph/v3/databases/$db/aliases/$name")
                .exchange().expectStatus().isOk
                .expectBody().json(v3)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: als-v3v2-basic
              v3: |
                {"table": "als-target", "comment": "test alias"}
              v2: |
                {"name": "als-compat-db.als-v3v2-basic", "target": "als-compat-db.als-target", "desc": "test alias", "active": true}
            - name: als-v3v2-empty
              v3: |
                {"table": "als-target", "comment": ""}
              v2: |
                {"name": "als-compat-db.als-v3v2-empty", "target": "als-compat-db.als-target", "desc": "", "active": true}
            - name: als-v3v2-special
              v3: |
                {"table": "als-target", "comment": "alias @#"}
              v2: |
                {"name": "als-compat-db.als-v3v2-special", "target": "als-compat-db.als-target", "desc": "alias @#", "active": true}
            """,
        )
        fun `V3 create - V2 get`(name: String, v3: String, v2: String) {
            client.post().uri("/graph/v3/databases/$db/aliases/$name")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(v3)
                .exchange().expectStatus().isOk

            client.get().uri("/graph/v2/service/$db/alias/$name")
                .exchange().expectStatus().isOk
                .expectBody().json(v2)
        }
    }
}
