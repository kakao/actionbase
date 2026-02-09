package actionbase

import org.gradle.api.Plugin
import org.gradle.api.Project

import actionbase.dependencies.Dependencies

class ReactorConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Apply plugins
        project.pluginManager.apply("actionbase.base-conventions")

        // Configure dependencies
        configureDependencies(project)
    }

    private fun configureDependencies(project: Project) {
        val dependencies = project.dependencies

        // Reactor dependencies
        dependencies.add("implementation", Dependencies.Reactor.CORE)
        dependencies.add("implementation", Dependencies.Reactor.REACTIVE_STREAMS)

        // Reactor test dependencies
        dependencies.add("testImplementation", Dependencies.Reactor.TEST)
    }
}
