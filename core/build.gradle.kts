import actionbase.dependencies.Dependencies

plugins {
    id("actionbase.java8-conventions")
    `java-test-fixtures`

    kotlin("jvm") // experimental (Java 8 + Kotlin)
}

dependencies {
    api(platform(project(":platform")))

    implementation(Dependencies.LZ4.LZ4)

    implementation(Dependencies.Jackson.JACKSON_KOTLIN)

    testImplementation(project(":core-java"))
    testImplementation(project(":codec-java"))
    testImplementation(Dependencies.Jackson.JACKSON_CBOR)
    testImplementation(Dependencies.Jackson.JACKSON_SMILE)
    testImplementation(kotlin("test"))

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
}
