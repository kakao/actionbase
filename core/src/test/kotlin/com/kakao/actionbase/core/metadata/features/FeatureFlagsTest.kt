package com.kakao.actionbase.core.metadata.features

import com.kakao.actionbase.core.metadata.features.FeatureFlags.Item
import com.kakao.actionbase.core.metadata.features.FeatureFlags.Scope

import kotlin.test.assertFalse
import kotlin.test.assertTrue

import org.junit.jupiter.api.Test

class FeatureFlagsTest {
    @Test
    fun `resolves a feature for its databases`() {
        val flags = FeatureFlags(listOf(Item(MutationFeature.INSERT_MERGE, setOf("fanin", "timeline"))))

        assertTrue(flags.has(Scope("fanin"), MutationFeature.INSERT_MERGE))
        assertTrue(flags.has(Scope("timeline"), MutationFeature.INSERT_MERGE))
    }

    @Test
    fun `an unlisted database has no features`() {
        val flags = FeatureFlags(listOf(Item(MutationFeature.INSERT_MERGE, setOf("fanin"))))

        assertFalse(flags.has(Scope("other"), MutationFeature.INSERT_MERGE))
    }

    @Test
    fun `an empty baseline gates nothing`() {
        val flags = FeatureFlags(emptyList())

        assertFalse(flags.has(Scope("fanin"), MutationFeature.INSERT_MERGE))
    }
}
