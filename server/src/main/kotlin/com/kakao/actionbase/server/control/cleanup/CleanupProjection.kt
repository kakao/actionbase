package com.kakao.actionbase.server.control.cleanup

import com.kakao.actionbase.server.control.topology.Side

/**
 * Turns what each side reported into one row per table.
 *
 * Pure: everything it knows arrives in the snapshots. The one thing it can say that no single
 * cluster can is who else holds a table - two tenants configured against the same cluster produce
 * the same cluster key, so a table held from elsewhere shows up as [TableView.sharedWith].
 */
object CleanupProjection {
    fun project(snapshots: List<SideSnapshot>): HtablesView {
        val answered = snapshots.filter { it.error == null }
        val holders = answered.holdersByClusterTable()

        return HtablesView(
            tables =
                answered
                    .groupBy { it.tenant }
                    .flatMap { (tenant, sides) -> tenant.tablesOf(sides, holders) }
                    .sortedWith(compareBy({ it.tenant }, { it.name })),
            failures =
                snapshots
                    .filter { it.error != null }
                    .map { SideFailure(it.tenant, it.side, it.error!!) }
                    .sortedWith(compareBy({ it.tenant }, { it.side })),
        )
    }

    /** Who has each `cluster -> table`, so a row can name the tenants that also hold it. */
    private fun List<SideSnapshot>.holdersByClusterTable(): Map<Pair<String, String>, List<TenantSide>> =
        flatMap { snapshot -> snapshot.tables.map { (snapshot.cluster to it.name) to TenantSide(snapshot.tenant, snapshot.side) } }
            .groupBy({ it.first }, { it.second })

    private fun String.tablesOf(
        sides: List<SideSnapshot>,
        holders: Map<Pair<String, String>, List<TenantSide>>,
    ): List<TableView> {
        val tenant = this
        val byName = sides.associateBy { it.side }

        // Metadata outliving its table is cleanup work too, and it is the only work left on such a
        // row - so names come from the references as well, not just from the tables that still exist.
        return (sides.flatMap { snapshot -> snapshot.tables.map { it.name } } + sides.flatMap { it.references.keys })
            .distinct()
            .map { name ->
                TableView(
                    tenant = tenant,
                    name = name,
                    sides = Side.entries.mapNotNull { side -> byName[side]?.let { side to it.stateOf(name) } }.toMap(),
                    references = byName.values.flatMap { it.referencesTo(name) }.sortedWith(referenceOrder),
                    sharedWith =
                        byName.values
                            .flatMap { holders[it.cluster to name].orEmpty() }
                            .filter { it.tenant != tenant }
                            .distinct()
                            .sortedWith(compareBy({ it.tenant }, { it.side })),
                )
            }
    }

    private fun SideSnapshot.stateOf(name: String): SideState {
        val table = tables.firstOrNull { it.name == name }
        return SideState(
            cluster = cluster,
            present = table != null,
            enabled = table?.enabled,
            replicationScope = table?.replicationScope,
        )
    }

    private fun SideSnapshot.referencesTo(name: String): List<ReferenceView> =
        references[name].orEmpty().map {
            ReferenceView(
                tenant = tenant,
                side = side,
                kind = it.kind,
                name = it.name.fullQualifiedName,
                active = it.active,
            )
        }

    private val referenceOrder = compareBy<ReferenceView>({ it.tenant }, { it.side }, { it.kind }, { it.name })
}
