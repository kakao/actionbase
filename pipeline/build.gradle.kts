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

dependencies {
    implementation(project(":codec-java"))

    implementation(Dependencies.Jackson.JACKSON_YAML)

    // Spark ML algorithms (ALS, etc.) used by built-in Transform steps. compileOnly because spark-submit provides it.
    compileOnly(Dependencies.Spark.MLLIB)
    testImplementation(Dependencies.Spark.MLLIB)
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
