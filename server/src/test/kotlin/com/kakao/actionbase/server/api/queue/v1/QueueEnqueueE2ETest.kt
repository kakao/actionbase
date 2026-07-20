package com.kakao.actionbase.server.api.queue.v1

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType

/**
 * Enqueue E2E: appending messages returns per-message routing (partition, id) and a CREATED status,
 * and a message whose non-nullable `orderBy` maps correctly is accepted.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueueEnqueueE2ETest : QueueE2ESupport() {
    private val db = "queue_enqueue_db"
    private val queue = "events"

    @Test
    fun `enqueue appends messages and reports routing`() {
        createDatabase(db)
        createQueue(db, queue, partitionCount = 8)

        client
            .post()
            .uri("/queue/v1/databases/$db/queues/$queue/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "messages": [
                    {"key": "user-1", "id": "m1", "orderBy": 1000, "payload": {"payload": "a"}},
                    {"key": "user-1", "id": "m2", "orderBy": 1001, "payload": {"payload": "b"}},
                    {"key": "user-2", "id": "m3", "orderBy": 1002, "payload": {"payload": "c"}}
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
            .jsonPath("$.results[?(@.id == 'm1')].status")
            .isEqualTo("CREATED")
    }
}
