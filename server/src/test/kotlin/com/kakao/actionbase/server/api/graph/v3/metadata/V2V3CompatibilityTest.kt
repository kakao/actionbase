package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.server.test.E2ETestBase
import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType

/**
 * The contract between the two metadata dialects.
 *
 * v3 is v2's structure with v3's names, so a v2 payload becomes a v3 payload by renaming keys and
 * nothing else. Each case writes through one dialect and reads back through the other.
 *
 * | v2                | v3                   |
 * |-------------------|----------------------|
 * | `name: "a.b"`     | `database` + `table` |
 * | `desc`            | `comment`            |
 * | `type: INDEXED`   | `type: EDGE`         |
 * | `IMMUTABLE_INDEXED` | `IMMUTABLE_EDGE`   |
 * | `schema.src/tgt`  | `schema.source/target` |
 * | `schema.fields`   | `schema.properties`  |
 * | `dirType`         | `direction`          |
 * | `indices[].name`  | `indexes[].index`    |
 * | `indices[].fields[].name` | `indexes[].fields[].field` |
 * | `mode: IGNORE`    | `mode: DROP`         |
 * | `event`/`readOnly`| derived by the server |
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V2V3CompatibilityTest : E2ETestBase() {
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class DatabaseCompatibilityTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - name: db_v2v3_basic
              create: |
                {"desc": "test database"}
              expected: |
                {"database": "db_v2v3_basic", "comment": "test database", "active": true}
            - name: db_v2v3_empty
              create: |
                {"desc": ""}
              expected: |
                {"database": "db_v2v3_empty", "comment": "", "active": true}
            - name: db_v2v3_special
              create: |
                {"desc": "test @#$%"}
              expected: |
                {"database": "db_v2v3_special", "comment": "test @#$%", "active": true}
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
            - name: db_v3v2_basic
              create: |
                {"database": "db_v3v2_basic", "comment": "test database"}
              expected: |
                {"name": "db_v3v2_basic", "desc": "test database", "active": true}
            - name: db_v3v2_empty
              create: |
                {"database": "db_v3v2_empty", "comment": ""}
              expected: |
                {"name": "db_v3v2_empty", "desc": "", "active": true}
            - name: db_v3v2_special
              create: |
                {"database": "db_v3v2_special", "comment": "test @#$%"}
              expected: |
                {"name": "db_v3v2_special", "desc": "test @#$%", "active": true}
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
        private val db = "tbl_compat_db"

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
            - name: tbl_v2v3_out
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
                  "active": true,
                  "database": "tbl_compat_db",
                  "table": "tbl_v2v3_out",
                  "comment": "direction out",
                  "type": "EDGE",
                  "schema": {
                    "source": {"type": "STRING", "comment": "source"},
                    "target": {"type": "STRING", "comment": "target"},
                    "properties": []
                  },
                  "direction": "OUT",
                  "storage": "datastore://test_namespace/tbl_v2v3_out"
                }

            # Direction: IN
            - name: tbl_v2v3_in
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
                  "active": true,
                  "database": "tbl_compat_db",
                  "table": "tbl_v2v3_in",
                  "comment": "direction in",
                  "type": "EDGE",
                  "schema": {
                    "source": {"type": "STRING", "comment": "source"},
                    "target": {"type": "STRING", "comment": "target"},
                    "properties": []
                  },
                  "direction": "IN",
                  "storage": "datastore://test_namespace/tbl_v2v3_in"
                }

            # Properties keep their v2 types verbatim
            - name: tbl_v2v3_props
              create: |
                {
                  "desc": "with props",
                  "type": "INDEXED",
                  "schema": {
                    "src": {"type": "STRING", "desc": "user"},
                    "tgt": {"type": "STRING", "desc": "item"},
                    "fields": [
                      {"name": "rating", "type": "INT", "nullable": true, "desc": "rating"},
                      {"name": "createdat", "type": "LONG", "nullable": false, "desc": "time"}
                    ]
                  },
                  "dirType": "OUT",
                  "storage": "datastore://test_namespace/tbl_v2v3_props"
                }
              expected: |
                {
                  "active": true,
                  "database": "tbl_compat_db",
                  "table": "tbl_v2v3_props",
                  "comment": "with props",
                  "type": "EDGE",
                  "schema": {
                    "source": {"type": "STRING", "comment": "user"},
                    "target": {"type": "STRING", "comment": "item"},
                    "properties": [
                      {"name": "rating", "type": "INT", "comment": "rating", "nullable": true},
                      {"name": "createdat", "type": "LONG", "comment": "time", "nullable": false}
                    ]
                  },
                  "direction": "OUT",
                  "storage": "datastore://test_namespace/tbl_v2v3_props"
                }

            # indices -> indexes, and their inner name -> index / field
            - name: tbl_v2v3_index
              create: |
                {
                  "desc": "with index",
                  "type": "INDEXED",
                  "schema": {
                    "src": {"type": "LONG", "desc": "uid"},
                    "tgt": {"type": "LONG", "desc": "iid"},
                    "fields": [
                      {"name": "createdat", "type": "LONG", "nullable": false, "desc": "time"}
                    ]
                  },
                  "dirType": "OUT",
                  "storage": "datastore://test_namespace/tbl_v2v3_index",
                  "indices": [
                    {
                      "name": "createdat_desc",
                      "fields": [{"name": "createdat", "order": "DESC"}],
                      "desc": "newest first"
                    }
                  ],
                  "mode": "IGNORE"
                }
              expected: |
                {
                  "active": true,
                  "database": "tbl_compat_db",
                  "table": "tbl_v2v3_index",
                  "comment": "with index",
                  "type": "EDGE",
                  "schema": {
                    "source": {"type": "LONG", "comment": "uid"},
                    "target": {"type": "LONG", "comment": "iid"},
                    "properties": [
                      {"name": "createdat", "type": "LONG", "comment": "time", "nullable": false}
                    ]
                  },
                  "direction": "OUT",
                  "storage": "datastore://test_namespace/tbl_v2v3_index",
                  "indexes": [
                    {
                      "index": "createdat_desc",
                      "fields": [{"field": "createdat", "order": "DESC"}],
                      "comment": "newest first"
                    }
                  ],
                  "mode": "DROP"
                }

            # IMMUTABLE_INDEXED -> IMMUTABLE_EDGE
            - name: tbl_v2v3_immutable
              create: |
                {
                  "desc": "append only",
                  "type": "IMMUTABLE_INDEXED",
                  "schema": {
                    "src": {"type": "LONG", "desc": "partition"},
                    "tgt": {"type": "STRING", "desc": "message id"},
                    "fields": [
                      {"name": "seq", "type": "LONG", "nullable": false, "desc": "sequence"}
                    ]
                  },
                  "dirType": "OUT",
                  "storage": "datastore://test_namespace/tbl_v2v3_immutable",
                  "indices": [
                    {"name": "seq_asc", "fields": [{"name": "seq", "order": "ASC"}], "desc": ""}
                  ]
                }
              expected: |
                {
                  "active": true,
                  "database": "tbl_compat_db",
                  "table": "tbl_v2v3_immutable",
                  "comment": "append only",
                  "type": "IMMUTABLE_EDGE",
                  "schema": {
                    "source": {"type": "LONG", "comment": "partition"},
                    "target": {"type": "STRING", "comment": "message id"},
                    "properties": [
                      {"name": "seq", "type": "LONG", "comment": "sequence", "nullable": false}
                    ]
                  },
                  "direction": "OUT",
                  "storage": "datastore://test_namespace/tbl_v2v3_immutable",
                  "indexes": [
                    {"index": "seq_asc", "fields": [{"field": "seq", "order": "ASC"}]}
                  ]
                }

            # VERTEX keeps the v2 layout: the id lives in src, tgt is a placeholder
            - name: tbl_v2v3_vertex
              create: |
                {
                  "desc": "vertex table",
                  "type": "VERTEX",
                  "schema": {
                    "src": {"type": "STRING", "desc": "user key"},
                    "tgt": {"type": "STRING", "desc": "<vertex>"},
                    "fields": [
                      {"name": "nickname", "type": "STRING", "nullable": true, "desc": "nickname"}
                    ]
                  },
                  "dirType": "OUT",
                  "storage": "datastore://test_namespace/tbl_v2v3_vertex"
                }
              expected: |
                {
                  "active": true,
                  "database": "tbl_compat_db",
                  "table": "tbl_v2v3_vertex",
                  "comment": "vertex table",
                  "type": "VERTEX",
                  "schema": {
                    "source": {"type": "STRING", "comment": "user key"},
                    "target": {"type": "STRING", "comment": "<vertex>"},
                    "properties": [
                      {"name": "nickname", "type": "STRING", "comment": "nickname", "nullable": true}
                    ]
                  },
                  "direction": "OUT",
                  "storage": "datastore://test_namespace/tbl_v2v3_vertex"
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
            - name: tbl_v3v2_out
              create: |
                {
                  "table": "tbl_v3v2_out",
                  "type": "EDGE",
                  "schema": {
                    "source": {"type": "STRING", "comment": "source"},
                    "target": {"type": "STRING", "comment": "target"},
                    "properties": []
                  },
                  "direction": "OUT",
                  "storage": "datastore://test_namespace/tbl_v3v2_out",
                  "mode": "SYNC",
                  "comment": "direction out"
                }
              expected: |
                {
                  "active": true,
                  "name": "tbl_compat_db.tbl_v3v2_out",
                  "desc": "direction out",
                  "type": "INDEXED",
                  "schema": {
                    "src": {"type": "STRING", "desc": "source"},
                    "tgt": {"type": "STRING", "desc": "target"},
                    "fields": []
                  },
                  "dirType": "OUT",
                  "storage": "datastore://test_namespace/tbl_v3v2_out",
                  "mode": "SYNC"
                }

            # Direction: IN
            - name: tbl_v3v2_in
              create: |
                {
                  "table": "tbl_v3v2_in",
                  "type": "EDGE",
                  "schema": {
                    "source": {"type": "STRING", "comment": "source"},
                    "target": {"type": "STRING", "comment": "target"},
                    "properties": []
                  },
                  "direction": "IN",
                  "storage": "datastore://test_namespace/tbl_v3v2_in",
                  "mode": "SYNC",
                  "comment": "direction in"
                }
              expected: |
                {
                  "active": true,
                  "name": "tbl_compat_db.tbl_v3v2_in",
                  "desc": "direction in",
                  "type": "INDEXED",
                  "schema": {
                    "src": {"type": "STRING", "desc": "source"},
                    "tgt": {"type": "STRING", "desc": "target"},
                    "fields": []
                  },
                  "dirType": "IN",
                  "storage": "datastore://test_namespace/tbl_v3v2_in",
                  "mode": "SYNC"
                }

            # Properties keep their v2 types verbatim
            - name: tbl_v3v2_props
              create: |
                {
                  "table": "tbl_v3v2_props",
                  "type": "EDGE",
                  "schema": {
                    "source": {"type": "STRING", "comment": "user"},
                    "target": {"type": "STRING", "comment": "item"},
                    "properties": [
                      {"name": "rating", "type": "INT", "comment": "rating", "nullable": true},
                      {"name": "createdat", "type": "LONG", "comment": "time", "nullable": false}
                    ]
                  },
                  "direction": "BOTH",
                  "storage": "datastore://test_namespace/tbl_v3v2_props",
                  "mode": "SYNC",
                  "comment": "with props"
                }
              expected: |
                {
                  "active": true,
                  "name": "tbl_compat_db.tbl_v3v2_props",
                  "desc": "with props",
                  "type": "INDEXED",
                  "schema": {
                    "src": {"type": "STRING", "desc": "user"},
                    "tgt": {"type": "STRING", "desc": "item"},
                    "fields": [
                      {"name": "rating", "type": "INT", "nullable": true, "desc": "rating"},
                      {"name": "createdat", "type": "LONG", "nullable": false, "desc": "time"}
                    ]
                  },
                  "dirType": "BOTH",
                  "storage": "datastore://test_namespace/tbl_v3v2_props",
                  "mode": "SYNC"
                }

            # indexes -> indices, DROP -> IGNORE
            - name: tbl_v3v2_index
              create: |
                {
                  "table": "tbl_v3v2_index",
                  "type": "EDGE",
                  "schema": {
                    "source": {"type": "LONG", "comment": "uid"},
                    "target": {"type": "LONG", "comment": "iid"},
                    "properties": [
                      {"name": "createdat", "type": "LONG", "comment": "time", "nullable": false}
                    ]
                  },
                  "direction": "OUT",
                  "storage": "datastore://test_namespace/tbl_v3v2_index",
                  "indexes": [
                    {
                      "index": "createdat_desc",
                      "fields": [{"field": "createdat", "order": "DESC"}],
                      "comment": "newest first"
                    }
                  ],
                  "mode": "DROP",
                  "comment": "with index"
                }
              expected: |
                {
                  "active": true,
                  "name": "tbl_compat_db.tbl_v3v2_index",
                  "desc": "with index",
                  "type": "INDEXED",
                  "schema": {
                    "src": {"type": "LONG", "desc": "uid"},
                    "tgt": {"type": "LONG", "desc": "iid"},
                    "fields": [
                      {"name": "createdat", "type": "LONG", "nullable": false, "desc": "time"}
                    ]
                  },
                  "dirType": "OUT",
                  "storage": "datastore://test_namespace/tbl_v3v2_index",
                  "indices": [
                    {
                      "name": "createdat_desc",
                      "fields": [{"name": "createdat", "order": "DESC"}],
                      "desc": "newest first"
                    }
                  ],
                  "mode": "IGNORE"
                }

            # IMMUTABLE_EDGE -> IMMUTABLE_INDEXED
            - name: tbl_v3v2_immutable
              create: |
                {
                  "table": "tbl_v3v2_immutable",
                  "type": "IMMUTABLE_EDGE",
                  "schema": {
                    "source": {"type": "LONG", "comment": "partition"},
                    "target": {"type": "STRING", "comment": "message id"},
                    "properties": [
                      {"name": "seq", "type": "LONG", "comment": "sequence", "nullable": false}
                    ]
                  },
                  "direction": "OUT",
                  "storage": "datastore://test_namespace/tbl_v3v2_immutable",
                  "indexes": [
                    {"index": "seq_asc", "fields": [{"field": "seq", "order": "ASC"}]}
                  ],
                  "mode": "SYNC",
                  "comment": "append only"
                }
              expected: |
                {
                  "active": true,
                  "name": "tbl_compat_db.tbl_v3v2_immutable",
                  "desc": "append only",
                  "type": "IMMUTABLE_INDEXED",
                  "schema": {
                    "src": {"type": "LONG", "desc": "partition"},
                    "tgt": {"type": "STRING", "desc": "message id"},
                    "fields": [
                      {"name": "seq", "type": "LONG", "nullable": false, "desc": "sequence"}
                    ]
                  },
                  "dirType": "OUT",
                  "storage": "datastore://test_namespace/tbl_v3v2_immutable",
                  "indices": [
                    {"name": "seq_asc", "fields": [{"name": "seq", "order": "ASC"}]}
                  ],
                  "mode": "SYNC"
                }

            # VERTEX keeps the v2 layout: the id lives in source, target is a placeholder
            - name: tbl_v3v2_vertex
              create: |
                {
                  "table": "tbl_v3v2_vertex",
                  "type": "VERTEX",
                  "schema": {
                    "source": {"type": "STRING", "comment": "user key"},
                    "target": {"type": "STRING", "comment": "<vertex>"},
                    "properties": [
                      {"name": "nickname", "type": "STRING", "comment": "nickname", "nullable": true}
                    ]
                  },
                  "direction": "OUT",
                  "storage": "datastore://test_namespace/tbl_v3v2_vertex",
                  "mode": "SYNC",
                  "comment": "vertex table"
                }
              expected: |
                {
                  "active": true,
                  "name": "tbl_compat_db.tbl_v3v2_vertex",
                  "desc": "vertex table",
                  "type": "VERTEX",
                  "schema": {
                    "src": {"type": "STRING", "desc": "user key"},
                    "tgt": {"type": "STRING", "desc": "<vertex>"},
                    "fields": [
                      {"name": "nickname", "type": "STRING", "nullable": true, "desc": "nickname"}
                    ]
                  },
                  "dirType": "OUT",
                  "storage": "datastore://test_namespace/tbl_v3v2_vertex",
                  "mode": "SYNC"
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
        private val db = "als_compat_db"
        private val table = "als_target"

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
                      "type": "EDGE",
                      "schema": {
                        "source": {"type": "STRING", "comment": "src"},
                        "target": {"type": "STRING", "comment": "tgt"},
                        "properties": []
                      },
                      "direction": "OUT",
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
            - name: als_v2v3_basic
              create: |
                {"desc": "test alias", "target": "als_compat_db.als_target"}
              expected: |
                {"alias": "als_v2v3_basic", "table": "als_target", "comment": "test alias", "active": true}
            - name: als_v2v3_empty
              create: |
                {"desc": "", "target": "als_compat_db.als_target"}
              expected: |
                {"alias": "als_v2v3_empty", "table": "als_target", "comment": "", "active": true}
            - name: als_v2v3_special
              create: |
                {"desc": "alias @#", "target": "als_compat_db.als_target"}
              expected: |
                {"alias": "als_v2v3_special", "table": "als_target", "comment": "alias @#", "active": true}
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
            - name: als_v3v2_basic
              create: |
                {"alias": "als_v3v2_basic", "table": "als_target", "comment": "test alias"}
              expected: |
                {"name": "als_compat_db.als_v3v2_basic", "target": "als_compat_db.als_target", "desc": "test alias", "active": true}
            - name: als_v3v2_empty
              create: |
                {"alias": "als_v3v2_empty", "table": "als_target", "comment": ""}
              expected: |
                {"name": "als_compat_db.als_v3v2_empty", "target": "als_compat_db.als_target", "desc": "", "active": true}
            - name: als_v3v2_special
              create: |
                {"alias": "als_v3v2_special", "table": "als_target", "comment": "alias @#"}
              expected: |
                {"name": "als_compat_db.als_v3v2_special", "target": "als_compat_db.als_target", "desc": "alias @#", "active": true}
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
        private val db = "me_compat_db"

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
            # The id stays where v2 puts it: the `_id` property
            - name: me_v2v3_basic
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
                  "active": true,
                  "database": "me_compat_db",
                  "table": "me_v2v3_basic",
                  "comment": "basic multiedge",
                  "type": "MULTI_EDGE",
                  "schema": {
                    "source": {"type": "LONG", "comment": "sender"},
                    "target": {"type": "LONG", "comment": "receiver"},
                    "properties": [
                      {"name": "_id", "type": "LONG", "comment": "order id", "nullable": false}
                    ]
                  },
                  "direction": "BOTH",
                  "storage": "datastore://test_namespace/me_v2v3_basic"
                }

            # MultiEdge with properties
            - name: me_v2v3_props
              create: |
                {
                  "desc": "multiedge with props",
                  "type": "MULTI_EDGE",
                  "schema": {
                    "src": {"type": "LONG", "desc": "user"},
                    "tgt": {"type": "LONG", "desc": "item"},
                    "fields": [
                      {"name": "_id", "type": "LONG", "nullable": false, "desc": "txn id"},
                      {"name": "amount", "type": "INT", "nullable": false, "desc": "purchase amount"}
                    ]
                  },
                  "dirType": "BOTH",
                  "storage": "datastore://test_namespace/me_v2v3_props",
                  "readOnly": true
                }
              expected: |
                {
                  "active": true,
                  "database": "me_compat_db",
                  "table": "me_v2v3_props",
                  "comment": "multiedge with props",
                  "type": "MULTI_EDGE",
                  "schema": {
                    "source": {"type": "LONG", "comment": "user"},
                    "target": {"type": "LONG", "comment": "item"},
                    "properties": [
                      {"name": "_id", "type": "LONG", "comment": "txn id", "nullable": false},
                      {"name": "amount", "type": "INT", "comment": "purchase amount", "nullable": false}
                    ]
                  },
                  "direction": "BOTH",
                  "storage": "datastore://test_namespace/me_v2v3_props"
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
            # The server derives readOnly=true for a multi-edge table, as v2 requires
            - name: me_v3v2_basic
              create: |
                {
                  "table": "me_v3v2_basic",
                  "type": "MULTI_EDGE",
                  "schema": {
                    "source": {"type": "LONG", "comment": "sender"},
                    "target": {"type": "LONG", "comment": "receiver"},
                    "properties": [
                      {"name": "_id", "type": "LONG", "comment": "order id", "nullable": false}
                    ]
                  },
                  "direction": "BOTH",
                  "storage": "datastore://test_namespace/me_v3v2_basic",
                  "mode": "SYNC",
                  "comment": "basic multiedge"
                }
              expected: |
                {
                  "active": true,
                  "name": "me_compat_db.me_v3v2_basic",
                  "desc": "basic multiedge",
                  "type": "MULTI_EDGE",
                  "readOnly": true,
                  "event": false,
                  "schema": {
                    "src": {"type": "LONG", "desc": "sender"},
                    "tgt": {"type": "LONG", "desc": "receiver"},
                    "fields": [
                      {"name": "_id", "type": "LONG", "nullable": false, "desc": "order id"}
                    ]
                  },
                  "dirType": "BOTH",
                  "storage": "datastore://test_namespace/me_v3v2_basic"
                }

            # MultiEdge with properties
            - name: me_v3v2_props
              create: |
                {
                  "table": "me_v3v2_props",
                  "type": "MULTI_EDGE",
                  "schema": {
                    "source": {"type": "LONG", "comment": "user"},
                    "target": {"type": "LONG", "comment": "item"},
                    "properties": [
                      {"name": "_id", "type": "LONG", "comment": "txn id", "nullable": false},
                      {"name": "amount", "type": "INT", "comment": "purchase amount", "nullable": false}
                    ]
                  },
                  "direction": "BOTH",
                  "storage": "datastore://test_namespace/me_v3v2_props",
                  "mode": "SYNC",
                  "comment": "multiedge with props"
                }
              expected: |
                {
                  "active": true,
                  "name": "me_compat_db.me_v3v2_props",
                  "desc": "multiedge with props",
                  "type": "MULTI_EDGE",
                  "readOnly": true,
                  "schema": {
                    "src": {"type": "LONG", "desc": "user"},
                    "tgt": {"type": "LONG", "desc": "item"},
                    "fields": [
                      {"name": "_id", "type": "LONG", "nullable": false, "desc": "txn id"},
                      {"name": "amount", "type": "INT", "nullable": false, "desc": "purchase amount"}
                    ]
                  },
                  "dirType": "BOTH",
                  "storage": "datastore://test_namespace/me_v3v2_props"
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
