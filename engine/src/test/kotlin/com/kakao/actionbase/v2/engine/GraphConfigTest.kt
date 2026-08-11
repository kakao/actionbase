package com.kakao.actionbase.v2.engine

import java.lang.reflect.Modifier

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class GraphConfigTest {
    @Nested
    @DisplayName("toString")
    inner class ToStringTest {
        @Test
        fun `should mask the metastore credentials`() {
            val rendered =
                GraphConfig.builder
                    .withMetastoreUser(USER)
                    .withMetastorePassword(PASSWORD)
                    .build()
                    .toString()

            assertFalse(rendered.contains(USER), "toString() rendered the metastore user")
            assertFalse(rendered.contains(PASSWORD), "toString() rendered the metastore password")
            assertTrue(rendered.contains("metastoreUser=****"), "toString() did not mask the metastore user")
            assertTrue(rendered.contains("metastorePassword=****"), "toString() did not mask the metastore password")
        }

        @Test
        fun `should render an unset credential as empty rather than masked`() {
            val rendered = GraphConfig.builder.build().toString()

            assertTrue(rendered.contains("metastoreUser=, "), "an unset metastore user should not look configured")
            assertTrue(rendered.contains("metastorePassword=, "), "an unset metastore password should not look configured")
        }

        @Test
        fun `should render every property`() {
            val rendered = GraphConfig.builder.build().toString()

            val missing =
                GraphConfig::class.java.declaredFields
                    .filterNot { Modifier.isStatic(it.modifiers) }
                    .map { it.name }
                    .filterNot { rendered.contains("$it=") }

            assertEquals(emptyList(), missing, "toString() is hand-written and must be extended whenever a property is added")
        }
    }

    private companion object {
        const val USER = "metastore-user-that-must-not-be-logged"
        const val PASSWORD = "metastore-password-that-must-not-be-logged"
    }
}
