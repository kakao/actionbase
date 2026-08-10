package com.kakao.actionbase.server.control.metastore

import com.kakao.actionbase.v2.engine.metastore.purge.MetastoreTarget

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * The metastore tables a control instance may open for a purge.
 *
 * ```yaml
 * actionbase:
 *   role: CONTROL
 *   control:
 *     metastores:
 *       alpha:
 *         url: jdbc:mysql://ab-alpha-meta.example.net:3306/graph
 *         user: ${METASTORE_USER_ALPHA}
 *         password: ${METASTORE_PASSWORD_ALPHA}
 *         table: kc_graph_metadata   # optional, this is the default
 * ```
 *
 * This is an allowlist, not a convenience. Without it the purge would open whatever database a
 * request body named, so a target absent from here is refused rather than resolved.
 */
@ConfigurationProperties(prefix = "actionbase.control")
data class MetastoreProperties(
    val metastores: Map<String, Metastore> = emptyMap(),
) {
    data class Metastore(
        val url: String,
        val user: String = "",
        val password: String = "",
        val table: String = DEFAULT_TABLE,
    )

    /**
     * Resolves every entry up front. A malformed url or a duplicated coordinate is a deployment
     * mistake, and finding it at boot beats finding it when an operator is midway through a purge.
     */
    fun toRegistry(): MetastoreRegistry {
        val entries =
            metastores.map { (name, metastore) ->
                MetastoreRegistry.Entry(
                    target = MetastoreTarget(name = name, url = metastore.url, table = metastore.table),
                    user = metastore.user,
                    password = metastore.password,
                )
            }

        val duplicates = entries.groupBy { it.target.url to it.target.table }.filterValues { it.size > 1 }
        require(duplicates.isEmpty()) {
            val named = duplicates.values.flatten().joinToString(", ") { it.target.name }
            "actionbase.control.metastores: $named point at the same url and table, " +
                "so a purge set cannot be resolved back to one of them"
        }

        return MetastoreRegistry(entries)
    }

    companion object {
        const val DEFAULT_TABLE = "kc_graph_metadata"
    }
}
