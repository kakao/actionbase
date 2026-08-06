package com.kakao.actionbase.server.control.topology

enum class Env {
    DEV,
    TEST,
    PROD,
}

/** Which cluster of a tenant a request is aimed at. */
enum class Side {
    ACTIVE,
    STANDBY,
}

data class TenantTopology(
    val tenant: String,
    val env: Env,
    val namespace: String,
    val sides: Map<Side, String>,
) {
    val hasStandby: Boolean get() = Side.STANDBY in sides
}

class UnknownTenantException(
    tenant: String,
    known: Collection<String>,
) : IllegalArgumentException("unknown tenant '$tenant', configured: ${known.sorted()}")

class UnknownSideException(
    tenant: String,
    side: Side,
) : IllegalArgumentException("tenant '$tenant' has no ${side.name.lowercase()} cluster configured")

/** Resolved deployment topology. Immutable for the process lifetime. */
class Topology(
    tenants: Collection<TenantTopology>,
) {
    private val byTenant: Map<String, TenantTopology> = tenants.associateBy { it.tenant }

    val tenants: List<TenantTopology> = tenants.sortedBy { it.tenant }

    fun tenant(tenant: String): TenantTopology = byTenant[tenant] ?: throw UnknownTenantException(tenant, byTenant.keys)

    fun baseUrl(
        tenant: String,
        side: Side,
    ): String = tenant(tenant).sides[side] ?: throw UnknownSideException(tenant, side)

    /**
     * Sides a request should reach. Fanout is opt-in and forgiving about the standby: asking for it
     * on a tenant that has none reaches the active only, because a caller should not have to know
     * which tenants are paired.
     */
    fun sidesFor(
        tenant: String,
        fanout: Boolean,
    ): List<Side> {
        val resolved = tenant(tenant)
        return if (fanout && resolved.hasStandby) listOf(Side.ACTIVE, Side.STANDBY) else listOf(Side.ACTIVE)
    }
}
