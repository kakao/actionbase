package com.kakao.actionbase.server.api.queue.v1

import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType

/**
 * Models the `_expire` table of the per-entity top-k feature (#386) on top of queue/v1: partition =
 * `hash(table|topk|entity) % partitionCount`, message id = `table|topk|entity|expiredAt`, and
 * **`expiredAt` is the `orderBy`** so an expire sweeper polls entries in due order (oldest first).
 *
 * Two angles, both end-to-end:
 *  1. the full create → enqueue → poll flow over HTTP;
 *  2. a workflow-style producer enqueueing through the `QueueService` bean (as another controller's
 *     workflow would) whose entries are then polled back over HTTP.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueueExpireTableE2ETest : E2ETestBase() {
    @Autowired
    private lateinit var queueService: QueueService

    private val db = "topk_expire_db"

    @BeforeAll
    fun setup() {
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$db", "comment": "topk expire"}""")
            .exchange()
            .expectStatus()
            .isOk
    }

    private fun createExpireQueue(queue: String) {
        client
            .post()
            .uri("/queue/v1/databases/$db/queues")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "queue": "$queue",
                  "storage": "datastore://topk_expire_ns/$queue",
                  "partitionCount": 30,
                  "orderBy": "expiredAt",
                  "properties": [
                    {"name": "expiredAt", "type": "long",   "nullable": false, "comment": "expiry epoch millis"},
                    {"name": "table",     "type": "string", "nullable": false, "comment": "source table"},
                    {"name": "topk",      "type": "string", "nullable": false, "comment": "topk name"},
                    {"name": "entity",    "type": "string", "nullable": false, "comment": "filter entity"}
                  ],
                  "comment": "topk expire table"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }

    // All entries share one entity → same partition → deterministic per-partition order by expiredAt.
    private val entity = "likes|top_actors|movie1"

    private fun expireEntry(expiredAt: Long) =
        """
        {"key": "$entity", "id": "$entity|$expiredAt",
         "payload": {"expiredAt": $expiredAt, "table": "likes", "topk": "top_actors", "entity": "movie1"}}
        """.trimIndent()

    @Test
    fun `expire entries are polled in due order over HTTP`() {
        val queue = "topk_expire_http"
        createExpireQueue(queue)

        // Registered out of order on purpose.
        client
            .post()
            .uri("/queue/v1/databases/$db/queues/$queue/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"messages": [${expireEntry(300)}, ${expireEntry(100)}, ${expireEntry(400)}, ${expireEntry(200)}]}""")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.accepted")
            .isEqualTo(4)

        // Due order = ascending expiredAt (oldest expiry first).
        client
            .get()
            .uri("/queue/v1/databases/$db/queues/$queue/poll?shard=0/1&limit=100")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.messages.length()")
            .isEqualTo(4)
            .jsonPath("$.messages[0].orderBy")
            .isEqualTo(100)
            .jsonPath("$.messages[3].orderBy")
            .isEqualTo(400)
            .jsonPath("$.messages[0].payload.entity")
            .isEqualTo("movie1")
    }

    @Test
    fun `workflow enqueues via the service bean and entries are polled back over HTTP`() {
        val queue = "topk_expire_workflow"
        createExpireQueue(queue)

        // A workflow in another controller would build this and call the bean directly.
        val response =
            queueService
                .enqueue(
                    db,
                    queue,
                    EnqueueRequest(
                        listOf(200L, 50L, 120L).map { expiredAt ->
                            EnqueueMessage(
                                key = entity,
                                id = "$entity|$expiredAt",
                                payload =
                                    mapOf(
                                        "expiredAt" to expiredAt,
                                        "table" to "likes",
                                        "topk" to "top_actors",
                                        "entity" to "movie1",
                                    ),
                            )
                        },
                    ),
                ).block()!!
        check(response.accepted == 3) { "expected 3 accepted, got ${response.accepted}" }

        // The consumer (sweeper) reads them back over HTTP in due order, and would stop at expiredAt > now.
        client
            .get()
            .uri("/queue/v1/databases/$db/queues/$queue/poll?shard=0/1&limit=100")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.messages.length()")
            .isEqualTo(3)
            .jsonPath("$.messages[0].orderBy")
            .isEqualTo(50)
            .jsonPath("$.messages[2].orderBy")
            .isEqualTo(200)
    }
}
