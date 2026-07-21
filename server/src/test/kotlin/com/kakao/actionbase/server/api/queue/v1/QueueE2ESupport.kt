package com.kakao.actionbase.server.api.queue.v1

import com.kakao.actionbase.server.test.E2ETestBase

import org.springframework.http.MediaType

/**
 * Shared setup for queue runtime E2E: create a namespace (a v3 database) and a queue backed by an
 * immutable edge table. A queue declares no schema of its own — `seq`, `value`, and the ULID `id`
 * are system fields.
 */
abstract class QueueE2ESupport : E2ETestBase() {
    protected fun createNamespace(ns: String) {
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$ns", "comment": "queue e2e"}""")
            .exchange()
    }

    protected fun createQueue(
        ns: String,
        queue: String,
        numPartitions: Int,
    ) {
        client
            .post()
            .uri("/queue/v1/namespaces/$ns/queues")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """{"queue": "$queue", "storage": "datastore://${ns}_ns/$queue", "partitions": $numPartitions}""",
            ).exchange()
            .expectStatus()
            .isOk
    }

    protected fun enqueue(
        ns: String,
        queue: String,
        messagesJson: String,
    ) {
        client
            .post()
            .uri("/queue/v1/namespaces/$ns/queues/$queue/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"messages": $messagesJson}""")
            .exchange()
            .expectStatus()
            .isOk
    }
}
