package com.kakao.actionbase.v2.engine.metastore.purge

/**
 * A metastore table the purge may open.
 *
 * Credentials are deliberately absent: this travels in request and response bodies and lands in
 * logs, so it carries only what identifies the table.
 *
 * The purge is a standalone tool, not a label. It shares no code with `Graph`, `JdbcHashLabel` or
 * the metastore's serving path - it opens a connection, reads rows, and decodes values with
 * codec-java. Deleting rows out from under the running metastore is not something the serving path
 * should grow the ability to do, and a tool that dies with JDBC should not be entangled with the
 * code being retired around it.
 */
data class MetastoreTarget(
    /** The configured name, which is how a caller asks for it. */
    val name: String,
    val url: String,
    val table: String,
) {
    init {
        require(name.isNotBlank()) { "metastore name is blank" }
        require(url.startsWith("jdbc:")) { "metastore '$name': url must be a jdbc url, got '$url'" }
        // The table name is interpolated into every statement the purge runs, so it is checked
        // here rather than at the call sites - a data class cannot be built around the check.
        require(IDENTIFIER.matches(table)) { "metastore '$name': table '$table' is not a plain sql identifier" }
    }

    private companion object {
        val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}
