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
 * Deployment config, read once at startup. A tenant here is nothing more than the URLs its cluster
 * answers on - the control plane holds no per-request state and opens no datastore of its own.
 *
 * `namespace` is declared, not discovered: it restates what the target cluster has in its own
 * `actionbase.datastore.configuration`, and nothing reconciles the two, so a rename on one side
 * drifts silently. Verifying it against what the cluster reports needs a call to that cluster,
 * which arrives with request routing.
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
        // A control plane with no clusters to reach is a misconfiguration, and one that would only
        // show up as an empty topology response. Same reasoning as the url checks below: fail the
        // boot, not the first request.
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
