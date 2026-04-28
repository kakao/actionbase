import java.io.File

import actionbase.dependencies.Dependencies
import actionbase.dependencies.Versions

plugins {
    scala
    `java-library`
    id("actionbase.java8-conventions")
}

// integrationTest sourceSet — separates long-running Spark / HBase
// container tests from the plain `test` task so `:pipeline-spark:test` stays fast.
sourceSets {
    create("integrationTest") {
        // The Scala plugin already contributes the default
        // `src/integrationTest/{scala,resources}` directories for a custom
        // source set, so do not re-register them here (would cause
        // `processIntegrationTestResources` to fail with duplicate entries).
        compileClasspath += sourceSets["main"].output + sourceSets["test"].output +
            configurations["testCompileClasspath"]
        runtimeClasspath += output + compileClasspath +
            sourceSets["main"].runtimeClasspath + sourceSets["test"].runtimeClasspath +
            configurations["testRuntimeClasspath"]
    }
}

val integrationTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations["testImplementation"])
}
val integrationTestRuntimeOnly: Configuration by configurations.getting {
    extendsFrom(configurations["testRuntimeOnly"])
}

repositories {
    mavenCentral()
}

val scalaBinary = Versions.SCALA_BINARY
val scalaVersion = Versions.SCALA
val sparkVersion = Versions.SPARK
val hbaseVersion = Versions.HBASE
val hadoopVersion = "2.10.0"
val circeVersion = "0.11.1"
val typesafeConfigVersion = "1.3.3"
val scalaLoggingVersion = "3.9.0"
val jacksonVersion = "2.14.2"

dependencies {
    implementation(project(":codec-java"))
    implementation(project(":core-java"))

    // Scala — `api` because public method signatures reference scala types.
    api("org.scala-lang:scala-library:$scalaVersion")

    // Spark — compileOnly to avoid packaging; provided by the Spark cluster.
    compileOnly(Dependencies.Spark.CORE)
    compileOnly(Dependencies.Spark.SQL)
    testImplementation(Dependencies.Spark.CORE)
    testImplementation(Dependencies.Spark.SQL)

    // Hadoop — `api` for hadoop-common because `Configuration` appears in
    // public trait/class signatures (HBaseService, step04 loader helpers).
    api("org.apache.hadoop:hadoop-common:$hadoopVersion")
    implementation("org.apache.hadoop:hadoop-mapreduce-client-core:$hadoopVersion")
    implementation("org.apache.hadoop:hadoop-distcp:$hadoopVersion")

    // HBase — `api` for the shaded client because HBase types (TableName,
    // Connection, Admin, ...) appear in public signatures (HBaseService,
    // HBaseBulkLoader). Bulk-load uses HFileOutputFormat2 + BulkLoadHFiles
    // from the shaded mapreduce artifact.
    api(Dependencies.HBase.CLIENT)
    implementation(Dependencies.HBase.MAPREDUCE)

    // Typesafe Config (HOCON)
    implementation("com.typesafe:config:$typesafeConfigVersion")

    // Circe — JSON Decoder/Encoder for AbService trait.
    implementation("io.circe:circe-core_$scalaBinary:$circeVersion")
    implementation("io.circe:circe-generic_$scalaBinary:$circeVersion")
    implementation("io.circe:circe-parser_$scalaBinary:$circeVersion")

    // Jackson — Scala module for case-class (de)serialization. databind
    // pulls jackson-core and jackson-annotations transitively.
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-scala_$scalaBinary:$jacksonVersion")

    // Logging — `api` because several public production classes extend
    // `com.typesafe.scalalogging.StrictLogging`.
    api("com.typesafe.scala-logging:scala-logging_$scalaBinary:$scalaLoggingVersion")
    implementation(Dependencies.Logging.SLF4J_API)

    // scala-collection-compat — backports `scala.jdk.CollectionConverters`
    // and `scala.util.Using` to Scala 2.12.
    implementation("org.scala-lang.modules:scala-collection-compat_$scalaBinary:2.7.0")

    // Test
    testImplementation("org.scalatest:scalatest_$scalaBinary:3.2.10")
    testImplementation(Dependencies.HBase.TESTING_UTIL)

    // ScalaTest <-> JUnit Platform wiring: the helmethair runner provides a
    // JUnit 5 TestEngine with id `scalatest`, letting Gradle's
    // `useJUnitPlatform` discover ScalaTest suites (AnyFunSuite, etc.).
    testRuntimeOnly("co.helmethair:scalatest-junit-runner:0.2.0")

    // testcontainers — 1.19.8 is the last release supporting JDK 8.
    testImplementation("org.testcontainers:testcontainers:1.19.8")
}

tasks.withType<ScalaCompile>().configureEach {
    scalaCompileOptions.additionalParameters =
        listOf(
            "-target:jvm-1.8",
            "-deprecation",
            "-feature",
        )
}

val integrationTest by tasks.registering(Test::class) {
    description = "Runs integration tests (Spark local + testcontainers HBase)."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    shouldRunAfter(tasks.test)

    // The HBaseContainer fixture uses fixed 1:1 host-container port bindings
    // because the HBase master advertises the region server as
    // `localhost:16020`; random host ports would break client reachability.
    // Two suites using `HBaseContainer` cannot run simultaneously.
    maxParallelForks = 1
    forkEvery = 0L

    // testcontainers Docker socket resolution on macOS:
    //   - Docker Desktop's CLI socket (/var/run/docker.sock) is a proxy that
    //     does not implement /info; testcontainers' auto-detection picks it
    //     first and fails with HTTP 400.
    //   - The actual Engine API lives at
    //     ~/Library/Containers/com.docker.docker/Data/docker.raw.sock.
    val userDockerHost = System.getenv("DOCKER_HOST")
    val defaultRawSock =
        System.getProperty("user.home") +
            "/Library/Containers/com.docker.docker/Data/docker.raw.sock"
    val dockerHost =
        when {
            !userDockerHost.isNullOrBlank() -> userDockerHost
            File(defaultRawSock).exists() -> "unix://$defaultRawSock"
            else -> null
        }
    if (dockerHost != null) {
        environment("DOCKER_HOST", dockerHost)
    }
    // Docker Desktop 29.x requires API version >= 1.44, but docker-java 3.3
    // (shipped with testcontainers 1.19.8) negotiates an older default.
    environment("DOCKER_API_VERSION", "1.44")
    systemProperty("api.version", "1.44")
    // ryuk (testcontainers' reaper) bind-mounts the Docker socket from the
    // host. Docker Desktop only allows bind-mounting the CLI-proxy socket,
    // not docker.raw.sock.
    environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform {
        includeEngines("scalatest")
    }
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
        showCauses = true
        showExceptions = true
        showStackTraces = true
    }
    // Exclude compiled Scala objects (`*$`) from JUnit engine discovery.
    exclude("**/*$*")
    jvmArgs(
        "-Xmx4g",
        "-XX:MaxMetaspaceSize=2g",
        "-Djava.net.preferIPv4Stack=true",
    )
}
