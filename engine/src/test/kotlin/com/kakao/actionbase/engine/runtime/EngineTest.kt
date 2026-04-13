package com.kakao.actionbase.engine.runtime

import kotlin.test.assertEquals
import kotlin.test.assertSame

import org.junit.jupiter.api.Test

class EngineTest {
    @Test
    fun `construction binds loader to self exactly once`() {
        val loader = FakeLoader()
        val engine = Engine(loader)
        assertEquals(1, loader.bindCount)
        assertSame(engine, loader.bound)
    }

    @Test
    fun `close delegates to loader`() {
        val loader = FakeLoader()
        val engine = Engine(loader)
        engine.close()
        assertEquals(1, loader.closeCount)
    }

    @Test
    fun `try-with-resources closes the loader`() {
        val loader = FakeLoader()
        Engine(loader).use { /* no-op */ }
        assertEquals(1, loader.closeCount)
    }

    @Test
    fun `create wires defaults without throwing`() {
        Engine.create().close()
    }

    private class FakeLoader : CatalogLoader {
        var bound: Engine? = null
        var bindCount = 0
        var closeCount = 0

        override fun bind(engine: Engine) {
            bound = engine
            bindCount++
        }

        override fun close() {
            closeCount++
        }
    }
}
