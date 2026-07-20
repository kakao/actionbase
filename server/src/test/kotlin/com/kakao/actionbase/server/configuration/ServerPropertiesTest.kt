package com.kakao.actionbase.server.configuration

import com.kakao.actionbase.core.metadata.features.MutationFeature

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.BindException
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource

class ServerPropertiesTest {
    private fun bind(properties: Map<String, String>): ServerProperties {
        val source =
            MapConfigurationPropertySource(
                mapOf(
                    "actionbase.tenant" to "test",
                    "actionbase.datastore.type" to "memory",
                ) + properties,
            )
        return Binder(source).bind("actionbase", Bindable.of(ServerProperties::class.java)).get()
    }

    @Test
    fun `binds feature-flags entries from kebab-case keys`() {
        val bound =
            bind(
                mapOf(
                    "actionbase.feature-flags[0].feature" to "INSERT_MERGE",
                    "actionbase.feature-flags[0].scope.databases[0]" to "fanin",
                ),
            )

        assertEquals(
            listOf(
                ServerProperties.FeatureFlag(
                    MutationFeature.INSERT_MERGE,
                    ServerProperties.Scope(setOf("fanin")),
                ),
            ),
            bound.featureFlags,
        )
    }

    @Test
    fun `a feature with no scope binds to an empty scope`() {
        val bound =
            bind(
                mapOf(
                    "actionbase.feature-flags[0].feature" to "INSERT_MERGE",
                ),
            )

        assertEquals(
            listOf(ServerProperties.FeatureFlag(MutationFeature.INSERT_MERGE, ServerProperties.Scope())),
            bound.featureFlags,
        )
    }

    @Test
    fun `a feature can target multiple databases`() {
        val bound =
            bind(
                mapOf(
                    "actionbase.feature-flags[0].feature" to "INSERT_MERGE",
                    "actionbase.feature-flags[0].scope.databases[0]" to "fanin",
                    "actionbase.feature-flags[0].scope.databases[1]" to "timeline",
                ),
            )

        assertEquals(
            setOf("fanin", "timeline"),
            bound.featureFlags
                .single()
                .scope.databases,
        )
    }

    @Test
    fun `no entries binds to an empty gate`() {
        val bound = bind(emptyMap())

        assertEquals(emptyList(), bound.featureFlags)
    }

    @Test
    fun `unknown feature name fails binding`() {
        val exception =
            assertFailsWith<BindException> {
                bind(
                    mapOf(
                        "actionbase.feature-flags[0].feature" to "INSERT_MEGRE",
                        "actionbase.feature-flags[0].scope.databases[0]" to "fanin",
                    ),
                )
            }

        assertTrue(exception.message!!.contains("feature-flags"))
    }

    @Test
    fun `duplicate feature entries fail binding`() {
        val exception =
            assertFailsWith<BindException> {
                bind(
                    mapOf(
                        "actionbase.feature-flags[0].feature" to "INSERT_MERGE",
                        "actionbase.feature-flags[0].scope.databases[0]" to "fanin",
                        "actionbase.feature-flags[1].feature" to "INSERT_MERGE",
                        "actionbase.feature-flags[1].scope.databases[0]" to "timeline",
                    ),
                )
            }

        assertTrue(
            exception.cause!!
                .cause!!
                .message!!
                .contains("Duplicate feature entries"),
        )
    }
}
