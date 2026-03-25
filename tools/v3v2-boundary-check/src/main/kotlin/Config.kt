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

    /** Target V2 classes — direct references to these are leaks unless excluded. */
    val TARGET_CLASSES = listOf(
        // God object
        "com.kakao.actionbase.v2.engine.Graph",

        // V2 entities
        "com.kakao.actionbase.v2.engine.entity.LabelEntity",
        "com.kakao.actionbase.v2.engine.entity.ServiceEntity",
        "com.kakao.actionbase.v2.engine.entity.StorageEntity",
        "com.kakao.actionbase.v2.engine.entity.AliasEntity",
        "com.kakao.actionbase.v2.engine.entity.EntityName",
        "com.kakao.actionbase.v2.engine.entity.EdgeEntity",
        "com.kakao.actionbase.v2.engine.entity.QueryEntity",

        // V2 DDL requests
        "com.kakao.actionbase.v2.engine.service.ddl.ServiceCreateRequest",
        "com.kakao.actionbase.v2.engine.service.ddl.ServiceUpdateRequest",
        "com.kakao.actionbase.v2.engine.service.ddl.ServiceDeleteRequest",
        "com.kakao.actionbase.v2.engine.service.ddl.LabelCreateRequest",
        "com.kakao.actionbase.v2.engine.service.ddl.LabelUpdateRequest",
        "com.kakao.actionbase.v2.engine.service.ddl.LabelDeleteRequest",
        "com.kakao.actionbase.v2.engine.service.ddl.AliasCreateRequest",
        "com.kakao.actionbase.v2.engine.service.ddl.AliasUpdateRequest",
        "com.kakao.actionbase.v2.engine.service.ddl.AliasDeleteRequest",

        // V2 metadata
        "com.kakao.actionbase.v2.engine.metadata.StorageType",

        // V2 query
        "com.kakao.actionbase.v2.engine.sql.ScanFilter",
    )

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
        "V2Compat",
    )
}
