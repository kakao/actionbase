package com.kakao.actionbase.v2.engine.metastore.purge

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * The metastore table, as the purge needs to see it.
 *
 * Deliberately not `MetadataTable`. That one is the serving path's definition and carries the index
 * declarations its DDL creation needs; this one only ever reads, deletes and inserts, and naming
 * the columns separately keeps a change on either side from reaching the other. The table name
 * comes from configuration and is checked as an identifier by `MetastoreTarget`.
 */
internal class PurgeTable(
    name: String,
) : Table(name) {
    val id = long("id")
    val k = varchar("k", 512)
    val v = text("v")
    val createdAt = datetime("created_at")
    val createdBy = varchar("created_by", 256)
    val modifiedAt = datetime("modified_at")
    val modifiedBy = varchar("modified_by", 256)
    val updateTs = datetime("update_ts")
}
