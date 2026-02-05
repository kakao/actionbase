import actionbase.BuildParameter
import actionbase.dependencies.Dependencies

plugins {
    id("actionbase.kotlin-conventions")
    id("actionbase.reactor-conventions")
    id("actionbase.spring-conventions")
    id("actionbase.jib-conventions")
    `java-test-fixtures`

    id("com.gorylenko.gradle-git-properties") version "2.5.5"
}

dependencyManagement {
    dependencies {
        imports {
            mavenBom(Dependencies.Spring.CLOUD_DEPENDENCIES)
        }
    }
}

dependencies {
    // core
    implementation(project(":codec-java"))
    implementation(project(":core"))
    implementation(project(":engine"))

    // reactor
    implementation(Dependencies.Reactor.KOTLIN_EXTENSIONS)

    // json
    implementation(Dependencies.Jackson.JACKSON_KOTLIN)

    // spring
    implementation(Dependencies.Spring.BOOT_STARTER_WEBFLUX)
    implementation(Dependencies.Spring.DATA_COMMONS)
    implementation(Dependencies.Spring.BOOT_STARTER_VALIDATION)
    implementation(Dependencies.Reactor.CORE)

    // HBase
    implementation(Dependencies.HBase.CLIENT)
    implementation(Dependencies.HBase.MAPREDUCE)

    // RDB
    runtimeOnly(Dependencies.Database.MYSQL_CONNECTOR)

    // logging
    implementation(Dependencies.Logging.LOGBACK_CLASSIC)

    // kafka
    implementation(Dependencies.Spring.KAFKA)
    implementation(Dependencies.Reactor.KAFKA)

    // testFixtures
    testFixturesApi(platform(Dependencies.Testing.JUNIT_BOM))
    testFixturesApi(Dependencies.Testing.JUPITER_API)
    testFixturesApi(Dependencies.Spring.BOOT_STARTER_TEST)
    testFixturesApi(Dependencies.Spring.BOOT_STARTER_WEBFLUX)
}

gitProperties {
    dateFormat = "yyyy-MM-dd'T'HH:mmZ"
    dateFormatTimeZone = "UTC+9"
}

val buildParam = project.extensions.extraProperties["buildParam"] as BuildParameter
val ghcrImage = "ghcr.io/kakao/actionbase"

val ghcrUsername =
    System.getenv("GHCR_USERNAME")
        ?: System.getenv("GITHUB_ACTOR")
        ?: (findProperty("ghcr.username") as? String)

val ghcrPassword =
    System.getenv("GHCR_TOKEN")
        ?: System.getenv("GHCR_PAT")
        ?: System.getenv("GITHUB_TOKEN")
        ?: (findProperty("ghcr.token") as? String)

springBoot {
    buildInfo()
}

jib {
    to {
        image = ghcrImage
        tags = setOf(buildParam.artifactName)
        auth {
            username = ghcrUsername ?: "kakao"
            password = ghcrPassword ?: ""
        }
    }
    container {
        mainClass = "com.kakao.actionbase.server.ServerApplicationKt"
    }
}

tasks.named("jib") {
    doFirst {
        if (ghcrPassword.isNullOrBlank()) {
            throw GradleException(
                """
                |❌ GitHub Packages authentication credentials are missing.
                |
                |Please set authentication credentials using one of the following methods:
                |
                |1. Environment variable:
                |   export GHCR_TOKEN=your_personal_access_token
                |   or
                |   export GITHUB_TOKEN=your_personal_access_token
                |
                |2. Gradle property:
                |   ./gradlew :oss:server:jib -Pghcr.token=your_personal_access_token
                |
                |3. Add to ~/.gradle/gradle.properties:
                |   ghcr.token=your_personal_access_token
                |
                |To create a Personal Access Token:
                |1. Go to GitHub Settings > Developer settings > Personal access tokens > Tokens (classic)
                |2. Click "Generate new token (classic)"
                |3. Select "write:packages" and "read:packages" permissions
                |4. Generate the token and set it using one of the methods above
                """.trimMargin(),
            )
        }
        println("🐳 Building and pushing to: $ghcrImage")
        println("📦 Tag: ${buildParam.artifactName}")
    }
}
