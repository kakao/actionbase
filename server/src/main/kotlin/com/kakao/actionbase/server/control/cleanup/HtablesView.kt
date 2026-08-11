package com.kakao.actionbase.server.control.cleanup

import com.kakao.actionbase.server.control.topology.Side
import com.kakao.actionbase.v2.engine.service.ddl.DatastoreTableReference

/** What one side of one tenant reported. The input to the projection, one entry per call made. */
data class SideSnapshot(
    val tenant: String,
    val side: Side,
    val cluster: String,
    val tables: List<TableInfo> = emptyList(),
    val references: Map<String, List<DatastoreTableReference>> = emptyMap(),
    val error: String? = null,
) {
    data class TableInfo(
        val name: String,
        val enabled: Boolean,
        val replicationScope: Int,
    )
}

data class HtablesView(
    val tables: List<TableView>,
    val failures: List<SideFailure>,
)

/**
 * One datastore table as the fleet sees it. Sides are symmetric: a table that exists only on the
 * standby is simply an absent active side, not a separate kind of row.
 */
data class TableView(
    val tenant: String,
    val name: String,
    val sides: Map<Side, SideState>,
    val references: List<ReferenceView>,
    val sharedWith: List<TenantSide>,
)

data class SideState(
    val cluster: String,
    val present: Boolean,
    val enabled: Boolean? = null,
    val replicationScope: Int? = null,
)

/** A binding that still holds the table, and which side it was found on. */
data class ReferenceView(
    val tenant: String,
    val side: Side,
    val kind: DatastoreTableReference.Kind,
    val name: String,
    val active: Boolean,
)

data class TenantSide(
    val tenant: String,
    val side: Side,
)

data class SideFailure(
    val tenant: String,
    val side: Side,
    val error: String,
)
