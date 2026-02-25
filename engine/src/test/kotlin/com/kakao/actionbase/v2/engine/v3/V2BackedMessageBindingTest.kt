package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.core.edge.EdgeEvent
import com.kakao.actionbase.core.edge.MultiEdgeEvent
import com.kakao.actionbase.core.state.Event
import com.kakao.actionbase.core.state.EventType
import com.kakao.actionbase.v2.engine.v3.V2BackedMessageBinding.Companion.toV2TraceEdge

import kotlin.test.assertEquals

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class V2BackedMessageBindingTest {
    @Nested
    @DisplayName("toV2TraceEdge")
    inner class ToV2TraceEdgeTest {
        @Test
        fun `EdgeEvent converts to TraceEdge with source, target, and traceId`() {
            val event = Event.create(EventType.INSERT, 100L, "score" to 10)
            val edgeEvent = EdgeEvent(source = "user1", target = "item1", event = event)

            val traceEdge = edgeEvent.toV2TraceEdge()

            assertEquals(100L, traceEdge.ts)
            assertEquals("user1", traceEdge.src)
            assertEquals("item1", traceEdge.tgt)
            assertEquals(10, traceEdge.props["score"])
            assertEquals(event.id, traceEdge.traceId)
        }

        @Test
        fun `MultiEdgeEvent converts to TraceEdge with _source and _target from properties`() {
            val event =
                Event.create(
                    EventType.INSERT,
                    200L,
                    "_source" to "user2",
                    "_target" to "item2",
                    "score" to 20,
                )
            val multiEdgeEvent = MultiEdgeEvent(id = "edge-id-1", event = event)

            val traceEdge = multiEdgeEvent.toV2TraceEdge()

            assertEquals(200L, traceEdge.ts)
            assertEquals("user2", traceEdge.src)
            assertEquals("item2", traceEdge.tgt)
            assertEquals("edge-id-1", traceEdge.props["_id"])
            assertEquals(20, traceEdge.props["score"])
            assertEquals(null, traceEdge.props["_source"])
            assertEquals(null, traceEdge.props["_target"])
            assertEquals(event.id, traceEdge.traceId)
        }

        @Test
        fun `MultiEdgeEvent uses default value when _source or _target missing`() {
            val event = Event.create(EventType.INSERT, 300L, "score" to 30)
            val multiEdgeEvent = MultiEdgeEvent(id = "edge-id-2", event = event)

            val traceEdge = multiEdgeEvent.toV2TraceEdge()

            assertEquals("0", traceEdge.src)
            assertEquals("0", traceEdge.tgt)
            assertEquals("edge-id-2", traceEdge.props["_id"])
        }
    }
}
