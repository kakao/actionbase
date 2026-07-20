package com.kakao.actionbase.server.api.queue.v1

import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.server.test.E2ETestBase

import kotlin.test.assertEquals

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType

/**
 * Models the `_expire` table of the per-entity top-k feature (#386) on top of queue/v1: partition =
 * `hash(table|topk|entity) % N`, message id = `table|topk|entity|expiredAt`, and **`expiredAt` is the
 * `orderBy`** so an expire sweeper polls entries in due order (oldest expiry first). Drives the
 * `QueueAdminService`/`QueueService` beans directly — not over HTTP — to prove another controller's
 * workflow can control a queue programmatically.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueueExpireTableE2ETest : E2ETestBase() {
    @Autowired
    private lateinit var admin: QueueAdminService

    @Autowired
    private lateinit var queue: QueueService

    private val db = "topk_expire_db"
    private val expireQueue = "topk_expire"

    private fun createDb() {
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$db", "comment": "topk expire"}""")
            .exchange()
    }

    @Test
    fun `expire entries are polled in due order via the service beans`() {
        createDb()

        // A workflow in another controller would build this and call the bean directly.
        admin
            .createQueue(
                db,
                QueueCreateRequest(
                    queue = expireQueue,
                    storage = "datastore://topk_expire_ns/$expireQueue",
                    partitionCount = 2310,
                    orderBy = "expiredAt",
                    properties =
                        listOf(
                            QueueField("expiredAt", PrimitiveType.LONG, nullable = false, comment = "expiry epoch millis"),
                            QueueField("table", PrimitiveType.STRING, nullable = false, comment = "source table"),
                            QueueField("topk", PrimitiveType.STRING, nullable = false, comment = "topk name"),
                            QueueField("entity", PrimitiveType.STRING, nullable = false, comment = "filter entity"),
                        ),
                    comment = "topk expire table",
                ),
            ).block()

        // All entries share one entity, so they route to the same partition and their per-partition
        // order by expiredAt is deterministic. Enqueued out of order on purpose.
        val entity = "likes|top_actors|movie1"

        fun message(expiredAt: Long) =
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

        val enqueued =
            queue
                .enqueue(
                    db,
                    expireQueue,
                    EnqueueRequest(listOf(message(300), message(100), message(400), message(200))),
                ).block()!!
        assertEquals(4, enqueued.accepted)

        val page = queue.poll(db, expireQueue, Shard(0, 1), limit = 100, cursor = null).block()!!
        // Due order = ascending expiredAt.
        assertEquals(listOf(100L, 200L, 300L, 400L), page.messages.map { it.orderBy })
        assertEquals("movie1", page.messages.first().payload["entity"])

        // A sweeper for `now = 250` consumes only entries that have already expired.
        val due = page.messages.takeWhile { it.orderBy <= 250L }
        assertEquals(listOf(100L, 200L), due.map { it.orderBy })
    }
}
