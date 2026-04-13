package com.kakao.actionbase.engine.runtime

/**
 * Loads the engine's view of the metastore.
 *
 * A `MetadataLoader` is bound to its [Engine] at construction time so it
 * can read metadata that is itself stored as Actionbase data — the same
 * self-hosted pattern as the v2 `Graph`. [Engine] depends on this
 * interface only and never on a concrete implementation.
 *
 * ## Lifecycle
 * - [bind] is called exactly once by [Engine]'s constructor and must not
 *   call back into the engine synchronously, since the engine is not yet
 *   fully constructed at that point.
 * - [close] is called by the engine on shutdown. Implementations must be
 *   idempotent — `close` may be called multiple times and must not throw.
 * - Both methods may assume single-threaded invocation by the engine.
 *
 * Phase 1 (#247) exposes only lifecycle. Data accessors land in phase 2.
 */
interface MetadataLoader : AutoCloseable {
    fun bind(engine: Engine)
}
