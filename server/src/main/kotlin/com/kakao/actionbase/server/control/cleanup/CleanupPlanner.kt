package com.kakao.actionbase.server.control.cleanup

import com.kakao.actionbase.server.control.topology.Side
import com.kakao.actionbase.v2.engine.service.ddl.DatastoreTableReference

/**
 * Decides which calls an action needs, in which order, on which sides.
 *
 * Pure: everything it knows comes from the projected [TableView]. Two properties matter more than
 * the individual rules.
 *
 * Fanout is partial. Only the sides not already at the target state get a step, so an active/standby
 * pair that has drifted apart is brought together rather than refused - an operator should not have
 * to fix the drift by hand before asking for the action.
 *
 * A refusal is a prediction. The data plane enforces these invariants and stays the one that says
 * no; predicting them here is how a client learns before it asks.
 */
object CleanupPlanner {
    fun plan(
        action: CleanupAction,
        table: TableView,
        fanout: Boolean,
    ): CleanupPlan {
        val target = JobTarget(table.tenant, table.name)
        val sides = table.sidesFor(action, fanout)

        return when (action) {
            CleanupAction.REPLICATION_OFF -> table.replication(target, action, sides, from = 1, to = StepOp.REPLICATION_DISABLE)
            CleanupAction.REPLICATION_ON -> table.toReplicationOn(target, action, sides)
            CleanupAction.DISABLE_TABLE -> table.toDisabled(target, action, sides)
            CleanupAction.ENABLE_TABLE -> table.toEnabled(target, action, sides)
            CleanupAction.UNLINK -> table.toUnlinked(target, action, sides)
            CleanupAction.DROP_TABLE -> table.toDropped(target, action, sides)
            CleanupAction.DELETE_METADATA -> table.toMetadataDeleted(target, action, sides)
        }
    }

    /** Which sides an action may touch: metadata lives with the active, table state can fan out. */
    private fun TableView.sidesFor(
        action: CleanupAction,
        fanout: Boolean,
    ): List<Side> =
        when {
            action.metadataOnly -> listOf(Side.ACTIVE).filter { it in sides }
            fanout -> Side.entries.filter { it in sides }
            else -> listOf(Side.ACTIVE).filter { it in sides }
        }

    private fun TableView.replication(
        target: JobTarget,
        action: CleanupAction,
        sides: List<Side>,
        from: Int,
        to: StepOp,
    ): CleanupPlan {
        val targets = sides.filter { state(it).present && state(it).replicationScope == from }
        if (targets.isEmpty()) return refuse(target, action, Precondition.ALREADY_SATISFIED, "every side is already there")
        return CleanupPlan(target, action, ok = true, steps = targets.map { PlanStep(it, to) })
    }

    private fun TableView.toReplicationOn(
        target: JobTarget,
        action: CleanupAction,
        sides: List<Side>,
    ): CleanupPlan {
        val targets = sides.filter { state(it).present && state(it).replicationScope == 0 }
        if (targets.isEmpty()) return refuse(target, action, Precondition.ALREADY_SATISFIED, "every side already replicates")
        targets.firstOrNull { state(it).enabled == false }?.let {
            return refuse(target, action, Precondition.NEEDS_ENABLED, "$it is disabled: enable it before turning replication on")
        }
        return CleanupPlan(target, action, ok = true, steps = targets.map { PlanStep(it, StepOp.REPLICATION_ENABLE) })
    }

    private fun TableView.toDisabled(
        target: JobTarget,
        action: CleanupAction,
        sides: List<Side>,
    ): CleanupPlan {
        val targets = sides.filter { state(it).enabled == true }
        if (targets.isEmpty()) return refuse(target, action, Precondition.ALREADY_SATISFIED, "every side is already disabled")
        heldByAnother()?.let { return refuse(target, action, Precondition.HELD_BY_ANOTHER_TENANT, it) }
        targets.firstOrNull { state(it).replicationScope != 0 }?.let {
            return refuse(
                target,
                action,
                Precondition.NEEDS_LOCAL_REPLICATION,
                "$it still replicates: disabling with replication on breaks standby sync, so turn replication off first",
            )
        }
        return CleanupPlan(target, action, ok = true, steps = targets.map { PlanStep(it, StepOp.DISABLE_TABLE) })
    }

    private fun TableView.toEnabled(
        target: JobTarget,
        action: CleanupAction,
        sides: List<Side>,
    ): CleanupPlan {
        val targets = sides.filter { state(it).enabled == false }
        if (targets.isEmpty()) return refuse(target, action, Precondition.ALREADY_SATISFIED, "every side is already enabled")
        heldByAnother()?.let { return refuse(target, action, Precondition.HELD_BY_ANOTHER_TENANT, it) }
        return CleanupPlan(target, action, ok = true, steps = targets.map { PlanStep(it, StepOp.ENABLE_TABLE) })
    }

    private fun TableView.toUnlinked(
        target: JobTarget,
        action: CleanupAction,
        sides: List<Side>,
    ): CleanupPlan {
        val side = sides.firstOrNull() ?: return refuse(target, action, Precondition.NOTHING_BOUND, "no active side to unlink on")
        val live = references.filter { it.active }
        if (live.isEmpty()) return refuse(target, action, Precondition.ALREADY_SATISFIED, "nothing active still binds this table")
        // Labels first: a storage that a label still reaches through cannot be deactivated yet.
        return CleanupPlan(target, action, ok = true, steps = live.stepsOn(side, StepOp.DEACTIVATE_COLLECTION, StepOp.DEACTIVATE_STORAGE))
    }

    private fun TableView.toDropped(
        target: JobTarget,
        action: CleanupAction,
        sides: List<Side>,
    ): CleanupPlan {
        val targets = sides.filter { state(it).present }
        if (targets.isEmpty()) return refuse(target, action, Precondition.TABLE_ALREADY_GONE, "nothing left to drop: delete the metadata instead")
        heldByAnother()?.let { return refuse(target, action, Precondition.HELD_BY_ANOTHER_TENANT, it) }
        if (references.any { it.active }) {
            return refuse(target, action, Precondition.NEEDS_UNLINK, "metadata still binds this table: unlink first")
        }
        targets.firstOrNull { state(it).replicationScope != 0 }?.let {
            return refuse(target, action, Precondition.NEEDS_LOCAL_REPLICATION, "$it still replicates: turn replication off first")
        }
        targets.firstOrNull { state(it).enabled != false }?.let {
            return refuse(target, action, Precondition.NEEDS_DISABLE, "$it is still enabled: disable it first")
        }
        return CleanupPlan(target, action, ok = true, steps = targets.map { PlanStep(it, StepOp.DROP_TABLE) })
    }

    private fun TableView.toMetadataDeleted(
        target: JobTarget,
        action: CleanupAction,
        sides: List<Side>,
    ): CleanupPlan {
        val side = sides.firstOrNull() ?: return refuse(target, action, Precondition.NOTHING_BOUND, "no active side to delete metadata on")
        this.sides.entries.firstOrNull { it.value.present }?.let {
            return refuse(target, action, Precondition.TABLE_STILL_EXISTS, "${it.key} still has the table: drop it first")
        }
        if (references.isEmpty()) return refuse(target, action, Precondition.ALREADY_SATISFIED, "no metadata left to delete")
        return CleanupPlan(target, action, ok = true, steps = references.stepsOn(side, StepOp.DELETE_COLLECTION, StepOp.DELETE_STORAGE))
    }

    /** Collections before storages: the storage is reached through the collection, so it goes second. */
    private fun List<ReferenceView>.stepsOn(
        side: Side,
        collectionOp: StepOp,
        storageOp: StepOp,
    ): List<PlanStep> =
        filter { it.kind == DatastoreTableReference.Kind.LABEL }.map { PlanStep(side, collectionOp, it.name) } +
            filter { it.kind == DatastoreTableReference.Kind.STORAGE }.map { PlanStep(side, storageOp, it.name) }

    private fun TableView.heldByAnother(): String? =
        references
            .filter { it.active && it.tenant != tenant }
            .map { it.tenant }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.let { "still bound by $it: that tenant has to release it first" }

    private fun TableView.state(side: Side): SideState = sides.getValue(side)

    private fun refuse(
        target: JobTarget,
        action: CleanupAction,
        precondition: Precondition,
        detail: String,
    ) = CleanupPlan(target, action, ok = false, refusal = precondition, detail = detail)
}
