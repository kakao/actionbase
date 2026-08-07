package com.kakao.actionbase.server.control.cleanup

import com.kakao.actionbase.server.control.topology.Side
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.service.ddl.DatastoreTableReference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CleanupProjectionTest {
    @Test
    fun `a table reports the state of each side`() {
        val view =
            CleanupProjection.project(
                listOf(
                    snapshot(Side.ACTIVE, "a:80", table("ns:t", enabled = true, scope = 1)),
                    snapshot(Side.STANDBY, "b:80", table("ns:t", enabled = false, scope = 0)),
                ),
            )

        val table = view.tables.single()
        assertEquals(listOf(Side.ACTIVE, Side.STANDBY), table.sides.keys.toList())
        assertEquals(SideState("a:80", present = true, enabled = true, replicationScope = 1), table.sides[Side.ACTIVE])
        assertEquals(SideState("b:80", present = true, enabled = false, replicationScope = 0), table.sides[Side.STANDBY])
    }

    // The standby-only table is the case a client-side model usually needs a separate list for.
    @Test
    fun `a table only on the standby is an absent active side`() {
        val view =
            CleanupProjection.project(
                listOf(
                    snapshot(Side.ACTIVE, "a:80"),
                    snapshot(Side.STANDBY, "b:80", table("ns:leftover")),
                ),
            )

        val table = view.tables.single()
        assertEquals("ns:leftover", table.name)
        assertFalse(table.sides.getValue(Side.ACTIVE).present)
        assertNull(table.sides.getValue(Side.ACTIVE).enabled)
        assertTrue(table.sides.getValue(Side.STANDBY).present)
    }

    // The purge case: the table is gone everywhere and only its metadata is left. A row driven by
    // the tables that still exist would drop it, which is the one row with work left on it.
    @Test
    fun `metadata outliving its table is still a row`() {
        val view =
            CleanupProjection.project(
                listOf(
                    snapshot(
                        Side.ACTIVE,
                        "a:80",
                        references = mapOf("ns:dropped" to listOf(reference(DatastoreTableReference.Kind.STORAGE, "svc.store", active = false))),
                    ),
                ),
            )

        val table = view.tables.single()
        assertEquals("ns:dropped", table.name)
        assertFalse(table.sides.getValue(Side.ACTIVE).present)
        assertEquals("svc.store", table.references.single().name)
    }

    // Only the control plane can see this: one cluster, two tenants configured against it.
    @Test
    fun `a table held from another tenant names that tenant`() {
        val view =
            CleanupProjection.project(
                listOf(
                    snapshot(Side.ACTIVE, "shared:80", table("ns:t"), tenant = "kc"),
                    snapshot(Side.ACTIVE, "shared:80", table("ns:t"), tenant = "talk"),
                ),
            )

        val kc = view.tables.single { it.tenant == "kc" }
        assertEquals(listOf(TenantSide("talk", Side.ACTIVE)), kc.sharedWith)

        val talk = view.tables.single { it.tenant == "talk" }
        assertEquals(listOf(TenantSide("kc", Side.ACTIVE)), talk.sharedWith)
    }

    @Test
    fun `a table on its own cluster is shared with nobody`() {
        val view =
            CleanupProjection.project(
                listOf(
                    snapshot(Side.ACTIVE, "a:80", table("ns:t"), tenant = "kc"),
                    snapshot(Side.ACTIVE, "b:80", table("ns:t"), tenant = "talk"),
                ),
            )

        assertTrue(view.tables.all { it.sharedWith.isEmpty() }, view.tables.toString())
    }

    @Test
    fun `references carry the side they were found on`() {
        val view =
            CleanupProjection.project(
                listOf(
                    snapshot(
                        Side.STANDBY,
                        "b:80",
                        table("ns:t"),
                        references = mapOf("ns:t" to listOf(reference(DatastoreTableReference.Kind.LABEL, "svc.label", active = true))),
                    ),
                ),
            )

        val held =
            view.tables
                .single()
                .references
                .single()
        assertEquals(Side.STANDBY, held.side)
        assertEquals(DatastoreTableReference.Kind.LABEL, held.kind)
        assertEquals("svc.label", held.name)
        assertTrue(held.active)
    }

    @Test
    fun `a failed side is reported instead of read as nothing to clean up`() {
        val view =
            CleanupProjection.project(
                listOf(
                    snapshot(Side.ACTIVE, "a:80", table("ns:t")),
                    SideSnapshot(tenant = "kc", side = Side.STANDBY, cluster = "b:80", error = "connection refused"),
                ),
            )

        assertEquals(listOf(SideFailure("kc", Side.STANDBY, "connection refused")), view.failures)
        // The active answer survives, and the standby is absent from the row rather than "not present".
        assertEquals(
            listOf(Side.ACTIVE),
            view.tables
                .single()
                .sides.keys
                .toList(),
        )
    }

    @Test
    fun `rows are ordered by tenant then name`() {
        val view =
            CleanupProjection.project(
                listOf(
                    snapshot(Side.ACTIVE, "a:80", table("ns:b"), table("ns:a"), tenant = "talk"),
                    snapshot(Side.ACTIVE, "c:80", table("ns:z"), tenant = "kc"),
                ),
            )

        assertEquals(listOf("kc" to "ns:z", "talk" to "ns:a", "talk" to "ns:b"), view.tables.map { it.tenant to it.name })
    }

    private fun snapshot(
        side: Side,
        cluster: String,
        vararg tables: SideSnapshot.TableInfo,
        tenant: String = "kc",
        references: Map<String, List<DatastoreTableReference>> = emptyMap(),
    ) = SideSnapshot(
        tenant = tenant,
        side = side,
        cluster = cluster,
        tables = tables.toList(),
        references = references,
    )

    private fun table(
        name: String,
        enabled: Boolean = true,
        scope: Int = 0,
    ) = SideSnapshot.TableInfo(name, enabled, scope)

    private fun reference(
        kind: DatastoreTableReference.Kind,
        name: String,
        active: Boolean,
    ) = DatastoreTableReference(kind, EntityName.of(name), active)
}
