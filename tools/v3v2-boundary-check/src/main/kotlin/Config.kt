/**
 * Boundary check configuration.
 *
 * Detect all callers of Graph class, then exclude known-OK callers.
 * Include overrides exclude (more specific wins).
 */
object Config {
    const val SCOPE = "com/kakao/actionbase"

    val CLASS_DIRS = listOf(
        "server/build/classes/kotlin/main",
        "server/build/classes/java/main",
        "engine/build/classes/kotlin/main",
        "engine/build/classes/java/main",
        "core/build/classes/kotlin/main",
        "core/build/classes/java/main",
        "core-java/build/classes/java/main",
    )

    /** Target V2 class — direct calls to this are leaks unless excluded. */
    const val TARGET_CLASS = "com.kakao.actionbase.v2.engine.Graph"

    /** Package exclude: V2 internals (prefix match, includes sub-packages). */
    val EXCLUDED_PACKAGES = listOf(
        "com.kakao.actionbase.v2.engine",
        "com.kakao.actionbase.server.api.graph.v2",
    )

    /** Package include: overrides exclude (prefix match). */
    val INCLUDED_PACKAGES = listOf(
        "com.kakao.actionbase.v2.engine.v3",
    )

    /** Class exclude: adapter classes (prefix match on simple class name). */
    val EXCLUDED_CLASS_PREFIXES = listOf(
        "V2Backed",
    )
}
