plugins {
    // Auto-provisions required JDKs for Dockerfile-based builds (not needed for Jib).
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "actionbase"

includeBuild("conventions")

include(
    "platform",

    // codec-java, core-java should be integrated to core later.
    "codec-java",
    "core-java",

    "core",
    "engine",
    "server",
)
