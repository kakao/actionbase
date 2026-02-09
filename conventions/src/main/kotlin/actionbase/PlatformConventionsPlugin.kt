package actionbase

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlatformExtension

class PlatformConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Apply plugins
        project.pluginManager.apply("java-platform")
        project.pluginManager.apply("maven-publish")

        // Configure Java Platform
        configureJavaPlatform(project)
    }

    private fun configureJavaPlatform(project: Project) {
        project.extensions.configure(JavaPlatformExtension::class.java) {
            allowDependencies()
        }
    }
}
