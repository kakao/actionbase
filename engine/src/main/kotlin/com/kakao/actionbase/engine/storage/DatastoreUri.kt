package com.kakao.actionbase.engine.storage

/**
 * Utility for parsing datastore URIs.
 *
 * Format: datastore://{namespace}/{tableName}
 */
object DatastoreUri {
    private const val PREFIX = "datastore://"
    private val SAFE_NAME_PATTERN = Regex("^[a-z0-9_]+$")

    /**
     * Parses a datastore URI and returns namespace and table name.
     *
     * @param uri The URI to parse (e.g., "datastore://my_namespace/my_table")
     * @return Pair of (namespace, tableName)
     * @throws IllegalArgumentException if URI format is invalid
     */
    fun parse(uri: String): Pair<String, String> {
        require(uri.startsWith(PREFIX)) {
            "Invalid datastore URI: $uri. Must start with '$PREFIX'"
        }
        val parts = uri.removePrefix(PREFIX).split("/")
        require(parts.size == 2) {
            "Invalid datastore URI: $uri. Expected format: datastore://{namespace}/{tableName}"
        }
        val (namespace, tableName) = parts[0] to parts[1]
        require(namespace.isEmpty() || namespace.matches(SAFE_NAME_PATTERN)) {
            "Invalid namespace: $namespace. Must contain only lowercase letters, digits, or underscore."
        }
        require(tableName.matches(SAFE_NAME_PATTERN)) {
            "Invalid table name: $tableName. Must contain only lowercase letters, digits, or underscore."
        }
        return namespace to tableName
    }
}
