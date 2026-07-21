package com.kakao.actionbase.server.api.queue.v1

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType

/**
 * Enqueue E2E: appending `{ key, seq, value }` messages routes each to a partition, assigns a ULID
 * `id`, and reports a CREATED status.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueueEnqueueE2ETest : QueueE2ESupport() {
    private val ns = "queue_enqueue_ns"
    private val queue = "events"

    @Test
    fun `enqueue appends messages and assigns a ULID id`() {
        createNamespace(ns)
        createQueue(ns, queue, partitions = 8)

        client
            .post()
            .uri("/queue/v1/namespaces/$ns/queues/$queue/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "messages": [
                    {"key": "user-1", "seq": 1000, "value": {"body": "a"}},
                    {"key": "user-1", "seq": 1001, "value": "b"},
                    {"key": "user-2", "seq": 1002, "value": [1, 2, 3]}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.accepted")
            .isEqualTo(3)
            .jsonPath("$.results.length()")
            .isEqualTo(3)
            .jsonPath("$.results[0].status")
            .isEqualTo("CREATED")
            // The id is a server-assigned ULID, not a client value.
            .jsonPath("$.results[0].id")
            .exists()
    }
}
