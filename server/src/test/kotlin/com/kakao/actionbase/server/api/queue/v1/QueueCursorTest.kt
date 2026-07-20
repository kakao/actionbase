package com.kakao.actionbase.server.api.queue.v1

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

import org.junit.jupiter.api.Test

class QueueCursorTest {
    @Test
    fun `round-trips through encode and decode`() {
        val cursor = QueueCursor(mapOf(0 to 1000L, 3 to 42L, 9 to Long.MAX_VALUE))
        assertEquals(cursor, QueueCursor.decode(cursor.encode()))
    }

    @Test
    fun `null or blank decodes to EMPTY`() {
        assertEquals(QueueCursor.EMPTY, QueueCursor.decode(null))
        assertEquals(QueueCursor.EMPTY, QueueCursor.decode(""))
        assertEquals(QueueCursor.EMPTY, QueueCursor.decode("   "))
    }

    @Test
    fun `EMPTY round-trips`() {
        assertEquals(QueueCursor.EMPTY, QueueCursor.decode(QueueCursor.EMPTY.encode()))
    }

    @Test
    fun `positionOf returns null for an absent partition`() {
        val cursor = QueueCursor(mapOf(0 to 1000L))
        assertEquals(1000L, cursor.positionOf(0))
        assertNull(cursor.positionOf(1))
    }

    @Test
    fun `encoded token is url-safe`() {
        val cursor = QueueCursor((0 until 50).associateWith { it * 1_000_000L })
        val token = cursor.encode()
        assert(token.all { it.isLetterOrDigit() || it == '-' || it == '_' }) { "not url-safe: $token" }
        assertEquals(cursor, QueueCursor.decode(token))
    }

    @Test
    fun `decode rejects an unsupported version`() {
        // version byte 2, count 0
        val forged =
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(byteArrayOf(2, 0, 0, 0, 0))
        assertFailsWith<IllegalArgumentException> { QueueCursor.decode(forged) }
    }
}
