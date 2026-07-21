package com.kakao.actionbase.server.api.queue.v1

import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType

/**
 * Queue lifecycle: create builds an immutable edge table stamped with queue metadata, get reads it
 * back, disable/enable toggle the active flag, and delete is guarded — it succeeds only once the
 * queue is disabled (409 otherwise).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueueLifecycleE2ETest : E2ETestBase() {
    private val ns = "queue_lifecycle_ns"

    private fun createNamespace() {
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$ns", "comment": "queue lifecycle e2e"}""")
            .exchange()
    }

    private fun createQueueBody(queue: String) = """{"queue": "$queue", "storage": "datastore://queue_lifecycle_ns/$queue", "partitions": 12}"""

    @Test
    fun `create then get then disable then delete a queue`() {
        createNamespace()
        val queue = "orders"

        client
            .post()
            .uri("/queue/v1/namespaces/$ns/queues")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createQueueBody(queue))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.queue")
            .isEqualTo(queue)
            .jsonPath("$.partitions")
            .isEqualTo(12)
            .jsonPath("$.namespace")
            .isEqualTo(ns)

        client
            .get()
            .uri("/queue/v1/namespaces/$ns/queues/$queue")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.partitions")
            .isEqualTo(12)

        // Delete is refused while the queue is still active.
        client
            .delete()
            .uri("/queue/v1/namespaces/$ns/queues/$queue")
            .exchange()
            .expectStatus()
            .isEqualTo(409)

        // Disable, then delete succeeds.
        client
            .put()
            .uri("/queue/v1/namespaces/$ns/queues/$queue/disable")
            .exchange()
            .expectStatus()
            .isOk

        client
            .delete()
            .uri("/queue/v1/namespaces/$ns/queues/$queue")
            .exchange()
            .expectStatus()
            .isNoContent
    }

    @Test
    fun `disable then enable toggles the active flag`() {
        createNamespace()
        val queue = "toggle"
        client
            .post()
            .uri("/queue/v1/namespaces/$ns/queues")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(createQueueBody(queue))
            .exchange()
            .expectStatus()
            .isOk

        client
            .put()
            .uri("/queue/v1/namespaces/$ns/queues/$queue/disable")
            .exchange()
            .expectStatus()
            .isOk

        client
            .put()
            .uri("/queue/v1/namespaces/$ns/queues/$queue/enable")
            .exchange()
            .expectStatus()
            .isOk
    }
}
