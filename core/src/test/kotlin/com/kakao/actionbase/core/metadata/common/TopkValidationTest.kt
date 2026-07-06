package com.kakao.actionbase.core.metadata.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TopkValidationTest {
    @Test
    fun `expire=true requires expireAfterMillis`() {
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                Topk(topk = "t", expire = true)
            }
        assertEquals(true, ex.message!!.contains("expireAfterMillis must be set"))
    }

    @Test
    fun `expire=false rejects expireAfterMillis`() {
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                Topk(topk = "t", expire = false, expireAfterMillis = 1000)
            }
        assertEquals(true, ex.message!!.contains("must be null"))
    }

    @Test
    fun `expire=true with positive expireAfterMillis is valid`() {
        val topk = Topk(topk = "t", expire = true, expireAfterMillis = 31536000000)
        assertEquals(31536000000L, topk.expireAfterMillis)
    }

    @Test
    fun `expire=false without expireAfterMillis is valid`() {
        val topk = Topk(topk = "t", expire = false)
        assertEquals(null, topk.expireAfterMillis)
    }

    @Test
    fun `expireAfterMillis must be positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            Topk(topk = "t", expire = true, expireAfterMillis = 0)
        }
    }
}
