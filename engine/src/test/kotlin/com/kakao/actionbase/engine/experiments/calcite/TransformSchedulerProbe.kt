package com.kakao.actionbase.engine.experiments.calcite

import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.core.metadata.common.StructType
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.engine.sql.DataFrame
import com.kakao.actionbase.engine.sql.Row
import com.kakao.actionbase.engine.sql.calcite.SqlTransform

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

import reactor.blockhound.BlockHound
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers

/**
 * Asks BlockHound what a transform does to a non-blocking scheduler — the question that decides how a
 * WebFlux request path may call one.
 *
 * On 14 cores, `Schedulers.parallel()`, 400 executions over a 100-row frame, pool capped at 14:
 *
 * ```
 * parallelism=2   sessions=2   failures=1    BlockingOperationError: java.io.FileInputStream#readBytes
 * parallelism=14  sessions=14  failures=0
 * parallelism=64  sessions=14  failures=20   BlockingOperationError: jdk.internal.misc.Unsafe#park
 * ```
 *
 * Executing a prepared transform is clean: at parallelism 14, where every execution finds a session
 * already open, nothing blocks. The two failures are the edges around it. Opening a session reads from
 * disk — the JDBC driver, the generated class — so a pool that grows on the request path blocks the
 * event loop; that is why a transform has to be warmed up before it serves traffic. And once
 * concurrency passes the pool size, waiting for a session parks the thread, so the pool has to cover
 * the threads that can be inside `execute` at once, or the call belongs on `boundedElastic`.
 *
 * `BlockHound.install()` is JVM-wide, which is why this stays disabled rather than joining the suite.
 */
@Disabled("Installs BlockHound for the whole JVM; run it by hand when the scheduler contract is in question.")
class TransformSchedulerProbe {
    @Test
    fun `what blocks when a transform runs on a non-blocking scheduler`() {
        BlockHound.install()

        listOf(2, Runtime.getRuntime().availableProcessors(), 64).forEach { parallelism ->
            SqlTransform().use { transform ->
                val prepared = transform.prepare(mapOf("hop1" to schema), "SELECT hop1.target AS t FROM hop1 ORDER BY hop1.metric DESC")
                val failures =
                    Flux
                        .range(1, 400)
                        .parallel(parallelism)
                        .runOn(Schedulers.parallel())
                        .map { runCatching { prepared.execute(mapOf("hop1" to frame)).rows.size }.exceptionOrNull()?.toString() ?: "" }
                        .sequential()
                        .filter { it.isNotEmpty() }
                        .collectList()
                        .block()!!

                println("parallelism=$parallelism sessions=${prepared.sessionCount} failures=${failures.size} ${failures.firstOrNull()?.take(120) ?: ""}")
            }
        }
    }

    private companion object {
        val schema =
            StructType(
                listOf(
                    StructField("target", PrimitiveType.STRING, "", false),
                    StructField("metric", PrimitiveType.LONG, "", false),
                ),
            )

        val frame = DataFrame((1..100).map { Row(mapOf("target" to "PG$it", "metric" to it.toLong()), schema) }, schema, total = 100L)
    }
}
