import actionbase.dependencies.Dependencies

group = "com.kakao.actionbase"
version = "0.2.0-SNAPSHOT"

plugins {
    id("actionbase.spark-conventions")

    id("maven-publish")
}

tasks.withType<JavaExec> {
    jvmArgs = listOf("--add-exports=java.base/sun.nio.ch=ALL-UNNAMED")
}

// Run a Workflow YAML through EmbeddedRunner without a separate spark-submit.
//   ./gradlew :pipeline:runWorkflow --args="pipeline/workflows/spark-pi.yaml"
//
// `env:` values can be overridden by passing extra `KEY=VALUE` args after the YAML path:
//   ./gradlew :pipeline:runWorkflow --args="pipeline/workflows/spark-pi.yaml SAMPLES=100000"
//
// Working directory is the repo root so paths in `--args=...` are interpreted
// the same as if the user typed them from where they invoke gradle.
//
// Spark is `compileOnly` for the module (a real `spark-submit` runtime provides it),
// so the embedded runner pulls Spark from the test classpath where it is `testImplementation`.
tasks.register<JavaExec>("runWorkflow") {
    group = "application"
    description = "Run a Workflow YAML through the EmbeddedRunner."
    mainClass.set("com.kakao.actionbase.pipeline.runner.EmbeddedRunner")
    classpath = sourceSets["test"].runtimeClasspath
    workingDir = project.rootDir
    systemProperty("spark.master", "local[*]")
    systemProperty("spark.driver.bindAddress", "127.0.0.1")
    systemProperty("spark.ui.enabled", "false")
}

dependencies {
    implementation(project(":codec-java"))

    implementation(Dependencies.Jackson.JACKSON_YAML)
}

publishing {
    publications {
        create<MavenPublication>("mavenScala") {
            from(components["java"])
            groupId = "com.kakao.actionbase"
            artifactId = "pipeline_2.12"
        }
    }

    repositories {
        maven {
            setUrl(
                provider {
                    val isReleaseVersion = !version.toString().endsWith("-SNAPSHOT")
                    val envVar = if (isReleaseVersion) "MAVEN_RELEASE_URL" else "MAVEN_SNAPSHOT_URL"
                    val url = System.getenv(envVar)

                    requireNotNull(url) { "$envVar environment variable is not set" }
                    url
                },
            )
        }
    }
}
