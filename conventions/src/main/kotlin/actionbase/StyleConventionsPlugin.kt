package actionbase

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

import actionbase.tasks.GenerateCodeStyleTask

class StyleConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.logger.lifecycle("Applying style conventions...")

        project.tasks.register<GenerateCodeStyleTask>("generateCodeStyle")
    }
}
