package com.kakao.actionbase.server.control.topology

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Where the clusters are, as seen by a control instance.
 *
 * ```yaml
 * actionbase:
 *   role: CONTROL
 *   control:
 *     tenants:
 *       kc:
 *         env: prod
 *         namespace: ab_kc
 *         active-url: http://ab-kc.example.net
 *         standby-url: http://ab-kc-standby.example.net   # omit when there is no standby
 * ```
 *
 * `namespace` is declared, not reconciled with the cluster's own config, so a rename on one side
 * drifts silently until routing can check it.
 */
@ConfigurationProperties(prefix = "actionbase.control")
data class TopologyProperties(
    val tenants: Map<String, Tenant> = emptyMap(),
) {
    data class Tenant(
        val env: Env,
        val namespace: String,
        val activeUrl: String,
        val standbyUrl: String? = null,
    )

    fun toTopology(): Topology {
        require(tenants.isNotEmpty()) { "actionbase.control.tenants is empty, but this instance is deployed as CONTROL" }
        return Topology(
            tenants.map { (id, t) ->
                TenantTopology(
                    tenant = id,
                    env = t.env,
                    namespace = t.namespace,
                    sides =
                        buildMap {
                            put(Side.ACTIVE, t.activeUrl.requireBaseUrl(id, Side.ACTIVE))
                            t.standbyUrl?.let { put(Side.STANDBY, it.requireBaseUrl(id, Side.STANDBY)) }
                        },
                )
            },
        )
    }

    private fun String.requireBaseUrl(
        tenant: String,
        side: Side,
    ): String {
        require(isNotBlank()) { "actionbase.control.tenants.$tenant: ${side.name.lowercase()} url is blank" }
        require(startsWith("http://") || startsWith("https://")) {
            "actionbase.control.tenants.$tenant: ${side.name.lowercase()} url must be absolute, got '$this'"
        }
        return trimEnd('/')
    }
}
