package com.kakao.actionbase.core.metadata.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AggregationConstantsTest {
    @Test
    fun `parseRefreshTarget round-trips a key whose segment contains delimiters`() {
        val key =
            AggregationConstants.refreshTarget(
                database = "gift",
                table = "interaction",
                topk = "topk_segment",
                direction = Direction.IN,
                entity = AggregationConstants.GLOBAL_ENTITY,
                segment = "eventType:eq:like;time:bt:now-3h,now",
                target = "1001",
                refreshAt = 61_000L,
            )

        assertEquals(
            AggregationConstants.RefreshTargetKey(
                database = "gift",
                table = "interaction",
                topk = "topk_segment",
                direction = Direction.IN,
                entity = AggregationConstants.GLOBAL_ENTITY,
                segment = "eventType:eq:like;time:bt:now-3h,now",
                target = "1001",
                refreshAt = 61_000L,
            ),
            AggregationConstants.parseRefreshTarget(key),
        )
    }

    @Test
    fun `parseRefreshTarget maps the __all__ segment block back to a null segment`() {
        val key =
            AggregationConstants.refreshTarget(
                database = "db",
                table = "src",
                topk = "top_purchased",
                direction = Direction.OUT,
                entity = "user1",
                segment = null,
                target = "item1",
                refreshAt = 61_000L,
            )

        val parsed = AggregationConstants.parseRefreshTarget(key)

        assertEquals("user1", parsed?.entity)
        assertNull(parsed?.segment)
        assertEquals("item1", parsed?.target)
    }

    @Test
    fun `parseRefreshTarget rejects malformed keys`() {
        assertNull(AggregationConstants.parseRefreshTarget("garbage"))
        assertNull(AggregationConstants.parseRefreshTarget("no-dot:topk:OUT:e:__all__:t:1"))
        assertNull(AggregationConstants.parseRefreshTarget("db.src:topk:SIDEWAYS:e:__all__:t:1"))
        assertNull(AggregationConstants.parseRefreshTarget("db.src:topk:OUT:e:__all__:t:not-a-number"))
    }
}
