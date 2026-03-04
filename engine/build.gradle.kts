import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion

import actionbase.dependencies.Dependencies

plugins {
    id("actionbase.kotlin-conventions")
    id("actionbase.reactor-conventions")
    `java-test-fixtures`
}

// Override to Java 25 for FFI support
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

// The kotlin-conventions plugin sets targetCompatibility=17, which sets org.gradle.jvm.version=17
// on all configurations. Any dependency requiring JVM 24+ needs this override.
configurations.matching { it.isCanBeResolved }.configureEach {
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

ext["jvmArgs"] = listOf("--add-opens=java.base/java.nio=ALL-UNNAMED")

tasks {
    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

dependencies {
    // core
    implementation(project(":codec-java"))
    implementation(project(":core"))
    implementation(Dependencies.LZ4.LZ4)
    implementation(Dependencies.Cache.CAFFEINE)
    implementation(Dependencies.Validation.JAKARTA_VALIDATION_API)

    // reactor
    implementation(Dependencies.Reactor.CORE)
    implementation(Dependencies.Reactor.KOTLIN_EXTENSIONS)

    // json
    implementation(Dependencies.Jackson.JACKSON_KOTLIN)

    // logging
    implementation(Dependencies.Logging.SLF4J_API)
    implementation(Dependencies.Logging.LOGBACK_CLASSIC)

    // SlateDB (Maven Central)
    implementation("io.slatedb:slatedb:0.11.0")

    // HBase
    implementation(Dependencies.HBase.CLIENT)
    implementation(Dependencies.HBase.MAPREDUCE)
    implementation(Dependencies.HBase.HGRAPHDB) {
        exclude("com.google.cloud.bigtable")
        exclude("org.apache.hbase")
    }

    // kafka
    implementation(Dependencies.Kafka.CLIENTS)

    // RDB
    implementation(Dependencies.DatabasePool.HIKARICP)
    implementation(Dependencies.Exposed.CORE)
    implementation(Dependencies.Exposed.JAVA_TIME)
    implementation(Dependencies.Exposed.DAO)
    implementation(Dependencies.Exposed.JDBC)
    runtimeOnly(Dependencies.Database.H2)

    // test
    testImplementation(Dependencies.Testing.KOTEST_ASSERTIONS_CORE)
    testImplementation(Dependencies.Testing.KOTEST_RUNNER_JUNIT5)
    testImplementation(Dependencies.Reactor.TEST)
    testImplementation(Dependencies.Testing.MOCKK)
    testImplementation(Dependencies.Testing.BLOCKHOUND)

    testImplementation(testFixtures(project(":core")))

    // for testFixtures
    testFixturesApi(Dependencies.Reactor.TEST)
    testFixturesApi(platform(Dependencies.Testing.JUNIT_BOM))
    testFixturesApi(Dependencies.Testing.JUPITER_API)
    testFixturesApi(Dependencies.Jackson.JACKSON_CORE)
    testFixturesApi(Dependencies.Jackson.JACKSON_DATABIND)
    testFixturesApi(Dependencies.Jackson.JACKSON_YAML)
    testFixturesApi(Dependencies.Jackson.JACKSON_KOTLIN)
    testFixturesApi(kotlin("test"))

    testFixturesApi(dependencies.platform(Dependencies.Testing.JUNIT_BOM))
    testFixturesApi(Dependencies.Testing.JUPITER_API)
    testFixturesApi(Dependencies.Testing.JUPITER_ENGINE)
    testFixturesApi(Dependencies.Testing.JUPITER_PARAMS)

    // JUnit Platform
    testFixturesApi(Dependencies.Testing.PLATFORM_LAUNCHER)
    testFixturesApi(Dependencies.Testing.PLATFORM_ENGINE)

    testFixturesApi(Dependencies.HBase.TESTING_UTIL)
    testFixturesRuntimeOnly(Dependencies.Logging.RELOAD4J)
}

tasks.withType<Test>().all {
    jvmArgs =
        listOf(
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
            "--enable-native-access=ALL-UNNAMED",
        )

    if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_13)) {
        jvmArgs("-XX:+AllowRedefinitionToAddDeleteMethods")
    }

    // BlockHound uses Byte Buddy which does not officially support Java 25.
    // The experimental flag allows it to run on unsupported JVM versions.
    systemProperty("reactor.blockhound.shaded.net.bytebuddy.experimental", "true")
}
