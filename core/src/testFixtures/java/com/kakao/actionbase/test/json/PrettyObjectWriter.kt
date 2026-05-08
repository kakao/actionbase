package com.kakao.actionbase.test.json

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

class PrettyObjectWriter(
    private val indentSize: Int = 2,
    private val lineLengthLimit: Int = 80,
) {
    val objectMapper: ObjectMapper
        get() = mapper

    fun format(
        json: String,
        sort: Boolean = false,
    ): String =
        buildString {
            val jsonNode = mapper.readTree(json)
            writeJsonNode(jsonNode, 0, sort)
        }

    fun writeValueAsString(
        value: Any,
        sort: Boolean = false,
    ): String =
        buildString {
            val jsonNode = mapper.valueToTree<JsonNode>(value)
            writeJsonNode(jsonNode, 0, sort)
        }

    private val indent = " ".repeat(indentSize)

    private fun StringBuilder.writeJsonNode(
        node: JsonNode,
        depth: Int,
        sort: Boolean,
    ) {
        val currentIndent = indent.repeat(depth)
        val nextIndent = indent.repeat(depth + 1)
        val nestedLimit = lineLengthLimit - (currentIndent.length * indentSize)

        when (node) {
            is ObjectNode -> writeObjectNode(node, depth, currentIndent, nextIndent, nestedLimit, sort)
            is ArrayNode -> writeArrayNode(node, depth, currentIndent, nextIndent, nestedLimit, sort)
            else -> writeNode(node)
        }
    }

    private fun StringBuilder.writeObjectNode(
        node: ObjectNode,
        depth: Int,
        currentIndent: String,
        nextIndent: String,
        nestedLimit: Int,
        sort: Boolean,
    ) {
        // Create sorted field list
        val fields =
            if (sort) {
                node
                    .fields()
                    .asSequence()
                    .sortedBy { it.key }
                    .toList()
            } else {
                node.fields().asSequence().toList()
            }

        // Create new ObjectNode if sorting is needed
        val sortedNode =
            if (sort) {
                val newNode = mapper.createObjectNode()
                fields.forEach { (key, value) ->
                    newNode.set<JsonNode>(key, value)
                }
                newNode
            } else {
                node
            }

        val rendered = tryRender(sortedNode)

        if (rendered.length < nestedLimit) {
            append(rendered)
            return
        }

        append("{\n")

        fields.forEachIndexed { index, (key, value) ->
            append("$nextIndent\"$key\": ")
            writeJsonNode(value, depth + 1, sort)

            if (index < fields.size - 1) {
                append(",\n")
            } else {
                append("\n")
            }
        }

        append("$currentIndent}")
    }

    private fun StringBuilder.writeArrayNode(
        node: ArrayNode,
        depth: Int,
        currentIndent: String,
        nextIndent: String,
        nestedLimit: Int,
        sort: Boolean,
    ) {
        val rendered = tryRender(node)

        if (rendered.length < nestedLimit) {
            append(rendered)
            return
        }

        append("[\n")

        node.forEachIndexed { index, element ->
            append(nextIndent)

            writeJsonNode(element, depth + 1, sort)

            if (index < node.size() - 1) {
                append(",\n")
            } else {
                append("\n")
            }
        }

        append("$currentIndent]")
    }

    private fun StringBuilder.writeNode(node: JsonNode) {
        append(node.toString())
    }

    private fun tryRender(value: Any): String = prettyPrinter.writeValueAsString(value)

    companion object {
        val DEFAULT: PrettyObjectWriter = PrettyObjectWriter(indentSize = 2, lineLengthLimit = 80)

        private val mapper =
            jacksonObjectMapper().apply {
                setDefaultPrettyPrinter(PrettyPrinter())
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }

        private val prettyPrinter: ObjectWriter = mapper.writerWithDefaultPrettyPrinter()
    }
}
