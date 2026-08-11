package com.kakao.actionbase.engine.query

/**
 * A query whose shape is fixed and whose values are not. A `VALUE` vertex may hold `{name}` in place
 * of a value; [bind] swaps those for arguments and returns a query to run, leaving this one intact.
 *
 * The shape staying constant is what lets a transform's plan be prepared once and reused across
 * requests. Immutable and safe to share.
 */
class PreparedQuery private constructor(
    private val template: ActionbaseQuery,
    /** Names this query expects, in the order they first appear. */
    val parameters: Set<String>,
) {
    fun bind(arguments: Map<String, Any>): ActionbaseQuery {
        val missing = parameters - arguments.keys
        require(missing.isEmpty()) { "This query takes ${parameters.joinToString()}; the call is missing ${missing.joinToString()}." }

        return template.copy(fetch = template.fetch.map { it.bind(arguments) })
    }

    /** The arguments a transform's `?` placeholders take, in placeholder order. */
    fun transformArguments(
        transform: ActionbaseQuery.Transform.Sql,
        arguments: Map<String, Any>,
    ): List<Any?> =
        transform.arguments.map { name ->
            require(name in parameters) { "`${transform.name}` binds `$name`, which this query has no parameter for." }
            arguments[name]
        }

    private fun ActionbaseQuery.Item.bind(arguments: Map<String, Any>): ActionbaseQuery.Item =
        when (this) {
            is ActionbaseQuery.Item.Self -> copy(source = source.bind(arguments))
            is ActionbaseQuery.Item.Get -> copy(source = source.bind(arguments), target = target.bind(arguments))
            is ActionbaseQuery.Item.Count -> copy(source = source.bind(arguments))
            is ActionbaseQuery.Item.Scan -> copy(source = source.bind(arguments))
            is ActionbaseQuery.Item.Seek -> copy(source = source.bind(arguments))
            is ActionbaseQuery.Item.Topk -> copy(entity = entity?.bind(arguments))
        }

    private fun ActionbaseQuery.Vertex.bind(arguments: Map<String, Any>): ActionbaseQuery.Vertex =
        when (this) {
            is ActionbaseQuery.Vertex.Value -> copy(value = value.map { it.substitute(arguments) })
            is ActionbaseQuery.Vertex.Ref -> this
            is ActionbaseQuery.Vertex.Step -> copy(step = step.bind(arguments))
        }

    private fun Any.substitute(arguments: Map<String, Any>): Any = parameterOf(this)?.let { arguments.getValue(it) } ?: this

    companion object {
        /**
         * A parameter is a whole value, not a fragment of one: `"{entity}"` is a placeholder and
         * `"user-{entity}"` is a string that happens to contain braces. Keeping it whole means an
         * argument can be any type, not just something that survives string concatenation.
         */
        private val PARAMETER = Regex("^\\{([A-Za-z_][A-Za-z0-9_]*)}$")

        fun of(query: ActionbaseQuery): PreparedQuery = PreparedQuery(query, query.parameters())

        private fun ActionbaseQuery.parameters(): Set<String> =
            fetch.flatMap { it.parameters() }.toSet() +
                transform.flatMap { step -> (step as? ActionbaseQuery.Transform.Sql)?.arguments.orEmpty() }

        private fun ActionbaseQuery.Item.parameters(): List<String> =
            when (this) {
                is ActionbaseQuery.Item.Self -> source.parameters()
                is ActionbaseQuery.Item.Get -> source.parameters() + target.parameters()
                is ActionbaseQuery.Item.Count -> source.parameters()
                is ActionbaseQuery.Item.Scan -> source.parameters()
                is ActionbaseQuery.Item.Seek -> source.parameters()
                is ActionbaseQuery.Item.Topk -> entity?.parameters().orEmpty()
            }

        private fun ActionbaseQuery.Vertex.parameters(): List<String> =
            when (this) {
                is ActionbaseQuery.Vertex.Value -> value.mapNotNull { parameterOf(it) }
                is ActionbaseQuery.Vertex.Ref -> emptyList()
                is ActionbaseQuery.Vertex.Step -> step.parameters()
            }

        private fun parameterOf(value: Any): String? = (value as? String)?.let { PARAMETER.find(it)?.groupValues?.get(1) }
    }
}
