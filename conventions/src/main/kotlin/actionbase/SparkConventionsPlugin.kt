package actionbase

import org.gradle.api.Plugin
import org.gradle.api.Project

import actionbase.dependencies.Dependencies

class SparkConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Apply plugins
        project.pluginManager.apply("actionbase.base-conventions")
        project.pluginManager.apply("actionbase.java8-conventions")
        project.pluginManager.apply("scala")

        project.pluginManager.apply("com.github.johnrengelman.shadow")

        // Configure dependencies
        configureDependencies(project)
    }

    private fun configureDependencies(project: Project) {
        val dependencies = project.dependencies

        // Spark dependencies
        dependencies.add("compileOnly", Dependencies.Spark.SQL)
        dependencies.add("compileOnly", Dependencies.Spark.STREAMING)

        // HBase
        dependencies.add("implementation", Dependencies.HBase.CLIENT)
        dependencies.add("implementation", Dependencies.HBase.MAPREDUCE)

        dependencies.add("testImplementation", Dependencies.Spark.SQL)
        dependencies.add("testImplementation", Dependencies.Spark.STREAMING)
    }
}
