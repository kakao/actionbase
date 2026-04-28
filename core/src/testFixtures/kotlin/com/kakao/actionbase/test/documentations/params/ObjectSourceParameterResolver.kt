package com.kakao.actionbase.test.documentations.params

import com.kakao.actionbase.test.ObjectMappers

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.type.CollectionType
import com.fasterxml.jackson.databind.type.MapType

class ObjectSourceParameterResolver(
    private val parameterNames: List<String>,
    private val testCase: Map<String, Any?>,
) : ParameterResolver {
    companion object {
        private val READ_VALUE_TYPES: Set<Class<*>> =
            setOf(
                MapType::class.java,
                CollectionType::class.java,
            )
    }

    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Boolean {
        val testMethod = extensionContext.testMethod.orElse(null) ?: return false
        if (parameterContext.declaringExecutable != testMethod) return false
        return parameterContext.index < parameterNames.size
    }

    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Any? {
        val paramName = parameterNames[parameterContext.index]
        val value: Any? = testCase[paramName]

        val javaType =
            ObjectMappers.YAML.typeFactory.constructType(
                parameterContext.parameter.parameterizedType,
            )

        return if (shouldUseReadValue(javaType, value)) {
            ObjectMappers.YAML.readValue(value.toString(), javaType)
        } else {
            ObjectMappers.YAML.convertValue(value, javaType)
        }
    }

    private fun shouldUseReadValue(
        javaType: JavaType,
        value: Any?,
    ) = (READ_VALUE_TYPES.contains(javaType::class.java)) && value is String
}
