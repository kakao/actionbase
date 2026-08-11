package com.kakao.actionbase.server.control.cleanup

import com.kakao.actionbase.server.control.topology.Side

/**
 * The lifecycle of a datastore table, as operators run it.
 *
 * Teardown goes `REPLICATION_OFF -> UNLINK -> DISABLE_TABLE -> DROP_TABLE -> DELETE_METADATA`;
 * setup goes `ENABLE_TABLE -> REPLICATION_ON`. The order is not a style preference - disabling a
 * table while replication is still on breaks standby sync, and dropping one a collection still
 * points at leaves the metadata dangling.
 *
 * Per #470 `table` is the physical HBase table and `collection` the logical v3 entity, so a name
 * carrying `TABLE` never means metadata.
 */
enum class CleanupAction {
    /** Replication scope 1 -> 0. */
    REPLICATION_OFF,

    /** Replication scope 0 -> 1. */
    REPLICATION_ON,

    /** Deactivate the collection and storage bound to the table. */
    UNLINK,

    DISABLE_TABLE,

    ENABLE_TABLE,

    DROP_TABLE,

    /** Delete the metadata left behind once the table is gone. */
    DELETE_METADATA,
    ;

    /** Metadata is not replicated, so the actions that touch it only ever run on the active side. */
    val metadataOnly: Boolean get() = this == UNLINK || this == DELETE_METADATA

    /**
     * Whether the action takes something that cannot be put back: [DROP_TABLE] loses the rows,
     * [DELETE_METADATA] the definition. Everything else is a staging step an operator can undo,
     * which is the line a confirmation prompt belongs on.
     */
    val destructive: Boolean get() = this == DROP_TABLE || this == DELETE_METADATA

    companion object {
        fun of(value: String): CleanupAction =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("unknown action '$value', expected one of ${entries.map { it.name.lowercase() }}")
    }
}

/** A single call the executor will make. */
enum class StepOp {
    REPLICATION_DISABLE,
    REPLICATION_ENABLE,
    DEACTIVATE_COLLECTION,
    DEACTIVATE_STORAGE,
    DISABLE_TABLE,
    ENABLE_TABLE,
    DROP_TABLE,
    DELETE_COLLECTION,
    DELETE_STORAGE,
}

/**
 * Why a plan is empty. Typed rather than prose: an operator staring at a disabled button needs to
 * know which precondition to satisfy, and a test needs to assert that it named the right one.
 */
enum class Precondition {
    /** Every side is already where the action would take it. */
    ALREADY_SATISFIED,

    /** `DISABLE_TABLE` and `DROP_TABLE` need replication off first, or the standby stops tracking. */
    NEEDS_LOCAL_REPLICATION,

    /** `REPLICATION_ON` needs the table enabled first. */
    NEEDS_ENABLED,

    /** `DROP_TABLE` needs the metadata unlinked first. */
    NEEDS_UNLINK,

    /** `DROP_TABLE` needs the table disabled first. */
    NEEDS_DISABLE,

    /** `DELETE_METADATA` is for what is already gone. */
    TABLE_STILL_EXISTS,

    /** `DROP_TABLE` is for what still exists. */
    TABLE_ALREADY_GONE,

    /** Another tenant's metadata still points at this table. */
    HELD_BY_ANOTHER_TENANT,

    /** Nothing binds the table, so there is nothing to unlink or purge. */
    NOTHING_BOUND,
}

data class JobTarget(
    val tenant: String,
    val table: String,
)

data class PlanStep(
    val side: Side,
    val op: StepOp,
    /** The metadata entity a metadata step acts on. */
    val name: String? = null,
)

/**
 * What would happen, or why nothing would.
 *
 * A refusal is a *prediction*: the data plane enforces these invariants itself and remains the one
 * that says no. Repeating the checks here is how a client learns before it asks, not a second source
 * of truth.
 */
data class CleanupPlan(
    val target: JobTarget,
    val action: CleanupAction,
    val ok: Boolean,
    val steps: List<PlanStep> = emptyList(),
    val refusal: Precondition? = null,
    val detail: String? = null,
)

data class JobRequest(
    val targets: List<JobTarget> = emptyList(),
    val action: String,
    val fanout: Boolean = false,
    val dryRun: Boolean = true,
)

data class JobView(
    val dryRun: Boolean,
    val action: CleanupAction,
    val fanout: Boolean,
    val plans: List<CleanupPlan>,
    val failures: List<SideFailure>,
)
