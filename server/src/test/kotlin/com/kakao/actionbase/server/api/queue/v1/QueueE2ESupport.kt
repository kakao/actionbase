package com.kakao.actionbase.server.api.queue.v1

import com.kakao.actionbase.server.test.E2ETestBase

import org.springframework.http.MediaType

/**
 * Shared setup for queue runtime E2E: create a database and a queue backed by an immutable edge
 * table with a `payload` string field and a LONG `orderBy`.
 */
abstract class QueueE2ESupport : E2ETestBase() {
    protected fun createDatabase(db: String) {
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$db", "comment": "queue e2e"}""")
            .exchange()
    }

    protected fun createQueue(
        db: String,
        queue: String,
        partitionCount: Int,
        orderBy: String = "seq",
    ) {
        client
            .post()
            .uri("/queue/v1/databases/$db/queues")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "queue": "$queue",
                  "storage": "datastore://${db}_ns/$queue",
                  "partitionCount": $partitionCount,
                  "orderBy": "$orderBy",
                  "properties": [
                    {"name": "$orderBy", "type": "long", "nullable": false, "comment": "order"},
                    {"name": "payload", "type": "string", "nullable": true, "comment": "payload"}
                  ],
                  "comment": "queue e2e"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }

    protected fun enqueue(
        db: String,
        queue: String,
        messagesJson: String,
    ) {
        client
            .post()
            .uri("/queue/v1/databases/$db/queues/$queue/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"messages": $messagesJson}""")
            .exchange()
            .expectStatus()
            .isOk
    }
}
