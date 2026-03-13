package com.kakao.actionbase.server.api.graph.v3.metadata

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MetadataStatusTest {
    @Test
    fun `ACTIVE matches only active entities`() {
        assertThat(MetadataStatus.ACTIVE.matches(active = true)).isTrue()
        assertThat(MetadataStatus.ACTIVE.matches(active = false)).isFalse()
    }

    @Test
    fun `INACTIVE matches only inactive entities`() {
        assertThat(MetadataStatus.INACTIVE.matches(active = true)).isFalse()
        assertThat(MetadataStatus.INACTIVE.matches(active = false)).isTrue()
    }

    @Test
    fun `ALL matches both active and inactive entities`() {
        assertThat(MetadataStatus.ALL.matches(active = true)).isTrue()
        assertThat(MetadataStatus.ALL.matches(active = false)).isTrue()
    }
}
