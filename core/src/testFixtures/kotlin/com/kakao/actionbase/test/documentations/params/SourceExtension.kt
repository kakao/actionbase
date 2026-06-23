package com.kakao.actionbase.test.documentations.params

import com.kakao.actionbase.test.ObjectMappers

import java.util.stream.Stream

import kotlin.reflect.full.memberFunctions

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider

import com.fasterxml.jackson.module.kotlin.readValue

class SourceExtension : TestTemplateInvocationContextProvider {
    override fun supportsTestTemplate(context: ExtensionContext): Boolean {
        val method = context.requiredTestMethod
        return method.isAnnotationPresent(ObjectSource::class.java) ||
            method.isAnnotationPresent(TableSource::class.java)
    }

    override fun provideTestTemplateInvocationContexts(context: ExtensionContext): Stream<TestTemplateInvocationContext> {
        val method = context.requiredTestMethod
        val objectSource = method.getAnnotation(ObjectSource::class.java)
        val tableSource = method.getAnnotation(TableSource::class.java)

        require(objectSource == null || tableSource == null) {
            "@ObjectSource and @TableSource cannot be applied to the same method"
        }

        val parameterNames = getParameterNames(context.requiredTestClass, context.requiredTestMethod.name)

        val testCases: List<Map<String, Any?>> =
            when {
                objectSource != null -> parseObjectSource(objectSource)
                tableSource != null -> parseTableSource(tableSource, parameterNames)
                else -> error("Either @ObjectSource or @TableSource must be present")
            }

        return testCases
            .mapIndexed { index, testCase ->
                SourceInvocationContext(index + 1, testCases.size, parameterNames, testCase) as TestTemplateInvocationContext
            }.stream()
    }

    private fun parseObjectSource(annotation: ObjectSource): List<Map<String, Any?>> {
        require(annotation.value.isBlank() || annotation.cases.isBlank()) {
            "@ObjectSource: specify either 'value' or 'cases', not both"
        }

        val testData =
            annotation.cases.ifBlank { annotation.value }.also {
                require(it.isNotBlank()) { "@ObjectSource: 'value' or 'cases' must be provided" }
            }
        val testCases: List<Map<String, Any?>> = ObjectMappers.YAML.readValue(testData)

        val allFields: Map<String, Any?> =
            if (annotation.shared.isNotBlank()) ObjectMappers.YAML.readValue(annotation.shared) else emptyMap()

        return if (allFields.isNotEmpty()) {
            testCases.map { allFields + it }
        } else {
            testCases
        }
    }

    fun parseTableSource(
        annotation: TableSource,
        methodParameters: List<String>,
    ): List<Map<String, Any?>> {
        require(annotation.value.isNotBlank()) { "@TableSource: 'value' must be provided" }
        require(methodParameters.isNotEmpty()) {
            "@TableSource: requires the test method to declare parameters; row cells map by position"
        }

        val rows: List<Any?> = ObjectMappers.YAML.readValue(annotation.value)
        return rows.map { row -> parseRow(row, methodParameters) }
    }

    private fun parseRow(
        row: Any?,
        columns: List<String>,
    ): Map<String, Any?> {
        val cells: List<Any?> =
            when (row) {
                is List<*> -> row
                is String ->
                    row.split("|").map { cell ->
                        val trimmed = cell.trim()
                        require(trimmed.isNotEmpty()) {
                            "@TableSource: empty cell in row '$row'. Use ~ for null."
                        }
                        if (trimmed == "~") null else trimmed
                    }
                else -> error("@TableSource: each row must be a list or a pipe-delimited string, got ${row?.javaClass}")
            }
        require(cells.size == columns.size) {
            "@TableSource: row has ${cells.size} cells, expected ${columns.size} (test method parameters): $row"
        }
        return columns.zip(cells).toMap()
    }

    private fun getParameterNames(
        testClass: Class<*>,
        methodName: String,
    ): List<String> =
        testClass.kotlin.memberFunctions
            .find { it.name == methodName }
            ?.parameters
            ?.drop(1) // drop 'this'
            ?.mapNotNull { it.name }
            ?: emptyList()
}
