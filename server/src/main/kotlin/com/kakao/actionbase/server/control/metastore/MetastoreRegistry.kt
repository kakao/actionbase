package com.kakao.actionbase.server.control.metastore

import com.kakao.actionbase.v2.engine.metastore.purge.MetastoreTarget

import java.sql.Connection
import java.sql.DriverManager

/**
 * The metastores a purge may open, and the only place their credentials live.
 *
 * Connections are opened per use rather than pooled at startup. A purge runs a handful of
 * statements per request against a database the data plane is still serving from, so a pool would
 * only add ways to hold its connections open; and a metastore that is down should fail the request
 * that wanted it, not the boot of a control instance that has other work to do.
 */
class MetastoreRegistry(
    entries: List<Entry>,
) {
    private val byName: Map<String, Entry> = entries.associateBy { it.target.name }
    private val byCoordinate: Map<Pair<String, String>, Entry> = entries.associateBy { it.target.url to it.target.table }

    val targets: List<MetastoreTarget> get() = byName.values.map { it.target }

    /** Resolves the name a caller asked for. */
    fun target(name: String): MetastoreTarget =
        byName[name]?.target
            ?: throw IllegalArgumentException("unknown metastore '$name': configured are ${byName.keys.sorted()}")

    /**
     * Resolves the coordinate carried by a purge set, which is how a file saved earlier is checked
     * against what this instance is allowed to open.
     */
    fun target(
        url: String,
        table: String,
    ): MetastoreTarget =
        byCoordinate[url to table]?.target
            ?: throw IllegalArgumentException("metastore '$url' table '$table' is not configured on this instance")

    /** Hands the purge a way to open connections without handing it the credentials. */
    fun connections(target: MetastoreTarget): () -> Connection {
        val entry =
            byName[target.name]?.takeIf { it.target == target }
                ?: throw IllegalArgumentException("metastore '${target.name}' is not configured on this instance")
        return { DriverManager.getConnection(entry.target.url, entry.user, entry.password) }
    }

    class Entry(
        val target: MetastoreTarget,
        val user: String,
        val password: String,
    )
}
