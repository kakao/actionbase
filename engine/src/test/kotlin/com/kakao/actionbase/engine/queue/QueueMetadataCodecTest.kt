package com.kakao.actionbase.engine.queue

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

import org.junit.jupiter.api.Test

class QueueMetadataCodecTest {
    @Test
    fun `round-trips queue metadata`() {
        val meta = QueueMetadata(numPartitions = 30)
        val comment = QueueMetadataCodec.encode(meta)
        assertTrue(comment.startsWith("queue/v1 "), "must carry the marker, was: $comment")
        assertEquals(meta, QueueMetadataCodec.decode(comment))
    }

    @Test
    fun `decode returns null for a non-queue comment`() {
        assertNull(QueueMetadataCodec.decode(null))
        assertNull(QueueMetadataCodec.decode("just a plain table"))
        assertNull(QueueMetadataCodec.decode("queue/v2 {}"))
    }

    @Test
    fun `decode returns null for a malformed marker payload`() {
        assertNull(QueueMetadataCodec.decode("queue/v1 not-json"))
    }
}
