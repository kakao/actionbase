package com.kakao.actionbase.engine.query

import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.v2.engine.sql.StatKey

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * A query whose shape is fixed and whose values are not: anywhere a whole value would go, the body may
 * hold `{name}` instead.
 *
 * Binding happens on the JSON, before it is read into an [ActionbaseQuery]: `"limit": "{limit}"` cannot
 * be the `Int` that slot wants until the value is in, so substitution has to come first.
 *
 * A placeholder inside SQL becomes a `?` bound at execution instead, which keeps the SQL text — and so
 * the compiled plan — the same between calls.
 */
class PreparedQuery private constructor(
    private val template: ObjectNode,
    val parameters: Set<String>,
    /** Empty for a query nobody registered: with no declaration, a value goes in as the JSON it came as. */
    private val types: Map<String, PrimitiveType>,
) {
    fun bind(values: Map<String, Any>): ActionbaseQuery {
        val missing = parameters - values.keys
        require(missing.isEmpty()) { "This query takes ${parameters.joinToString()}; the call is missing ${missing.joinToString()}." }

        val unknown = values.keys - parameters
        require(unknown.isEmpty()) { "This query has no argument named ${unknown.joinToString()}." }

        val cast = parameters.associateWith { name -> cast(name, values.getValue(name)) }
        return objectMapper.convertValue(template.deepCopy().substitute(cast))
    }

    private fun cast(
        name: String,
        value: Any,
    ): Any = types[name]?.cast(value) ?: value

    fun transformArguments(
        transform: ActionbaseQuery.Transform,
        values: Map<String, Any>,
    ): List<Any?> =
        when (transform) {
            is ActionbaseQuery.Transform.Sql ->
                transform.arguments.map { name ->
                    require(name in parameters) { "`${transform.name}` binds `$name`, which this query has no argument for." }
                    cast(name, values.getValue(name))
                }
        }

    private fun JsonNode.substitute(values: Map<String, Any>): JsonNode {
        when (this) {
            is ObjectNode -> fieldNames().asSequence().toList().forEach { field -> replace(field, get(field).substituted(values)) }
            is ArrayNode -> (0 until size()).forEach { index -> set(index, get(index).substituted(values)) }
            else -> Unit
        }
        return this
    }

    private fun JsonNode.substituted(values: Map<String, Any>): JsonNode =
        parameterOf(this)?.let { name -> objectMapper.valueToTree<JsonNode>(values.getValue(name)) }
            ?: substitute(values)

    companion object {
        private val objectMapper = jacksonObjectMapper()

        private const val SQL_FIELD = "sql"

        private const val ARGUMENTS_FIELD = "arguments"

        /** Whole values only, so that an argument can be a number: `"user-{entity}"` is a string. */
        private val PARAMETER = Regex("^\\{([A-Za-z_][A-Za-z0-9_]*)}$")

        private val IN_SQL = Regex("\\{([A-Za-z_][A-Za-z0-9_]*)}")

        /** A registered query, which has to use exactly the names it declares. */
        fun of(
            fetch: JsonNode,
            transform: JsonNode,
            stats: Set<StatKey>,
            arguments: List<StructField>,
        ): PreparedQuery {
            val template = template(fetch, transform, stats)
            val used = template.prepare()
            val declared = arguments.map { it.name }.toSet()

            require(used == declared) {
                "This query uses ${used.joinToString().ifEmpty { "no arguments" }} " +
                    "but declares ${declared.joinToString().ifEmpty { "none" }}."
            }

            return PreparedQuery(template, declared, arguments.associate { it.name to it.type })
        }

        fun of(query: ActionbaseQuery): PreparedQuery {
            // The whole query, not its parts: a step's `type` is written from the property's declared type,
            // which a bare list of items no longer carries.
            val template = objectMapper.valueToTree<ObjectNode>(query)
            return PreparedQuery(template, template.prepare(), emptyMap())
        }

        /** A query nobody registered: the names are the ones the body uses, and nothing declares a type. */
        fun adhoc(
            fetch: JsonNode,
            transform: JsonNode,
            stats: Set<StatKey>,
        ): PreparedQuery {
            val template = template(fetch, transform, stats)
            return PreparedQuery(template, template.prepare(), emptyMap())
        }

        private fun template(
            fetch: JsonNode,
            transform: JsonNode,
            stats: Set<StatKey>,
        ): ObjectNode =
            objectMapper.createObjectNode().apply {
                set<JsonNode>("fetch", fetch.deepCopy())
                set<JsonNode>("transform", transform.deepCopy())
                set<JsonNode>("stats", objectMapper.valueToTree(stats))
            }

        /**
         * Rewrites every SQL body to the `?` form once, and answers with the names the template asks for.
         * Doing it here rather than per call is what keeps the SQL text, and so the compiled plan, the same
         * between calls.
         *
         * The SQL itself is not read here. Calcite first sees it on the call that runs it, because a plan is
         * compiled against the columns of the frames it reads and those are only known once the fetch steps
         * have run — so a statement Calcite would reject registers cleanly and fails on its first call.
         */
        private fun JsonNode.prepare(): Set<String> =
            when (this) {
                is ObjectNode ->
                    fieldNames()
                        .asSequence()
                        .toList()
                        .flatMap { field ->
                            val child = get(field)
                            if (field == SQL_FIELD && child.isTextual) {
                                val (sql, bound) = rewrite(child.asText())
                                put(SQL_FIELD, sql)
                                set<ArrayNode>(ARGUMENTS_FIELD, objectMapper.valueToTree(bound))
                                bound
                            } else {
                                child.prepare()
                            }
                        }.toSet()
                is ArrayNode -> (0 until size()).flatMap { get(it).prepare() }.toSet()
                else -> parameterOf(this)?.let { setOf(it) } ?: emptySet()
            }

        /** Braces inside a string literal are left alone: `WHERE tag = \'{limit}\'` is a literal. */
        private fun rewrite(sql: String): Pair<String, List<String>> {
            val rewritten = StringBuilder()
            val bound = mutableListOf<String>()
            var index = 0
            var quoted = false

            while (index < sql.length) {
                val char = sql[index]
                if (char == '\'') {
                    quoted = !quoted
                    rewritten.append(char)
                    index++
                    continue
                }

                val match = if (!quoted && char == '{') IN_SQL.matchAt(sql, index) else null
                if (match == null) {
                    rewritten.append(char)
                    index++
                } else {
                    rewritten.append('?')
                    bound += match.groupValues[1]
                    index += match.value.length
                }
            }

            return rewritten.toString() to bound
        }

        private fun parameterOf(node: JsonNode): String? = node.takeIf { it.isTextual }?.asText()?.let { PARAMETER.find(it)?.groupValues?.get(1) }
    }
}
