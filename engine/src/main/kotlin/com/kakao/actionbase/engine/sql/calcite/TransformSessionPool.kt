package com.kakao.actionbase.engine.sql.calcite

import com.kakao.actionbase.core.metadata.common.StructType

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The sessions one prepared transform can execute on. Concurrency comes from holding several rather
 * than sharing one, since a statement takes a single thread.
 *
 * Each session carries its own copy of the plan and costs milliseconds to open, so the pool stops at
 * [maximumSessions] and a request that finds them all busy waits instead of opening another.
 */
internal class TransformSessionPool(
    private val sql: String,
    private val schemas: Map<String, StructType>,
    private val maximumSessions: Int = DEFAULT_MAXIMUM_SESSIONS,
) : AutoCloseable {
    private val idle = ArrayBlockingQueue<TransformSession>(maximumSessions)
    private val opened = AtomicInteger()
    private val closed = AtomicBoolean()

    /** Opened here, not on the first request. Its metadata is immutable, so reading it while leased is safe. */
    private val first: TransformSession = open().also { idle.offer(it) }

    val schema: StructType get() = first.schema

    val parameterCount: Int get() = first.parameterCount

    val sessionCount: Int get() = opened.get()

    fun <T> lease(block: (TransformSession) -> T): T {
        check(!closed.get()) { "This transform has been closed." }

        val session = borrow()
        return try {
            block(session)
        } finally {
            release(session)
        }
    }

    /** Opens sessions up front so no request pays for one. */
    fun warmUp(sessions: Int) {
        while (opened.get() < minOf(sessions, maximumSessions)) {
            idle.offer(openIfUnderCap() ?: return)
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            generateSequence { idle.poll() }.forEach { it.close() }
        }
    }

    private fun borrow(): TransformSession =
        idle.poll()
            ?: openIfUnderCap()
            ?: idle.poll(LEASE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            ?: throw IllegalStateException("All $maximumSessions transform sessions were busy for $LEASE_TIMEOUT_MILLIS ms.")

    private fun release(session: TransformSession) {
        if (closed.get() || !idle.offer(session)) {
            session.close()
            opened.decrementAndGet()
        }
    }

    private fun open(): TransformSession = openIfUnderCap() ?: throw IllegalStateException("The pool is already holding $maximumSessions sessions.")

    /** Claims a slot before opening, so the cap holds even when several threads arrive at once. */
    private fun openIfUnderCap(): TransformSession? {
        while (true) {
            val current = opened.get()
            if (current >= maximumSessions) {
                return null
            }
            if (opened.compareAndSet(current, current + 1)) {
                return runCatching { TransformSession(sql, schemas) }
                    .getOrElse { failure ->
                        opened.decrementAndGet()
                        throw failure
                    }
            }
        }
    }

    private companion object {
        val DEFAULT_MAXIMUM_SESSIONS = Runtime.getRuntime().availableProcessors()
        const val LEASE_TIMEOUT_MILLIS = 1_000L
    }
}
