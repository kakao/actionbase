package actionbase.tasks

import java.io.File

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

/**
 * Generates code style configuration files (.editorconfig and .scalafmt.conf)
 * This task is automatically executed before any spotless task
 */
open class GenerateCodeStyleTask : DefaultTask() {
    companion object {
        private const val SOURCE_FILE = "buildSrc/src/main/kotlin/actionbase/tasks/GenerateCodeStyleTask.kt"
        private const val WARNING_MESSAGE =
            "WARNING: This file is auto-generated. Any manual changes will be overwritten."
        private const val UPDATE_MESSAGE = "To modify import order, update $SOURCE_FILE"

        private val importOrder =
            listOf(
                "^", // static imports
                "com.kakao.actionbase.**",
                "com.kakao.**",
                "java.**",
                "javax.**",
                "scala.**",
                "kotlin.**",
                "org.**",
                "com.**",
                "*",
            )

        private const val IMPORT_COUNT = "999"
        private const val IMPORT_ON_DEMAND = ".*"

        private fun getIjImportsOrder(): String = importOrder.joinToString(",|,") { it }

        fun getJavaImportsOrder(): Array<String> =
            importOrder
                .filter { it != "^" && it != "*" }
                .map { it.removeSuffix(".**") }
                .toTypedArray()

        /**
         * Returns Java editor settings as a Map.
         */
        fun getJavaEditorConfigSettings(): Map<String, String> =
            mapOf(
                "ij_java_imports_layout" to getIjImportsOrder(),
                "ij_java_class_count_to_use_import_on_demand" to IMPORT_COUNT,
                "ij_java_names_count_to_use_import_on_demand" to IMPORT_COUNT,
                "ij_continuation_indent_size" to "4",
                "ij_java_space_before_method_parentheses" to "false",
                "ij_java_wrap_long_lines" to "true",
                "ij_java_keep_line_breaks" to "false",
            )

        /**
         * Returns Kotlin editor settings as a Map.
         */
        fun getKotlinEditorConfigSettings(): Map<String, String> =
            mapOf(
                "indent_size" to "4",
                "ij_kotlin_imports_layout" to getIjImportsOrder(),
                "ij_kotlin_allow_trailing_comma" to "true",
                "ij_kotlin_allow_trailing_comma_on_call_site" to "true",
                "ij_kotlin_packages_to_use_import_on_demand" to "",
                "ij_kotlin_name_count_to_use_star_import" to IMPORT_COUNT,
                "ij_kotlin_name_count_to_use_star_import_for_members" to IMPORT_COUNT,
            )

        /**
         * Returns Scala editor settings as a Map.
         */
        fun getScalaEditorConfigSettings(): Map<String, String> =
            mapOf(
                "# ij_scala_imports_layout" to getIjImportsOrder(),
                "ij_continuation_indent_size" to "2",
                "ij_scala_use_scala3_indentation_based_syntax" to "true",
            )

        private fun getScalafmtImportGroups(): String =
            importOrder
                .filter { it != "^" && it != "*" }
                .joinToString(",\n              ") {
                    "\"${it.replace(".", "\\.")}\""
                }

        private val Project.configDir: File
            get() = File(rootDir, ".config")

        val Project.editorConfig: File
            get() = File(rootDir, ".editorconfig")

        val Project.scalaFmtConfig: File
            get() = File(rootDir, "./.scalafmt.conf")
    }

    init {
        group = "verification"
        description = "Generates code style configuration files"
    }

    @TaskAction
    fun generate() {
        // Get settings for each language as a Map
        val kotlinSettings = getKotlinEditorConfigSettings()
        val javaSettings = getJavaEditorConfigSettings()
        val scalaSettings = getScalaEditorConfigSettings()

        // Function to render settings as string
        fun render(
            settings: Map<String, String>,
            indent: String = "",
        ): String = settings.entries.joinToString("\n$indent") { (key, value) -> "$key = $value".trim() }

        project.editorConfig.writeText(
            """
            # $WARNING_MESSAGE
            # $UPDATE_MESSAGE

            root = true

            [*]
            charset = utf-8
            end_of_line = lf
            indent_size = 2
            indent_style = space
            insert_final_newline = true
            trim_trailing_whitespace = true
            max_line_length = 999

            ij_formatter_off_tag = @formatter:off
            ij_formatter_on_tag = @formatter:on

            [*.java]
            ${render(javaSettings, "            ")}

            [*.{kt,kts}]
            ${render(kotlinSettings, "            ")}

            [*.scala]
            ${render(scalaSettings, "            ")}

            """.trimIndent(),
        )

        project.scalaFmtConfig.writeText(
            """
            # $WARNING_MESSAGE
            # $UPDATE_MESSAGE

            version = "3.8.3"
            runner.dialect = scala212

            align.preset = more
            maxColumn = 120
            """.trimIndent(),
        )

        logger.lifecycle("Code style configuration files have been generated.")
    }
}
