package com.kakao.actionbase.engine.catalog

import com.kakao.actionbase.engine.Engine

import java.time.Duration

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

import org.junit.jupiter.api.Test

class PeriodicCatalogTest {
    @Test
    fun `periodic loop fires on schedule`() {
        val catalog =
            PeriodicCatalog(
                catalogReloadInitialDelay = Duration.ZERO,
                catalogReloadInterval = Duration.ofMillis(20),
            )
        Engine(catalog).use {
            waitUntil(Duration.ofSeconds(2)) { catalog.reloadCount() >= 3 }
            assertTrue(catalog.reloadCount() >= 3)
            assertNotNull(catalog.lastReloadAt())
        }
    }

    @Test
    fun `close halts the loop`() {
        val catalog =
            PeriodicCatalog(
                catalogReloadInitialDelay = Duration.ZERO,
                catalogReloadInterval = Duration.ofMillis(20),
            )
        val engine = Engine(catalog)
        waitUntil(Duration.ofSeconds(1)) { catalog.reloadCount() >= 1 }
        engine.close()
        Thread.sleep(50)
        val snapshot = catalog.reloadCount()
        Thread.sleep(100)
        assertTrue(catalog.reloadCount() == snapshot, "reloadCount should not advance after close")
    }

    @Test
    fun `bind triggers exactly one reload before the periodic loop kicks in`() {
        // A long interval lets us observe the initial reload alone.
        val catalog =
            PeriodicCatalog(
                catalogReloadInitialDelay = Duration.ZERO,
                catalogReloadInterval = Duration.ofMinutes(10),
            )
        Engine(catalog).use {
            waitUntil(Duration.ofSeconds(1)) { catalog.reloadCount() == 1L }
            // Window between the initial reload and the next periodic tick
            // should stay at exactly 1.
            Thread.sleep(50)
            assertEquals(1, catalog.reloadCount())
        }
    }

    @Test
    fun `null interval still runs the initial reload`() {
        val catalog =
            PeriodicCatalog(
                catalogReloadInitialDelay = Duration.ZERO,
                catalogReloadInterval = null,
            )
        Engine(catalog).use {
            waitUntil(Duration.ofSeconds(1)) { catalog.reloadCount() == 1L }
            // No periodic loop, so the count must stay at 1 forever.
            Thread.sleep(50)
            assertEquals(1, catalog.reloadCount())
        }
    }

    @Test
    fun `initial delay defers the first reload`() {
        val catalog =
            PeriodicCatalog(
                catalogReloadInitialDelay = Duration.ofMillis(300),
                catalogReloadInterval = null,
            )
        Engine(catalog).use {
            // Before the delay expires, the reload has not fired yet.
            Thread.sleep(100)
            assertEquals(0, catalog.reloadCount())
            // After the delay, the one-shot reload arrives.
            waitUntil(Duration.ofSeconds(2)) { catalog.reloadCount() == 1L }
        }
    }

    private fun waitUntil(
        timeout: Duration,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeout.toMillis()
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("condition not met within $timeout")
    }
}
