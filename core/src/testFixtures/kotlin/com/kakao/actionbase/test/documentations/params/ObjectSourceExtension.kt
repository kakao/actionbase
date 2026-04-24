package com.kakao.actionbase.test.documentations.params

import com.kakao.actionbase.test.ObjectMappers

import java.util.stream.Stream

import kotlin.reflect.full.memberFunctions

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider

import com.fasterxml.jackson.module.kotlin.readValue

class ObjectSourceExtension : TestTemplateInvocationContextProvider {
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

        val testCases: List<Map<String, Any?>> =
            when {
                objectSource != null -> parseObjectSource(objectSource)
                tableSource != null -> parseTableSource(tableSource)
                else -> error("Either @ObjectSource or @TableSource must be present")
            }

        val parameterNames = getParameterNames(context.requiredTestClass, context.requiredTestMethod.name)

        return testCases
            .mapIndexed { index, testCase ->
                ObjectSourceInvocationContext(index + 1, testCases.size, parameterNames, testCase) as TestTemplateInvocationContext
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

    fun parseTableSource(annotation: TableSource): List<Map<String, Any?>> {
        require(annotation.value.isNotBlank()) { "@TableSource: 'value' must be provided" }

        val parsed: Map<String, Any?> = ObjectMappers.YAML.readValue(annotation.value)

        val columns =
            (parsed["columns"] as? List<*>)?.map {
                require(it is String) { "@TableSource: 'columns' entries must be strings, got ${it?.javaClass}" }
                it
            } ?: error("@TableSource: 'columns' key is required and must be a list of strings")

        val rows =
            (parsed["rows"] as? List<*>)?.map {
                require(it is List<*>) { "@TableSource: each row must be a list, got ${it?.javaClass}" }
                it
            } ?: error("@TableSource: 'rows' key is required and must be a list of lists")

        return rows.map { row ->
            require(row.size == columns.size) {
                "@TableSource: row size ${row.size} does not match columns size ${columns.size}: $row"
            }
            columns.zip(row).toMap()
        }
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
