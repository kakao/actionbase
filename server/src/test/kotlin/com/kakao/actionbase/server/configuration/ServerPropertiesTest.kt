package com.kakao.actionbase.server.configuration

import com.kakao.actionbase.core.metadata.features.TableFeature

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
    fun `binds database-level-features entries from kebab-case keys`() {
        val bound =
            bind(
                mapOf(
                    "actionbase.database-level-features[0].database" to "fanin",
                    "actionbase.database-level-features[0].features[0]" to "INSERT_MERGE",
                ),
            )

        assertEquals(setOf(TableFeature.INSERT_MERGE), bound.featuresOf("fanin"))
    }

    @Test
    fun `featuresOf returns empty set for an unlisted database`() {
        val bound =
            bind(
                mapOf(
                    "actionbase.database-level-features[0].database" to "fanin",
                    "actionbase.database-level-features[0].features[0]" to "INSERT_MERGE",
                ),
            )

        assertEquals(emptySet(), bound.featuresOf("other"))
    }

    @Test
    fun `no entries binds to an empty gate`() {
        val bound = bind(emptyMap())

        assertEquals(emptySet(), bound.featuresOf("fanin"))
    }

    @Test
    fun `unknown feature name fails binding`() {
        val exception =
            assertFailsWith<BindException> {
                bind(
                    mapOf(
                        "actionbase.database-level-features[0].database" to "fanin",
                        "actionbase.database-level-features[0].features[0]" to "INSERT_MEGRE",
                    ),
                )
            }

        assertTrue(exception.message!!.contains("database-level-features"))
    }

    @Test
    fun `invalid database name fails binding`() {
        val exception =
            assertFailsWith<BindException> {
                bind(
                    mapOf(
                        "actionbase.database-level-features[0].database" to "fanin-svc",
                        "actionbase.database-level-features[0].features[0]" to "INSERT_MERGE",
                    ),
                )
            }

        assertTrue(
            exception.cause!!
                .cause!!
                .message!!
                .contains("Invalid database names"),
        )
    }

    @Test
    fun `duplicate database entries fail binding`() {
        val exception =
            assertFailsWith<BindException> {
                bind(
                    mapOf(
                        "actionbase.database-level-features[0].database" to "fanin",
                        "actionbase.database-level-features[0].features[0]" to "INSERT_MERGE",
                        "actionbase.database-level-features[1].database" to "fanin",
                        "actionbase.database-level-features[1].features[0]" to "INSERT_MERGE",
                    ),
                )
            }

        assertTrue(
            exception.cause!!
                .cause!!
                .message!!
                .contains("Duplicate database entries"),
        )
    }
}
