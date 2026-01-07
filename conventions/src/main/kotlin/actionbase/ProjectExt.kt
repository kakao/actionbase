package actionbase

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern
import org.gradle.api.Project

data class BuildParameter(val buildTime: String, val artifactName: String)

fun Project.genBuildParam(): BuildParameter {
    val buildTime = nowKST()
    val artifactName = project.findProperty("artifactName") as? String ?: generateApplicationArtifactName(buildTime)
    return BuildParameter(buildTime = buildTime, artifactName = artifactName)
}

private fun nowKST(): String {
    val formatter = DateTimeFormatter
        .ofPattern("yyyyMMdd'T'HHmmss")
        .withZone(ZoneId.of("Asia/Seoul"))
    return formatter.format(Instant.now())
}

private fun Project.generateApplicationArtifactName(timestamp: String = nowKST()): String {
    val projectVersion = project.version.toString()
    val branchName: String = gitBranchName()
    return if (
        !projectVersion.contains("SNAPSHOT")
    ) {
        "v$projectVersion"
    } else {
        val branchNamePart = when {
            branchName.startsWith("release/") -> {
                // Remove release/ prefix
                branchName.removePrefix("release/")
            }
            branchName.startsWith("feature/") -> {
                // Extract Jira key from feature/ branch
                val jiraKey = Pattern.compile("[A-Z]+-\\d+")
                val matcher = jiraKey.matcher(branchName)
                if (matcher.find()) {
                    matcher.group()
                } else {
                    branchName
                }
            }
            branchName == "main" -> {
                "main"
            }
            else -> {
                // throw IllegalArgumentException("Unsupported branch: $branchName. Only main, release/, feature/ branches are allowed.")
                "unknown"
            }
        }
        val commitHash: String = gitCommitHash()
        "${projectVersion}_${timestamp}_${branchNamePart}_${commitHash}"
    }
}

private fun gitBranchName(): String {
    return "git rev-parse --abbrev-ref HEAD".runCommand() ?: "unknown"
}

private fun gitCommitHash(): String {
    return "git rev-parse --short HEAD".runCommand() ?: "unknown-commit"
}

private fun String.runCommand(): String? = try {
    Runtime.getRuntime()
        .exec(this)
        .inputStream
        .bufferedReader()
        .use {
            it.readText().trim()
        }
} catch (e: Exception) {
    e.printStackTrace()
    null
}

