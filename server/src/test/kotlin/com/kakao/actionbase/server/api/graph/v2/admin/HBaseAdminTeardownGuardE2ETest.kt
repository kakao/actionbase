package com.kakao.actionbase.server.api.graph.v2.admin

import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

/**
 * The deprecated devtools endpoints reach the same tables as `/graph/v3/datastore/hbase`, so they
 * have to refuse a teardown for the same reason. This runs on the default context: the guard
 * answers from metadata and rejects before `HBaseAdminService` is involved, so no cluster is needed.
 */
class HBaseAdminTeardownGuardE2ETest : E2ETestBase() {
    private val cluster = "any_cluster"
    private val htable = "v2_ns:v2_bound"

    private fun bindTable() {
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "v2_guard_db", "comment": "v2 guard e2e"}""")
            .exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/graph/v3/databases/v2_guard_db/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "v2_guard_tbl",
                  "storage": "datastore://v2_ns/v2_bound",
                  "mode": "SYNC",
                  "comment": "v2 guard e2e",
                  "schema": {
                    "type": "EDGE",
                    "direction": "OUT",
                    "source": {"type": "long", "comment": "src"},
                    "target": {"type": "long", "comment": "tgt"},
                    "properties": [],
                    "indexes": [],
                    "groups": []
                  }
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }

    @Test
    fun `refuses to delete a table a URI-bound label references`() {
        bindTable()

        client
            .delete()
            .uri("/graph/v2/admin/hbase/cluster/$cluster/table/$htable")
            .exchange()
            .expectStatus()
            .isBadRequest
            .expectBody()
            .jsonPath("$.message")
            .value<String> { message -> assert(message.contains("is used by")) { message } }
    }

    @Test
    fun `refuses to disable a table a URI-bound label references`() {
        bindTable()

        client
            .put()
            .uri("/graph/v2/admin/hbase/cluster/$cluster/table/$htable")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"enable": false}""")
            .exchange()
            .expectStatus()
            .isBadRequest
            .expectBody()
            .jsonPath("$.message")
            .value<String> { message -> assert(message.contains("is used by")) { message } }
    }

    @Test
    fun `rejects a table name that is not namespace qualified`() {
        client
            .delete()
            .uri("/graph/v2/admin/hbase/cluster/$cluster/table/unqualified")
            .exchange()
            .expectStatus()
            .isBadRequest
    }
}
