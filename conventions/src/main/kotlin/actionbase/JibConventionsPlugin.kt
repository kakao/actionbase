package actionbase

import com.google.cloud.tools.jib.gradle.JibExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class JibConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Apply Jib plugin
        project.pluginManager.apply("com.google.cloud.tools.jib")

        // Generate build parameter and store in ext
        val buildParam = project.genBuildParam()
        project.extensions.extraProperties["buildParam"] = buildParam

        // Configure Jib with common settings
        val jvmArgs =
            listOf(
                "-Dsun.net.inetaddr.ttl=0",
                "-Dnetworkaddress.cache.ttl=0",
                "-Dnetworkaddress.cache.negative.ttl=0",
                "--add-opens",
                "java.base/java.nio=ALL-UNNAMED",
                "--add-exports",
                "java.security.jgss/sun.security.krb5=ALL-UNNAMED"
            )
        project.extensions.extraProperties["jvmArgs"] = jvmArgs

        project.extensions.configure(JibExtension::class.java) {
            from {
                image = "eclipse-temurin:17-jdk-alpine"
            }

            container {
                jvmFlags = jvmArgs
                environment = mapOf("TZ" to "Asia/Seoul")
                user = "nobody"
                ports = listOf("8080")
                appRoot = "/app"
                containerizingMode = "exploded"
            }
        }
    }
}

