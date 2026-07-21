package com.kakao.actionbase.server.api.queue.v1

import com.kakao.actionbase.engine.queue.PartitionHasher
import com.kakao.actionbase.engine.queue.PollResponse
import com.kakao.actionbase.engine.queue.QueueCommitResponse

import kotlin.test.assertEquals
import kotlin.test.assertTrue

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Poll E2E: a single-partition poll returns messages ordered by `seq`, a small limit paginates via
 * the forward `offset`, and `until` bounds a refresh-style poll to due messages.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueuePollE2ETest : QueueE2ESupport() {
    private val ns = "queue_poll_ns"
    private val numPartitions = 4

    @Test
    fun `poll returns messages ordered by seq and then drains`() {
        val queue = "ordered"
        createNamespace(ns)
        createQueue(ns, queue, numPartitions)
        enqueue(
            ns,
            queue,
            """
            [
              {"key": "u", "seq": 1000, "value": {"body": "a"}},
              {"key": "u", "seq": 1001, "value": {"body": "b"}},
              {"key": "u", "seq": 1002, "value": {"body": "c"}}
            ]
            """.trimIndent(),
        )
        val p = PartitionHasher.partition("u", numPartitions)

        val page = poll(queue, p, limit = 10)
        assertEquals(listOf(1000L, 1001L, 1002L), page.messages.map { it.seq })
        assertEquals(listOf("a", "b", "c"), page.messages.map { (it.value as Map<*, *>)["body"] })
        assertTrue(!page.hasNext, "a fully-read page must not report more")
        assertEquals(1002L, page.offset)

        // Re-polling from the drained offset must not re-read anything (forward-only).
        val drained = poll(queue, p, limit = 10, offset = page.offset)
        assertTrue(drained.messages.isEmpty(), "drained partitions must not be re-read")
        assertTrue(!drained.hasNext)
    }

    @Test
    fun `small limit paginates through the forward offset`() {
        val queue = "paged"
        createNamespace(ns)
        createQueue(ns, queue, numPartitions)
        enqueue(
            ns,
            queue,
            """
            [
              {"key": "u", "seq": 1, "value": "a"},
              {"key": "u", "seq": 2, "value": "b"},
              {"key": "u", "seq": 3, "value": "c"},
              {"key": "u", "seq": 4, "value": "d"}
            ]
            """.trimIndent(),
        )
        val p = PartitionHasher.partition("u", numPartitions)

        val first = poll(queue, p, limit = 2)
        assertEquals(listOf(1L, 2L), first.messages.map { it.seq })
        assertTrue(first.hasNext, "there are more messages")

        val second = poll(queue, p, limit = 2, offset = first.offset)
        assertEquals(listOf(3L, 4L), second.messages.map { it.seq })
    }

    @Test
    fun `until bounds a refresh poll to due messages`() {
        val queue = "due"
        createNamespace(ns)
        createQueue(ns, queue, numPartitions)
        enqueue(
            ns,
            queue,
            """
            [
              {"key": "u", "seq": 100, "value": "a"},
              {"key": "u", "seq": 200, "value": "b"},
              {"key": "u", "seq": 300, "value": "c"}
            ]
            """.trimIndent(),
        )
        val p = PartitionHasher.partition("u", numPartitions)

        // until = 200 -> only messages due by 200; the seq=300 message is not yet due.
        val due = poll(queue, p, limit = 100, until = 200)
        assertEquals(listOf(100L, 200L), due.messages.map { it.seq })
    }

    @Test
    fun `poll rejects an out-of-range limit`() {
        val queue = "limited"
        createNamespace(ns)
        createQueue(ns, queue, numPartitions)

        client
            .get()
            .uri("/queue/v1/namespaces/$ns/queues/$queue/partitions/0/poll?limit=0")
            .exchange()
            .expectStatus()
            .isBadRequest
    }

    @Test
    fun `poll rejects an out-of-range partition`() {
        val queue = "outofrange"
        createNamespace(ns)
        createQueue(ns, queue, numPartitions)

        client
            .get()
            .uri("/queue/v1/namespaces/$ns/queues/$queue/partitions/$numPartitions/poll?limit=10")
            .exchange()
            .expectStatus()
            .isBadRequest
    }

    @Test
    fun `partitions endpoint returns the queue partition count`() {
        val queue = "meta"
        createNamespace(ns)
        createQueue(ns, queue, numPartitions)

        client
            .get()
            .uri("/queue/v1/namespaces/$ns/queues/$queue/partitions")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.partitions")
            .isEqualTo(numPartitions)
            .jsonPath("$.namespace")
            .isEqualTo(ns)
            .jsonPath("$.queue")
            .isEqualTo(queue)
    }

    @Test
    fun `poll then commit deletes the processed prefix and a re-poll reflects it`() {
        val queue = "committed"
        createNamespace(ns)
        createQueue(ns, queue, numPartitions)
        enqueue(
            ns,
            queue,
            """
            [
              {"key": "u", "seq": 1, "value": "a"},
              {"key": "u", "seq": 2, "value": "b"},
              {"key": "u", "seq": 3, "value": "c"},
              {"key": "u", "seq": 4, "value": "d"},
              {"key": "u", "seq": 5, "value": "e"}
            ]
            """.trimIndent(),
        )
        val p = PartitionHasher.partition("u", numPartitions)

        val batch = poll(queue, p, limit = 3)
        assertEquals(listOf(1L, 2L, 3L), batch.messages.map { it.seq })
        assertEquals(3L, batch.offset)
        assertEquals(3, commit(queue, p, offset = batch.offset!!))

        val afterCommit = poll(queue, p, limit = 10)
        assertEquals(listOf(4L, 5L), afterCommit.messages.map { it.seq })

        assertEquals(2, commit(queue, p, offset = afterCommit.offset!!))
        assertTrue(poll(queue, p, limit = 10).messages.isEmpty(), "a fully committed partition is empty")
    }

    private fun commit(
        queue: String,
        partition: Int,
        offset: Long,
    ): Int =
        client
            .delete()
            .uri("/queue/v1/namespaces/$ns/queues/$queue/partitions/$partition/messages?offset=$offset")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody(QueueCommitResponse::class.java)
            .returnResult()
            .responseBody!!
            .committed

    private fun poll(
        queue: String,
        partition: Int,
        limit: Int,
        offset: Long? = null,
        until: Long? = null,
    ): PollResponse =
        client
            .get()
            .uri { builder ->
                builder
                    .path("/queue/v1/namespaces/$ns/queues/$queue/partitions/$partition/poll")
                    .queryParam("limit", limit)
                    .apply {
                        offset?.let { queryParam("offset", it) }
                        until?.let { queryParam("until", it) }
                    }.build()
            }.exchange()
            .expectStatus()
            .isOk
            .expectBody(PollResponse::class.java)
            .returnResult()
            .responseBody!!
}
