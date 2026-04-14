package com.kakao.actionbase.engine

import com.kakao.actionbase.core.metadata.AliasDescriptor
import com.kakao.actionbase.core.metadata.DatabaseDescriptor
import com.kakao.actionbase.core.metadata.DatabaseId
import com.kakao.actionbase.core.metadata.TableDescriptor
import com.kakao.actionbase.core.metadata.TableId
import com.kakao.actionbase.engine.catalog.Catalog

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

    private class FakeLoader : Catalog {
        var bound: Engine? = null
        var bindCount = 0
        var closeCount = 0

        override val databases: Map<DatabaseId, DatabaseDescriptor> = emptyMap()
        override val tables: Map<TableId, TableDescriptor<*>> = emptyMap()
        override val aliases: Map<TableId, AliasDescriptor> = emptyMap()

        override fun bind(engine: Engine) {
            bound = engine
            bindCount++
        }

        override fun close() {
            closeCount++
        }
    }
}
