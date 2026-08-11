package com.kakao.actionbase.server.control.topology

import java.net.URI

enum class Env {
    DEV,
    TEST,
    PROD,
    ;

    companion object {
        /**
         * Config binding accepts `env: prod`, so a query parameter has to as well - a surface that
         * takes one spelling in yaml and another in a url is a surface people get wrong.
         */
        fun of(value: String): Env =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("unknown env '$value', expected one of ${entries.map { it.name.lowercase() }}")
    }
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
     * Which cluster a side is, as `host:port`. Two tenants configured against the same cluster get
     * the same answer, which is how sharing is noticed without asking anyone to declare it.
     */
    fun cluster(
        tenant: String,
        side: Side,
    ): String = URI(baseUrl(tenant, side)).authority

    /** Fanout on an unpaired tenant reaches the active only - a caller need not know which are paired. */
    fun sidesFor(
        tenant: String,
        fanout: Boolean,
    ): List<Side> {
        val resolved = tenant(tenant)
        return if (fanout && resolved.hasStandby) listOf(Side.ACTIVE, Side.STANDBY) else listOf(Side.ACTIVE)
    }
}
