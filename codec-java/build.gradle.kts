import actionbase.dependencies.Dependencies

group = "com.kakao.actionbase"
version = "0.0.1-SNAPSHOT"

plugins {
    id("actionbase.java8-conventions")

    id("maven-publish")
    `java-library`
}

java {
    withSourcesJar()
}

dependencies {
    implementation(Dependencies.LZ4.LZ4)
    implementation(Dependencies.Jackson.JACKSON_ANNOTATIONS_SPARK) // for Apache Spark 3.2.4
    implementation(Dependencies.Jackson.JACKSON_DATABIND_SPARK) // for Apache Spark 3.2.4

    testImplementation(Dependencies.Testing.JUPITER)
}

repositories {
    mavenCentral()
}

tasks.withType<Test> {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "com.kakao.actionbase"
            artifactId = "codec-java"
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
