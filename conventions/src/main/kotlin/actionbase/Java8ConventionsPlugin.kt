package actionbase

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

import actionbase.dependencies.Dependencies

class Java8ConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Apply plugins
        project.pluginManager.apply("actionbase.base-conventions")
        project.pluginManager.apply("java-library")

        // Configure dependencies
        configureDependencies(project)

        // Configure Java 8 toolchain
        configureJava8(project)
    }

    private fun configureDependencies(project: Project) {
        val dependencies = project.dependencies

        dependencies.add("compileOnly", Dependencies.Immutables.VALUE_ANNOTATIONS)
        dependencies.add("annotationProcessor", Dependencies.Immutables.VALUE)

        dependencies.add("implementation", Dependencies.Jackson.JACKSON_CORE)
        dependencies.add("implementation", Dependencies.Jackson.JACKSON_DATABIND)
        dependencies.add("implementation", Dependencies.Jackson.JACKSON_ANNOTATIONS)

        // Java 8 uses slf4j-simple for logging (Java 17 uses logback)
        dependencies.add("testImplementation", Dependencies.Logging.SLF4J_SIMPLE)
    }

    private fun configureJava8(project: Project) {
        project.extensions.configure(JavaPluginExtension::class.java) {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(8))
            }
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }
    }
}
