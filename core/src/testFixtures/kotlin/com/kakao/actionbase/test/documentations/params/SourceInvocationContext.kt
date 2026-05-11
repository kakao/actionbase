package com.kakao.actionbase.test.documentations.params

import org.junit.jupiter.api.extension.ParameterResolver
import org.junit.jupiter.api.extension.TestTemplateInvocationContext

class SourceInvocationContext(
    private val index: Int,
    private val total: Int,
    private val parameterNames: List<String>,
    private val testCase: Map<String, Any?>,
) : TestTemplateInvocationContext {
    override fun getDisplayName(invocationIndex: Int): String = "[$index/$total] ${testCase.entries.joinToString(", ") { "${it.key}=${it.value}" }}"

    override fun getAdditionalExtensions(): List<ParameterResolver> = listOf(SourceParameterResolver(parameterNames, testCase))
}
