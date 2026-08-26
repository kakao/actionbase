package com.kakao.actionbase.engine.sql.calcite

import com.kakao.actionbase.core.metadata.common.StructType
import com.kakao.actionbase.engine.sql.DataFrame

import com.github.benmanes.caffeine.cache.Caffeine

/**
 * Holds the prepared statement behind each SQL transform, keyed by the statement and the columns of
 * the frames it reads. Calcite's driver caches no plan between statements, so without this every
 * request re-parses, re-plans and re-compiles the same SQL.
 *
 * [prepare] pays that cost up front and fills the session pool; [prepared] answers only if it has
 * already been paid, so a caller can keep it off a thread that must not block. Close it to release
 * every statement it holds.
 */
class SqlTransform(
    private val maximumSessionsPerTransform: Int = PreparedTransform.DEFAULT_MAXIMUM_SESSIONS,
    maximumTransforms: Long = DEFAULT_MAXIMUM_TRANSFORMS,
) : AutoCloseable {
    private val transforms =
        Caffeine
            .newBuilder()
            .maximumSize(maximumTransforms)
            .evictionListener<PlanKey, PreparedTransform> { _, transform, _ -> transform?.close() }
            .build<PlanKey, PreparedTransform>()

    /** Transforms held, for a cache-hit metric to sit on. */
    val transformCount: Long get() = transforms.estimatedSize()

    fun run(
        frames: Map<String, DataFrame>,
        sql: String,
        arguments: List<Any?> = emptyList(),
    ): DataFrame = prepare(frames.mapValues { (_, frame) -> frame.schema }, sql).execute(frames, arguments)

    fun prepare(
        schemas: Map<String, StructType>,
        sql: String,
    ): PreparedTransform =
        transforms.get(PlanKey(sql, schemas)) { key ->
            PreparedTransform.compile(key.schemas, key.sql, maximumSessionsPerTransform).also { it.warmUp() }
        }

    fun prepared(
        schemas: Map<String, StructType>,
        sql: String,
    ): PreparedTransform? = transforms.getIfPresent(PlanKey(sql, schemas))

    override fun close() {
        transforms.asMap().values.forEach { it.close() }
        transforms.invalidateAll()
    }

    private data class PlanKey(
        val sql: String,
        val schemas: Map<String, StructType>,
    )

    companion object {
        const val DEFAULT_MAXIMUM_TRANSFORMS = 1_000L
    }
}
