package com.kakao.actionbase.server.api.graph.v3.datastore.hbase

import com.kakao.actionbase.server.configuration.HttpHeaderConstants

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

import io.mockk.every
import reactor.core.publisher.Mono

/** The context and its metastore are shared across tests, so each one uses its own htable. */
class DatastoreTableReferencesE2ETest : HBaseDatastoreE2ETestBase() {
    /** Creates a v3 table bound to `$TEST_NAMESPACE:{suffix}` and returns the htable name. */
    private fun bindTable(suffix: String): String {
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "db_$suffix", "comment": "references e2e"}""")
            .exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/graph/v3/databases/db_$suffix/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "tbl_$suffix",
                  "storage": "datastore://$TEST_NAMESPACE/$suffix",
                  "mode": "SYNC",
                  "comment": "references e2e",
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

        return "$TEST_NAMESPACE:$suffix"
    }

    private fun deactivate(suffix: String) {
        client
            .put()
            .uri("/graph/v3/databases/db_$suffix/tables/tbl_$suffix")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"active": false}""")
            .exchange()
            .expectStatus()
            .isOk
    }

    @Test
    fun `reports the table bound by a datastore URI`() {
        val htable = bindTable("bound")

        client
            .get()
            .uri("/graph/v3/datastore/hbase/tables/$htable/references")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.references.length()")
            .isEqualTo(1)
            .jsonPath("$.references[0].kind")
            .isEqualTo("LABEL")
            .jsonPath("$.references[0].active")
            .isEqualTo(true)
    }

    @Test
    fun `reports no references for a table nothing is bound to`() {
        client
            .get()
            .uri("/graph/v3/datastore/hbase/tables/$TEST_NAMESPACE:unbound/references")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.references.length()")
            .isEqualTo(0)
    }

    @Test
    fun `refuses to drop a table an active table is bound to`() {
        val htable = bindTable("undroppable")
        // Subscribing means the guard let the drop through. The call itself is built either way.
        every { hBaseAdmin.deleteTable(any(), any()) } returns
            Mono.error(AssertionError("drop reached HBase despite an active binding"))

        client
            .delete()
            .uri("/graph/v3/datastore/hbase/tables/$htable")
            .header(HttpHeaderConstants.ACTOR_ROLE, "ADMIN")
            .exchange()
            .expectStatus()
            .isBadRequest
            .expectBody()
            .jsonPath("$.message")
            .value<String> { message -> assert(message.contains("is used by")) { message } }
    }

    @Test
    fun `reports every binding on the cluster from one scan`() {
        val htable = bindTable("bulk")

        client
            .get()
            .uri("/graph/v3/datastore/hbase/references")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.references['$htable'].length()")
            .isEqualTo(1)
            .jsonPath("$.references['$htable'][0].kind")
            .isEqualTo("LABEL")
    }

    @Test
    fun `narrows the cluster listing to one namespace`() {
        val htable = bindTable("filtered")

        client
            .get()
            .uri("/graph/v3/datastore/hbase/references?namespace=$TEST_NAMESPACE")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.references['$htable']")
            .exists()

        client
            .get()
            .uri("/graph/v3/datastore/hbase/references?namespace=somewhere_else")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.references.length()")
            .isEqualTo(0)
    }

    @Test
    fun `keeps reporting the binding after it is deactivated`() {
        val htable = bindTable("deactivated")
        deactivate("deactivated")

        client
            .get()
            .uri("/graph/v3/datastore/hbase/tables/$htable/references")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.references.length()")
            .isEqualTo(1)
            .jsonPath("$.references[0].active")
            .isEqualTo(false)
    }
}
