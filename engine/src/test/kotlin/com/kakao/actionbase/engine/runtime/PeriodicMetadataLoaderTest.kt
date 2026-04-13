package com.kakao.actionbase.engine.runtime

import java.time.Duration

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

import org.junit.jupiter.api.Test

class PeriodicMetadataLoaderTest {
    @Test
    fun `periodic loop fires on schedule`() {
        val loader = PeriodicMetadataLoader(
            metastoreReloadInitialDelay = Duration.ZERO,
            metastoreReloadInterval = Duration.ofMillis(20),
        )
        Engine(loader).use {
            waitUntil(Duration.ofSeconds(2)) { loader.reloadCount() >= 3 }
            assertTrue(loader.reloadCount() >= 3)
            assertNotNull(loader.lastReloadAt())
        }
    }

    @Test
    fun `close halts the loop`() {
        val loader = PeriodicMetadataLoader(
            metastoreReloadInitialDelay = Duration.ZERO,
            metastoreReloadInterval = Duration.ofMillis(20),
        )
        val engine = Engine(loader)
        waitUntil(Duration.ofSeconds(1)) { loader.reloadCount() >= 1 }
        engine.close()
        Thread.sleep(50)
        val snapshot = loader.reloadCount()
        Thread.sleep(100)
        assertTrue(loader.reloadCount() == snapshot, "reloadCount should not advance after close")
    }

    @Test
    fun `bind triggers exactly one reload before the periodic loop kicks in`() {
        // A long interval lets us observe the initial reload alone.
        val loader = PeriodicMetadataLoader(
            metastoreReloadInitialDelay = Duration.ZERO,
            metastoreReloadInterval = Duration.ofMinutes(10),
        )
        Engine(loader).use {
            waitUntil(Duration.ofSeconds(1)) { loader.reloadCount() == 1L }
            // Window between the initial reload and the next periodic tick
            // should stay at exactly 1.
            Thread.sleep(50)
            assertEquals(1, loader.reloadCount())
        }
    }

    @Test
    fun `null interval still runs the initial reload`() {
        val loader = PeriodicMetadataLoader(
            metastoreReloadInitialDelay = Duration.ZERO,
            metastoreReloadInterval = null,
        )
        Engine(loader).use {
            waitUntil(Duration.ofSeconds(1)) { loader.reloadCount() == 1L }
            // No periodic loop, so the count must stay at 1 forever.
            Thread.sleep(50)
            assertEquals(1, loader.reloadCount())
        }
    }

    @Test
    fun `initial delay defers the first reload`() {
        val loader = PeriodicMetadataLoader(
            metastoreReloadInitialDelay = Duration.ofMillis(300),
            metastoreReloadInterval = null,
        )
        Engine(loader).use {
            // Before the delay expires, the reload has not fired yet.
            Thread.sleep(100)
            assertEquals(0, loader.reloadCount())
            // After the delay, the one-shot reload arrives.
            waitUntil(Duration.ofSeconds(2)) { loader.reloadCount() == 1L }
        }
    }

    private fun waitUntil(timeout: Duration, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeout.toMillis()
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("condition not met within $timeout")
    }
}
