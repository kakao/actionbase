package com.kakao.actionbase.v2.engine.storage

/**
 * Utility for parsing datastore URIs.
 *
 * Format: datastore://{namespace}/{tableName}
 */
object DatastoreUri {
    private const val PREFIX = "datastore://"

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
        return parts[0] to parts[1]
    }
}
