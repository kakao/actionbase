package actionbase

import org.gradle.api.Plugin
import org.gradle.api.Project

class SpringConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Apply plugins
        project.pluginManager.apply("actionbase.kotlin-conventions")
        project.pluginManager.apply("actionbase.reactor-conventions")

        project.pluginManager.apply("org.jetbrains.kotlin.plugin.spring")
        project.pluginManager.apply("org.springframework.boot")
        project.pluginManager.apply("io.spring.dependency-management")

        // Configure dependencies
        configureDependencies(project)
    }

    private fun configureDependencies(project: Project) {
        val dependencies = project.dependencies

        // Spring Boot dependencies
        dependencies.add("implementation", "org.springframework.boot:spring-boot-starter-actuator")
        dependencies.add("implementation", "org.springframework.boot:spring-boot-starter-validation")
        dependencies.add("implementation", "org.springframework.data:spring-data-commons")

        // Development only dependencies
        dependencies.add("developmentOnly", "org.springframework.boot:spring-boot-devtools")

        // Test dependencies with exclusions
        dependencies.add("testImplementation", "org.springframework.boot:spring-boot-starter-test")
    }
}
