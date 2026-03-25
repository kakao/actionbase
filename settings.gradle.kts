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

    "tools:v2v3-boundary-check",
)
