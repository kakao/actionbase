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

    "pipeline",

    "tools:v3v2-boundary-check",
)
