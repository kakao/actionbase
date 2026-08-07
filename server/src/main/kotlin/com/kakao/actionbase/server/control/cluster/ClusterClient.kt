package com.kakao.actionbase.server.control.cluster

import com.kakao.actionbase.server.control.topology.Side
import com.kakao.actionbase.server.control.topology.Topology

import java.time.Duration

import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.web.reactive.function.client.WebClient

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/** One side's answer. A side that failed carries its reason rather than nothing. */
data class SideResponse<T>(
    val side: Side,
    val body: T? = null,
    val error: String? = null,
) {
    val ok: Boolean get() = error == null
}

/**
 * Reaches the clusters of one tenant.
 *
 * A failing side does not fail the call: an operator needs to know which cluster is unwell, and the
 * other side's answer is still worth having. Sides come back in a stable order however slow each was.
 */
class ClusterClient(
    private val topology: Topology,
    private val webClient: WebClient,
    private val timeout: Duration,
) {
    private val log = LoggerFactory.getLogger(ClusterClient::class.java)

    fun <T : Any> get(
        tenant: String,
        path: String,
        fanout: Boolean,
        type: ParameterizedTypeReference<T>,
        headers: Map<String, String> = emptyMap(),
    ): Mono<List<SideResponse<T>>> =
        Flux
            .fromIterable(topology.sidesFor(tenant, fanout))
            .flatMapSequential { side -> get(tenant, side, path, type, headers) }
            .collectList()

    private fun <T : Any> get(
        tenant: String,
        side: Side,
        path: String,
        type: ParameterizedTypeReference<T>,
        headers: Map<String, String>,
    ): Mono<SideResponse<T>> =
        webClient
            .get()
            .uri(topology.baseUrl(tenant, side) + path)
            .headers { outgoing -> headers.forEach { (name, value) -> outgoing.set(name, value) } }
            .retrieve()
            .bodyToMono(type)
            .timeout(timeout)
            .map { SideResponse(side, body = it) }
            .onErrorResume { failure ->
                log.warn("Cluster call failed. tenant={}, side={}, path={}", tenant, side, path, failure)
                Mono.just(SideResponse<T>(side, error = failure.describe()))
            }

    /** The cause carries the reason a connection failed, so it is surfaced next to the message. */
    private fun Throwable.describe(): String {
        val message = message ?: this::class.simpleName
        val cause = cause?.message?.takeIf { it != message }
        return listOfNotNull(message, cause).joinToString(" / ")
    }
}
