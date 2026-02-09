package actionbase

import actionbase.dependencies.Dependencies
import actionbase.tasks.GenerateCodeStyleTask
import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.external.javadoc.StandardJavadocDocletOptions

class BaseConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Apply plugins
        project.pluginManager.apply("java")
        project.pluginManager.apply("com.diffplug.spotless")

        // Set version
        project.version = project.rootProject.version

        // Configure repositories
        project.repositories.mavenCentral()

        // Configure dependencies
        configureDependencies(project)

        // Configure Java
        configureJava(project)

        // Configure tasks
        configureTasks(project)

        // Configure Spotless
        configureSpotless(project)
    }

    private fun configureDependencies(project: Project) {
        val dependencies = project.dependencies

        // logging
        dependencies.add("implementation", Dependencies.Logging.SLF4J_API)

        // JUnit Jupiter
        dependencies.add("testImplementation", dependencies.platform(Dependencies.Testing.JUNIT_BOM))
        dependencies.add("testImplementation", Dependencies.Testing.JUPITER_API)
        dependencies.add("testImplementation", Dependencies.Testing.JUPITER_ENGINE)
        dependencies.add("testImplementation", Dependencies.Testing.JUPITER_PARAMS)

        // JUnit Platform
        dependencies.add("testImplementation", Dependencies.Testing.PLATFORM_LAUNCHER)
        dependencies.add("testImplementation", Dependencies.Testing.PLATFORM_ENGINE)
    }

    private fun configureJava(project: Project) {
        project.extensions.configure(JavaPluginExtension::class.java) {
            // Sources and Javadoc JARs are only needed for CI/publishing.
            // Skipping locally saves ~seconds per module during compilation.
            if (System.getenv("CI") != null) {
                withSourcesJar()
                withJavadocJar()
            }
        }
    }

    private fun configureTasks(project: Project) {
        project.tasks.withType(JavaCompile::class.java).configureEach {
            options.encoding = "UTF-8"
        }

        project.tasks.withType(Test::class.java).configureEach {
            useJUnitPlatform()

            testLogging {
                events = setOf(TestLogEvent.FAILED, TestLogEvent.SKIPPED)
                exceptionFormat = TestExceptionFormat.FULL
                showStandardStreams = false
            }

            maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { cores -> cores > 0 } ?: 1
        }

        project.tasks.withType(Javadoc::class.java).configureEach {
            options.let { options ->
                (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
            }
            isFailOnError = false
        }
    }

    private fun configureSpotless(project: Project) {
        project.extensions.configure(SpotlessExtension::class.java) {
            // Decouple spotlessCheck from the build lifecycle.
            // Run ./gradlew spotlessApply before pushing. CI enforces via spotlessCheck.
            setEnforceCheck(false)

            java {
                googleJavaFormat()
                importOrder(*GenerateCodeStyleTask.getJavaImportsOrder())
                removeUnusedImports()
                target("**/*.java")
                targetExclude("**/generated/**") // ✅ Exclude auto-generated folders
                trimTrailingWhitespace()
                endWithNewline()
            }

            kotlin {
                ktlint()
                target("**/*.kt", "**/*.kts")
                trimTrailingWhitespace()
                endWithNewline()
            }

            // Scala configuration is commented out in the original file
            // spotless.scala {
            //     scalafmt().configFile(project.rootProject.scalaFmtConfig)
            //     trimTrailingWhitespace()
            //     endWithNewline()
            // }
        }
    }
}
