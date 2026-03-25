/**
 * Boundary check configuration.
 * Exact package match. Unmatched = error.
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

    val V3 = listOf(
        "com.kakao.actionbase.server.api.graph.v3",
        "com.kakao.actionbase.server.api.graph.v3.datastore",
        "com.kakao.actionbase.server.api.graph.v3.datastore.hbase",
        "com.kakao.actionbase.server.api.graph.v3.metadata",
        "com.kakao.actionbase.engine.service",
    )

    val ADAPTER = listOf(
        "com.kakao.actionbase.v2.engine.v3",
        "com.kakao.actionbase.v2.engine.v3.edge",
    )

    val V2 = listOf(
        // V2 API controllers
        "com.kakao.actionbase.server.api.graph.v2.admin",
        "com.kakao.actionbase.server.api.graph.v2.edge",
        "com.kakao.actionbase.server.api.graph.v2.metastore",
        "com.kakao.actionbase.server.api.graph.v2.query",
        "com.kakao.actionbase.server.api.graph.v2.service",
        "com.kakao.actionbase.server.api.graph.v2.storage",

        // V2 engine
        "com.kakao.actionbase.v2.engine",
        "com.kakao.actionbase.v2.engine.alias",
        "com.kakao.actionbase.v2.engine.audit",
        "com.kakao.actionbase.v2.engine.cdc",
        "com.kakao.actionbase.v2.engine.client.kafka",
        "com.kakao.actionbase.v2.engine.client.kafka.impl",
        "com.kakao.actionbase.v2.engine.client.web",
        "com.kakao.actionbase.v2.engine.client.web.impl",
        "com.kakao.actionbase.v2.engine.compat",
        "com.kakao.actionbase.v2.engine.consistency",
        "com.kakao.actionbase.v2.engine.dml",
        "com.kakao.actionbase.v2.engine.edge",
        "com.kakao.actionbase.v2.engine.entity",
        "com.kakao.actionbase.v2.engine.entity.deprecated",
        "com.kakao.actionbase.v2.engine.exception",
        "com.kakao.actionbase.v2.engine.fake",
        "com.kakao.actionbase.v2.engine.indexed",
        "com.kakao.actionbase.v2.engine.label",
        "com.kakao.actionbase.v2.engine.label.hbase",
        "com.kakao.actionbase.v2.engine.label.metastore",
        "com.kakao.actionbase.v2.engine.label.mixin",
        "com.kakao.actionbase.v2.engine.label.nil",
        "com.kakao.actionbase.v2.engine.label.scan",
        "com.kakao.actionbase.v2.engine.label.slatedb",
        "com.kakao.actionbase.v2.engine.metadata",
        "com.kakao.actionbase.v2.engine.metadata.sync",
        "com.kakao.actionbase.v2.engine.metastore",
        "com.kakao.actionbase.v2.engine.migration",
        "com.kakao.actionbase.v2.engine.migration.tasks",
        "com.kakao.actionbase.v2.engine.producer",
        "com.kakao.actionbase.v2.engine.query",
        "com.kakao.actionbase.v2.engine.query.compat",
        "com.kakao.actionbase.v2.engine.service.ddl",
        "com.kakao.actionbase.v2.engine.sql",
        "com.kakao.actionbase.v2.engine.storage",
        "com.kakao.actionbase.v2.engine.storage.druid",
        "com.kakao.actionbase.v2.engine.storage.hbase",
        "com.kakao.actionbase.v2.engine.storage.hbase.impl",
        "com.kakao.actionbase.v2.engine.storage.jdbc",
        "com.kakao.actionbase.v2.engine.storage.local",
        "com.kakao.actionbase.v2.engine.storage.nil",
        "com.kakao.actionbase.v2.engine.storage.slatedb",
        "com.kakao.actionbase.v2.engine.test",
        "com.kakao.actionbase.v2.engine.test.cdc",
        "com.kakao.actionbase.v2.engine.test.dsl",
        "com.kakao.actionbase.v2.engine.test.wal",
        "com.kakao.actionbase.v2.engine.util",
        "com.kakao.actionbase.v2.engine.wal",
        "com.kakao.actionbase.v2.engine.warmup",

        // V2 core
        "com.kakao.actionbase.v2.core.code",
        "com.kakao.actionbase.v2.core.code.hbase",
        "com.kakao.actionbase.v2.core.edge",
        "com.kakao.actionbase.v2.core.metadata",
        "com.kakao.actionbase.v2.core.metadata.common",
        "com.kakao.actionbase.v2.core.types",
        "com.kakao.actionbase.v2.core.util",
    )

    val SHARED = listOf(
        // core
        "com.kakao.actionbase.core",
        "com.kakao.actionbase.core.codec",
        "com.kakao.actionbase.core.edge",
        "com.kakao.actionbase.core.edge.mapper",
        "com.kakao.actionbase.core.edge.mutation",
        "com.kakao.actionbase.core.edge.payload",
        "com.kakao.actionbase.core.edge.record",
        "com.kakao.actionbase.core.metadata",
        "com.kakao.actionbase.core.metadata.common",
        "com.kakao.actionbase.core.metadata.payload",
        "com.kakao.actionbase.core.state",
        "com.kakao.actionbase.core.storage",
        "com.kakao.actionbase.core.types",
        "com.kakao.actionbase.core.v2.edge",
        "com.kakao.actionbase.core.v2.metadata",
        "com.kakao.actionbase.core.v2.metadata.common",

        // core-java
        "com.kakao.actionbase.core.java",
        "com.kakao.actionbase.core.java.codec",
        "com.kakao.actionbase.core.java.codec.common",
        "com.kakao.actionbase.core.java.codec.common.hbase",
        "com.kakao.actionbase.core.java.compat.v2",
        "com.kakao.actionbase.core.java.context",
        "com.kakao.actionbase.core.java.dataframe",
        "com.kakao.actionbase.core.java.dataframe.column",
        "com.kakao.actionbase.core.java.dataframe.row",
        "com.kakao.actionbase.core.java.edge",
        "com.kakao.actionbase.core.java.edge.index",
        "com.kakao.actionbase.core.java.hbase",
        "com.kakao.actionbase.core.java.information",
        "com.kakao.actionbase.core.java.inspect",
        "com.kakao.actionbase.core.java.jackson",
        "com.kakao.actionbase.core.java.logo",
        "com.kakao.actionbase.core.java.metadata",
        "com.kakao.actionbase.core.java.metadata.v2",
        "com.kakao.actionbase.core.java.metadata.v2.common",
        "com.kakao.actionbase.core.java.metadata.v3",
        "com.kakao.actionbase.core.java.metadata.v3.common",
        "com.kakao.actionbase.core.java.payload",
        "com.kakao.actionbase.core.java.payload.cdc",
        "com.kakao.actionbase.core.java.pipeline",
        "com.kakao.actionbase.core.java.pipeline.common",
        "com.kakao.actionbase.core.java.query",
        "com.kakao.actionbase.core.java.state",
        "com.kakao.actionbase.core.java.state.base",
        "com.kakao.actionbase.core.java.types",
        "com.kakao.actionbase.core.java.types.common",
        "com.kakao.actionbase.core.java.util",
        "com.kakao.actionbase.core.java.vertex",

        // engine abstractions
        "com.kakao.actionbase.engine",
        "com.kakao.actionbase.engine.binding",
        "com.kakao.actionbase.engine.binding.datastore.hbase",
        "com.kakao.actionbase.engine.context",
        "com.kakao.actionbase.engine.datastore",
        "com.kakao.actionbase.engine.datastore.hbase.admin",
        "com.kakao.actionbase.engine.datastore.impl",
        "com.kakao.actionbase.engine.experiments.hbase",
        "com.kakao.actionbase.engine.experiments.reactor",
        "com.kakao.actionbase.engine.metadata",
        "com.kakao.actionbase.engine.storage",
        "com.kakao.actionbase.engine.storage.hbase",
        "com.kakao.actionbase.engine.storage.memory",
        "com.kakao.actionbase.engine.util",

        // server infra
        "com.kakao.actionbase.server",
        "com.kakao.actionbase.server.api",
        "com.kakao.actionbase.server.api.check",
        "com.kakao.actionbase.server.api.exception",
        "com.kakao.actionbase.server.api.graph",
        "com.kakao.actionbase.server.auth",
        "com.kakao.actionbase.server.client.kafka",
        "com.kakao.actionbase.server.client.web",
        "com.kakao.actionbase.server.configuration",
        "com.kakao.actionbase.server.configuration.initializer",
        "com.kakao.actionbase.server.configuration.resolver",
        "com.kakao.actionbase.server.filter",
        "com.kakao.actionbase.server.filter.model",
        "com.kakao.actionbase.server.payload",
        "com.kakao.actionbase.server.service.devtools",
        "com.kakao.actionbase.server.service.devtools.models",
        "com.kakao.actionbase.server.service.devtools.util",
        "com.kakao.actionbase.server.test",
        "com.kakao.actionbase.server.util",

        // test
        "com.kakao.actionbase.test",
    )

    /** version name → package list */
    val VERSIONS = linkedMapOf(
        "v3" to V3,
        "adapter" to ADAPTER,
        "v2" to V2,
        "shared" to SHARED,
    )

    /** Leak rules: from, to, description */
    val LEAKS = listOf(Triple("v3", "v2", "should go through adapter"))

    /** Bridge rules: from, to */
    val BRIDGES = listOf("v3" to "adapter", "adapter" to "v2")
}
