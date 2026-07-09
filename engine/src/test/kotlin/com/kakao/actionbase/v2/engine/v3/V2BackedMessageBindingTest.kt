package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.core.edge.EdgeEvent
import com.kakao.actionbase.core.edge.MultiEdgeEvent
import com.kakao.actionbase.core.state.Event
import com.kakao.actionbase.core.state.EventType
import com.kakao.actionbase.core.state.State
import com.kakao.actionbase.engine.Audit
import com.kakao.actionbase.engine.MutationContext
import com.kakao.actionbase.engine.metadata.MutationMode
import com.kakao.actionbase.engine.metadata.MutationModeContext
import com.kakao.actionbase.v2.engine.cdc.Cdc
import com.kakao.actionbase.v2.engine.cdc.CdcContext
import com.kakao.actionbase.v2.engine.label.EdgeOperationStatus
import com.kakao.actionbase.v2.engine.v3.V2BackedMessageBinding.Companion.toV2TraceEdge
import com.kakao.actionbase.v2.engine.wal.Wal

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

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

    @Nested
    @DisplayName("writeCdc")
    inner class WriteCdcTest {
        private val loggedEvents = mutableListOf<ILoggingEvent>()
        private val latch = CountDownLatch(1)
        private val appender =
            object : AppenderBase<ILoggingEvent>() {
                override fun append(eventObject: ILoggingEvent) {
                    loggedEvents.add(eventObject)
                    latch.countDown()
                }
            }

        @BeforeEach
        fun attachAppender() {
            val logger = LoggerFactory.getLogger(V2BackedMessageBinding::class.java) as Logger
            appender.start()
            logger.addAppender(appender)
        }

        @AfterEach
        fun detachAppender() {
            val logger = LoggerFactory.getLogger(V2BackedMessageBinding::class.java) as Logger
            logger.detachAppender(appender)
            appender.stop()
        }

        @Test
        fun `CDC write failure is logged as ERROR with label, key, op, and traceId`() {
            val cause = RuntimeException("kafka delivery timeout")
            val cdc = mockk<Cdc>()
            every { cdc.write(any()) } returns Mono.error(cause)
            val binding = V2BackedMessageBinding(wal = mockk(), cdc = cdc)

            val ctx =
                MutationContext(
                    database = "test",
                    alias = "alias",
                    table = "table",
                    mutationMode = MutationModeContext.of(label = MutationMode.SYNC, request = null),
                    audit = Audit("actor"),
                    requestId = "req-1",
                )
            val event = Event.create(EventType.INSERT, 1L, "score" to 10)
            val edgeEvent = EdgeEvent(source = "src1", target = "tgt1", event = event)

            binding.writeCdc(
                ctx = ctx,
                events = listOf(edgeEvent),
                status = EdgeOperationStatus.CREATED.name,
                before = State.initial,
                after = State.initial,
                acc = 0,
            )

            assertTrue(latch.await(5, TimeUnit.SECONDS), "expected an ERROR log within 5s")
            val logged = loggedEvents.single()
            assertEquals(Level.ERROR, logged.level)
            assertTrue(logged.formattedMessage.contains("test.table"))
            assertTrue(logged.formattedMessage.contains("src1:tgt1"))
            assertTrue(logged.formattedMessage.contains("req-1"))
            assertEquals(cause, logged.throwableProxy.let { (it as ch.qos.logback.classic.spi.ThrowableProxy).throwable })
        }
    }
}
