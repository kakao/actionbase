plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.ow2.asm:asm:9.7.1")
    implementation("org.ow2.asm:asm-tree:9.7.1")
}

application {
    mainClass.set("BoundaryCheckKt")
}

tasks.named<JavaExec>("run") {
    dependsOn(":server:classes", ":engine:classes", ":core:classes", ":core-java:classes")
    args = listOf(rootProject.projectDir.absolutePath) +
        (project.findProperty("verbose")?.let { listOf("--verbose") } ?: emptyList())
}
