package actionbase

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

import actionbase.dependencies.Dependencies

class KotlinConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Apply plugins
        project.pluginManager.apply("actionbase.base-conventions")
        project.pluginManager.apply("org.jetbrains.kotlin.jvm")

        // Configure Java 17 toolchain
        configureJava17(project)

        // Configure Kotlin compiler options
        configureKotlinCompiler(project)

        // Configure dependencies
        configureDependencies(project)
    }

    private fun configureJava17(project: Project) {
        project.extensions.configure(JavaPluginExtension::class.java) {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(17))
            }
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }

//
    private fun configureKotlinCompiler(project: Project) {
        project.tasks.withType(KotlinCompile::class.java).configureEach {
            kotlinOptions {
                jvmTarget = "17"
                freeCompilerArgs = listOf("-Xjsr305=strict")
            }
        }
    }

    private fun configureDependencies(project: Project) {
        val dependencies = project.dependencies

        // Kotlin standard dependencies
        dependencies.add("implementation", Dependencies.Kotlin.STDLIB)
        dependencies.add("implementation", Dependencies.Kotlin.REFLECT)

        // Jackson Kotlin support
        dependencies.add("implementation", Dependencies.Jackson.JACKSON_KOTLIN)

        // Kotlin test dependencies
        dependencies.add("testImplementation", project.kotlin("test"))
        dependencies.add("testImplementation", project.kotlin("test-junit5"))
        dependencies.add("testImplementation", Dependencies.Testing.MOCKK)

        dependencies.add("testImplementation", Dependencies.Logging.LOGBACK_CLASSIC)
    }

    // Helper function to improve readability when adding Kotlin dependencies
    private fun Project.kotlin(module: String): String = "org.jetbrains.kotlin:kotlin-$module"
}
