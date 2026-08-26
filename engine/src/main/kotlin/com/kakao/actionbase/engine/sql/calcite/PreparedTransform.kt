package com.kakao.actionbase.engine.sql.calcite

import com.kakao.actionbase.core.metadata.common.StructType
import com.kakao.actionbase.engine.sql.DataFrame

/**
 * A transform already parsed, planned and compiled into a JDBC `PreparedStatement`. Executing it is
 * the only work left per request. Close it to give the statement back.
 *
 * Safe to share across threads; a statement is not, so each execution leases a session for its
 * duration. The lease stays inside one synchronous call — never hand a `ResultSet` to a reactive
 * chain, or the session outlives the thread that took it. From a chain, wrap the call in
 * `Mono.fromCallable`.
 */
class PreparedTransform internal constructor(
    private val sessions: TransformSessionPool,
    /** The frames this transform reads, and the columns each was prepared for. */
    private val inputSchemas: Map<String, StructType>,
) : AutoCloseable {
    /** The fetch names this transform reads. An execution has to supply a frame for each. */
    val inputs: Set<String> get() = inputSchemas.keys

    /** The columns the SQL returns, known without executing it. */
    val schema: StructType get() = sessions.schema

    /** How many `?` placeholders the SQL carries, so a caller can check its arguments up front. */
    val parameterCount: Int get() = sessions.parameterCount

    /** Sessions currently open, which is the concurrency this transform can actually serve. */
    val sessionCount: Int get() = sessions.sessionCount

    fun execute(
        frames: Map<String, DataFrame>,
        arguments: List<Any?> = emptyList(),
    ): DataFrame {
        val missing = inputs - frames.keys
        require(missing.isEmpty()) { "This transform reads ${inputs.joinToString()}; the execution is missing ${missing.joinToString()}." }
        require(arguments.size == parameterCount) { "This transform takes $parameterCount arguments, got ${arguments.size}." }
        // Left to the statement this surfaces as a SQLException from inside Calcite, after taking a session.
        inputSchemas.forEach { (name, schema) ->
            val given = frames.getValue(name).schema
            require(given == schema) {
                "`$name` was prepared for ${schema.fields.map { it.name }} but was given ${given.fields.map { it.name }}."
            }
        }

        return sessions.lease { session -> session.execute(frames, arguments) }
    }

    /** Opens sessions up front. Required before serving traffic: opening one reads from disk. */
    fun warmUp(sessions: Int = DEFAULT_MAXIMUM_SESSIONS) {
        this.sessions.warmUp(sessions)
    }

    override fun close() {
        sessions.close()
    }

    companion object {
        fun compile(
            schemas: Map<String, StructType>,
            sql: String,
            maximumSessions: Int = DEFAULT_MAXIMUM_SESSIONS,
        ): PreparedTransform = PreparedTransform(TransformSessionPool(sql, schemas, maximumSessions), schemas)

        /** One per event loop, so no event-loop thread ever waits for a session. */
        val DEFAULT_MAXIMUM_SESSIONS: Int = Runtime.getRuntime().availableProcessors()
    }
}
