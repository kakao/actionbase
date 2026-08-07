package com.kakao.actionbase.server.control.cleanup

import com.kakao.actionbase.server.control.topology.Side
import com.kakao.actionbase.v2.engine.service.ddl.DatastoreTableReference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The plan is dry, so the whole grid can be walked here: every refusal asserts *which* precondition
 * stopped it, because that string is the only thing an operator has to go on.
 */
class CleanupPlannerTest {
    @Test
    fun `going local turns replication off on the sides that have it on`() {
        val plan =
            plan(
                CleanupAction.REPLICATION_OFF,
                table(active = state(scope = 1), standby = state(scope = 0)),
                fanout = true,
            )

        assertTrue(plan.ok)
        assertEquals(listOf(PlanStep(Side.ACTIVE, StepOp.REPLICATION_DISABLE)), plan.steps)
    }

    // Partial fanout: a pair that drifted apart is brought together, not refused.
    @Test
    fun `a side already at the target state is skipped rather than refused`() {
        val plan =
            plan(
                CleanupAction.DISABLE_TABLE,
                table(active = state(enabled = true, scope = 0), standby = state(enabled = false, scope = 0)),
                fanout = true,
            )

        assertTrue(plan.ok)
        assertEquals(listOf(Side.ACTIVE), plan.steps.map { it.side })
    }

    @Test
    fun `nothing to do is a refusal that says so`() {
        val plan = plan(CleanupAction.REPLICATION_OFF, table(active = state(scope = 0)))

        assertRefused(plan, Precondition.ALREADY_SATISFIED)
    }

    @Test
    fun `disable needs replication off first`() {
        val plan = plan(CleanupAction.DISABLE_TABLE, table(active = state(enabled = true, scope = 1)))

        assertRefused(plan, Precondition.NEEDS_LOCAL_REPLICATION)
        assertTrue(plan.detail!!.contains("standby sync"), plan.detail)
    }

    @Test
    fun `going global needs the table enabled first`() {
        val plan = plan(CleanupAction.REPLICATION_ON, table(active = state(enabled = false, scope = 0)))

        assertRefused(plan, Precondition.NEEDS_ENABLED)
    }

    @Test
    fun `dropping needs the metadata unlinked first`() {
        val plan =
            plan(
                CleanupAction.DROP_TABLE,
                table(active = state(enabled = false, scope = 0), references = listOf(label("svc.label", active = true))),
            )

        assertRefused(plan, Precondition.NEEDS_UNLINK)
    }

    @Test
    fun `dropping needs the table disabled first`() {
        val plan = plan(CleanupAction.DROP_TABLE, table(active = state(enabled = true, scope = 0)))

        assertRefused(plan, Precondition.NEEDS_DISABLE)
    }

    @Test
    fun `dropping needs replication off first`() {
        val plan = plan(CleanupAction.DROP_TABLE, table(active = state(enabled = false, scope = 1)))

        assertRefused(plan, Precondition.NEEDS_LOCAL_REPLICATION)
    }

    @Test
    fun `dropping runs once every step before it is done`() {
        val plan =
            plan(
                CleanupAction.DROP_TABLE,
                table(active = state(enabled = false, scope = 0), standby = state(enabled = false, scope = 0)),
                fanout = true,
            )

        assertTrue(plan.ok)
        assertEquals(listOf(Side.ACTIVE, Side.STANDBY), plan.steps.map { it.side })
        assertTrue(plan.steps.all { it.op == StepOp.DROP_TABLE })
    }

    // drop and purge are complementary: whichever applies, the other refuses.
    @Test
    fun `dropping a table that is already gone points at purge`() {
        val plan = plan(CleanupAction.DROP_TABLE, table(active = state(present = false)))

        assertRefused(plan, Precondition.TABLE_ALREADY_GONE)
        assertTrue(plan.detail!!.contains("delete the metadata"), plan.detail)
    }

    @Test
    fun `purging a table that still exists points at drop`() {
        val plan = plan(CleanupAction.DELETE_METADATA, table(active = state(enabled = false, scope = 0)))

        assertRefused(plan, Precondition.TABLE_STILL_EXISTS)
        assertTrue(plan.detail!!.contains("drop"), plan.detail)
    }

    @Test
    fun `unlinking deactivates labels before storages`() {
        val plan =
            plan(
                CleanupAction.UNLINK,
                table(
                    active = state(),
                    references = listOf(storage("svc.store", active = true), label("svc.label", active = true)),
                ),
            )

        assertTrue(plan.ok)
        assertEquals(
            listOf(StepOp.DEACTIVATE_COLLECTION to "svc.label", StepOp.DEACTIVATE_STORAGE to "svc.store"),
            plan.steps.map { it.op to it.name },
        )
    }

    @Test
    fun `purging deletes labels before storages`() {
        val plan =
            plan(
                CleanupAction.DELETE_METADATA,
                table(
                    active = state(present = false),
                    references = listOf(storage("svc.store", active = false), label("svc.label", active = false)),
                ),
            )

        assertTrue(plan.ok)
        assertEquals(
            listOf(StepOp.DELETE_COLLECTION to "svc.label", StepOp.DELETE_STORAGE to "svc.store"),
            plan.steps.map { it.op to it.name },
        )
    }

    // Metadata is not replicated, so it is only ever touched on the active side.
    @Test
    fun `unlinking stays on the active side even with fanout asked for`() {
        val plan =
            plan(
                CleanupAction.UNLINK,
                table(active = state(), standby = state(), references = listOf(label("svc.label", active = true))),
                fanout = true,
            )

        assertEquals(listOf(Side.ACTIVE), plan.steps.map { it.side }.distinct())
    }

    @Test
    fun `unlinking with nothing bound is already satisfied`() {
        val plan = plan(CleanupAction.UNLINK, table(active = state(), references = listOf(label("svc.label", active = false))))

        assertRefused(plan, Precondition.ALREADY_SATISFIED)
    }

    // Only the control plane can see this, so only the control plane can warn about it.
    @Test
    fun `a table another tenant still binds is refused by name`() {
        val plan =
            plan(
                CleanupAction.DISABLE_TABLE,
                table(
                    active = state(enabled = true, scope = 0),
                    references = listOf(label("other.label", active = true, tenant = "talk")),
                ),
            )

        assertRefused(plan, Precondition.HELD_BY_ANOTHER_TENANT)
        assertTrue(plan.detail!!.contains("talk"), plan.detail)
    }

    @Test
    fun `without fanout only the active side is planned`() {
        val plan =
            plan(
                CleanupAction.REPLICATION_OFF,
                table(active = state(scope = 1), standby = state(scope = 1)),
                fanout = false,
            )

        assertEquals(listOf(Side.ACTIVE), plan.steps.map { it.side })
    }

    @Test
    fun `a table living only on the standby is still droppable there`() {
        val plan =
            plan(
                CleanupAction.DROP_TABLE,
                table(active = state(present = false), standby = state(enabled = false, scope = 0)),
                fanout = true,
            )

        assertTrue(plan.ok)
        assertEquals(listOf(PlanStep(Side.STANDBY, StepOp.DROP_TABLE)), plan.steps)
    }

    @Test
    fun `an unknown action names the ones that exist`() {
        val thrown = runCatching { CleanupAction.of("obliterate") }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
        assertTrue(thrown.message!!.contains("delete_metadata"), thrown.message)
    }

    private fun assertRefused(
        plan: CleanupPlan,
        expected: Precondition,
    ) {
        assertFalse(plan.ok, "expected a refusal, got ${plan.steps}")
        assertEquals(expected, plan.refusal)
        assertTrue(plan.steps.isEmpty())
        assertTrue(!plan.detail.isNullOrBlank())
    }

    private fun plan(
        action: CleanupAction,
        table: TableView,
        fanout: Boolean = false,
    ) = CleanupPlanner.plan(action, table, fanout)

    private fun state(
        present: Boolean = true,
        enabled: Boolean = true,
        scope: Int = 0,
    ) = SideState(
        cluster = "cluster:80",
        present = present,
        enabled = if (present) enabled else null,
        replicationScope = if (present) scope else null,
    )

    private fun table(
        active: SideState? = null,
        standby: SideState? = null,
        references: List<ReferenceView> = emptyList(),
    ) = TableView(
        tenant = "kc",
        name = "ns:t",
        sides =
            buildMap {
                active?.let { put(Side.ACTIVE, it) }
                standby?.let { put(Side.STANDBY, it) }
            },
        references = references,
        sharedWith = emptyList(),
    )

    private fun label(
        name: String,
        active: Boolean,
        tenant: String = "kc",
    ) = ReferenceView(tenant, Side.ACTIVE, DatastoreTableReference.Kind.LABEL, name, active)

    private fun storage(
        name: String,
        active: Boolean,
        tenant: String = "kc",
    ) = ReferenceView(tenant, Side.ACTIVE, DatastoreTableReference.Kind.STORAGE, name, active)
}
