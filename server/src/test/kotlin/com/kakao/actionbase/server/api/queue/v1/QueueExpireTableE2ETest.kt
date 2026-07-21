package com.kakao.actionbase.server.api.queue.v1

import com.kakao.actionbase.engine.queue.EnqueueMessage
import com.kakao.actionbase.engine.queue.EnqueueRequest
import com.kakao.actionbase.engine.queue.PartitionHasher
import com.kakao.actionbase.engine.queue.QueueService
import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType

/**
 * Models the `_expire` table of the per-entity top-k feature (#386) on top of queue/v1: partition =
 * `hash(table|topk|entity) % partitions`, and **`expiredAt` is the `seq`** so an expire sweeper polls
 * entries in due order (oldest first). The rest of the record travels in the opaque `value`.
 *
 * Two angles, both end-to-end:
 *  1. the full create → enqueue → poll flow over HTTP;
 *  2. a workflow-style producer enqueueing through the engine [QueueService] bean (as another
 *     controller's workflow would) whose entries are then polled back over HTTP.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueueExpireTableE2ETest : E2ETestBase() {
    @Autowired
    private lateinit var queueService: QueueService

    private val ns = "topk_expire_ns"
    private val numPartitions = 30

    @BeforeAll
    fun setup() {
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$ns", "comment": "topk expire"}""")
            .exchange()
            .expectStatus()
            .isOk
    }

    private fun createExpireQueue(queue: String) {
        client
            .post()
            .uri("/queue/v1/namespaces/$ns/queues")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"queue": "$queue", "storage": "datastore://topk_expire_ns/$queue", "partitions": $numPartitions}""")
            .exchange()
            .expectStatus()
            .isOk
    }

    // All entries share one entity → same partition → deterministic per-partition order by seq.
    private val entity = "likes|top_actors|movie1"
    private val partition = PartitionHasher.partition(entity, numPartitions)

    private fun expireEntry(expiredAt: Long) = """{"key": "$entity", "seq": $expiredAt, "value": {"table": "likes", "topk": "top_actors", "entity": "movie1"}}"""

    @Test
    fun `expire entries are polled in due order over HTTP`() {
        val queue = "topk_expire_http"
        createExpireQueue(queue)

        // Registered out of order on purpose.
        client
            .post()
            .uri("/queue/v1/namespaces/$ns/queues/$queue/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"messages": [${expireEntry(300)}, ${expireEntry(100)}, ${expireEntry(400)}, ${expireEntry(200)}]}""")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.accepted")
            .isEqualTo(4)

        // Due order = ascending seq (oldest expiry first).
        client
            .get()
            .uri("/queue/v1/namespaces/$ns/queues/$queue/partitions/$partition/poll?limit=100")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.messages.length()")
            .isEqualTo(4)
            .jsonPath("$.messages[0].seq")
            .isEqualTo(100)
            .jsonPath("$.messages[3].seq")
            .isEqualTo(400)
            .jsonPath("$.messages[0].value.entity")
            .isEqualTo("movie1")
    }

    @Test
    fun `workflow enqueues via the service bean and entries are polled back over HTTP`() {
        val queue = "topk_expire_workflow"
        createExpireQueue(queue)

        // A workflow in another controller would build this and call the engine bean directly.
        val response =
            queueService
                .enqueue(
                    ns,
                    queue,
                    EnqueueRequest(
                        listOf(200L, 50L, 120L).map { expiredAt ->
                            EnqueueMessage(
                                key = entity,
                                seq = expiredAt,
                                value = mapOf("table" to "likes", "topk" to "top_actors", "entity" to "movie1"),
                            )
                        },
                    ),
                ).block()!!
        check(response.accepted == 3) { "expected 3 accepted, got ${response.accepted}" }

        // The consumer (sweeper) reads them back over HTTP in due order, and would stop at seq > now.
        client
            .get()
            .uri("/queue/v1/namespaces/$ns/queues/$queue/partitions/$partition/poll?limit=100")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.messages.length()")
            .isEqualTo(3)
            .jsonPath("$.messages[0].seq")
            .isEqualTo(50)
            .jsonPath("$.messages[2].seq")
            .isEqualTo(200)
    }
}
