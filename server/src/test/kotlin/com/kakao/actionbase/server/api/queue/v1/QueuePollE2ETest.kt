package com.kakao.actionbase.server.api.queue.v1

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Poll E2E: append then poll returns messages ordered by `orderBy`, a small limit paginates via the
 * forward cursor, and splitting a queue across shards covers every message exactly once.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueuePollE2ETest : QueueE2ESupport() {
    private val db = "queue_poll_db"

    @Test
    fun `poll returns messages ordered by orderBy and then drains`() {
        val queue = "ordered"
        createDatabase(db)
        createQueue(db, queue, partitionCount = 4)
        enqueue(
            db,
            queue,
            """
            [
              {"key": "u", "id": "m1", "orderBy": 1000, "payload": {"payload": "a"}},
              {"key": "u", "id": "m2", "orderBy": 1001, "payload": {"payload": "b"}},
              {"key": "u", "id": "m3", "orderBy": 1002, "payload": {"payload": "c"}}
            ]
            """.trimIndent(),
        )

        val page = poll(queue, shard = "0/1", limit = 10)
        assertEquals(listOf("m1", "m2", "m3"), page.messages.map { it.id })
        assertEquals(listOf("a", "b", "c"), page.messages.map { it.payload["payload"] })
        assertTrue(!page.hasNext, "a fully-read page must not report more")
        assertNull(page.cursor)
    }

    @Test
    fun `small limit paginates through the forward cursor`() {
        val queue = "paged"
        createDatabase(db)
        createQueue(db, queue, partitionCount = 4)
        enqueue(
            db,
            queue,
            """
            [
              {"key": "u", "id": "m1", "orderBy": 1, "payload": {"payload": "a"}},
              {"key": "u", "id": "m2", "orderBy": 2, "payload": {"payload": "b"}},
              {"key": "u", "id": "m3", "orderBy": 3, "payload": {"payload": "c"}},
              {"key": "u", "id": "m4", "orderBy": 4, "payload": {"payload": "d"}}
            ]
            """.trimIndent(),
        )

        val first = poll(queue, shard = "0/1", limit = 2)
        assertEquals(listOf("m1", "m2"), first.messages.map { it.id })
        assertTrue(first.hasNext, "there are more messages")

        val second = poll(queue, shard = "0/1", limit = 2, cursor = first.cursor)
        assertEquals(listOf("m3", "m4"), second.messages.map { it.id })
    }

    @Test
    fun `shards partition the queue with no overlap`() {
        val queue = "sharded"
        createDatabase(db)
        createQueue(db, queue, partitionCount = 4)
        val ids = (0 until 12).map { "m$it" }
        enqueue(
            db,
            queue,
            ids.joinToString(prefix = "[", postfix = "]") { id ->
                """{"key": "$id", "id": "$id", "orderBy": ${id.drop(1)}, "payload": {"payload": "$id"}}"""
            },
        )

        val shard0 = poll(queue, shard = "0/2", limit = 100).messages.map { it.id }
        val shard1 = poll(queue, shard = "1/2", limit = 100).messages.map { it.id }
        assertTrue(shard0.intersect(shard1.toSet()).isEmpty(), "shards must not overlap")
        assertEquals(ids.toSet(), (shard0 + shard1).toSet())
    }

    private fun poll(
        queue: String,
        shard: String,
        limit: Int,
        cursor: String? = null,
    ): PollResponse =
        client
            .get()
            .uri { builder ->
                builder
                    .path("/queue/v1/databases/$db/queues/$queue/poll")
                    .queryParam("shard", shard)
                    .queryParam("limit", limit)
                    .apply { cursor?.let { queryParam("cursor", it) } }
                    .build()
            }.exchange()
            .expectStatus()
            .isOk
            .expectBody(PollResponse::class.java)
            .returnResult()
            .responseBody!!
}
