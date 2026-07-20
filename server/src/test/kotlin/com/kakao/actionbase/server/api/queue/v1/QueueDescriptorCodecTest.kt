package com.kakao.actionbase.server.api.queue.v1

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

import org.junit.jupiter.api.Test

class QueueDescriptorCodecTest {
    @Test
    fun `round-trips queue metadata`() {
        val meta = QueueMeta(partitionCount = 480, orderBy = "seq")
        val comment = QueueDescriptorCodec.encode(meta)
        assertTrue(comment.startsWith("queue/v1 "), "must carry the marker, was: $comment")
        assertEquals(meta, QueueDescriptorCodec.decode(comment))
    }

    @Test
    fun `decode returns null for a non-queue comment`() {
        assertNull(QueueDescriptorCodec.decode(null))
        assertNull(QueueDescriptorCodec.decode("just a plain table"))
        assertNull(QueueDescriptorCodec.decode("queue/v2 {}"))
    }

    @Test
    fun `decode returns null for a malformed marker payload`() {
        assertNull(QueueDescriptorCodec.decode("queue/v1 not-json"))
    }
}
