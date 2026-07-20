package com.kakao.actionbase.server.api.queue.v1

import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType

/**
 * Queue admin lifecycle: create builds an immutable edge table stamped with queue metadata, get
 * reads it back, delete removes it, and a reserved `orderBy` name is rejected with 400.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueueAdminE2ETest : E2ETestBase() {
    private val db = "queue_admin_db"

    private fun createDb() {
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$db", "comment": "queue admin e2e"}""")
            .exchange()
    }

    private fun createQueueBody(
        queue: String,
        orderBy: String = "seq",
    ) = """
        {
          "queue": "$queue",
          "storage": "datastore://queue_admin_ns/$queue",
          "partitionCount": 12,
          "orderBy": "$orderBy",
          "properties": [{"name": "payload", "type": "string", "nullable": true, "comment": "payload"}],
          "comment": "admin lifecycle"
        }
        """.trimIndent()

    @Test
    fun `create then get then delete a queue`() {
        createDb()
        val queue = "orders"

        client
            .post()
            .uri("/queue/v1/databases/$db/queues")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createQueueBody(queue))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.queue")
            .isEqualTo(queue)
            .jsonPath("$.partitionCount")
            .isEqualTo(12)
            .jsonPath("$.orderBy")
            .isEqualTo("seq")

        client
            .get()
            .uri("/queue/v1/databases/$db/queues/$queue")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.partitionCount")
            .isEqualTo(12)

        client
            .delete()
            .uri("/queue/v1/databases/$db/queues/$queue")
            .exchange()
            .expectStatus()
            .isNoContent
    }

    @Test
    fun `reserved orderBy name is rejected`() {
        createDb()
        client
            .post()
            .uri("/queue/v1/databases/$db/queues")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createQueueBody("bad_queue", orderBy = "ts"))
            .exchange()
            .expectStatus()
            .isBadRequest
    }
}
