package com.kakao.actionbase.server.control.cluster

import com.kakao.actionbase.server.control.topology.Env
import com.kakao.actionbase.server.control.topology.Side
import com.kakao.actionbase.server.control.topology.Topology
import com.kakao.actionbase.server.control.topology.TopologyProperties

import java.time.Duration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient

import reactor.core.publisher.Mono

// No stub server: the exchange function stands in for the clusters, so nothing binds a port.
class ClusterClientTest {
    private val recorded = mutableListOf<ClientRequest>()

    private val stringBody = object : ParameterizedTypeReference<String>() {}

    @Test
    fun `reaches every configured side and keeps their order`() {
        val client = clusterClient { request -> respond("from ${request.url().host}") }

        val sides = client.get("alpha", "/graph/v3/datastore/hbase/tables", fanout = true, stringBody).block()!!

        assertEquals(listOf(Side.ACTIVE, Side.STANDBY), sides.map { it.side })
        assertEquals(
            listOf(
                "http://active.example.net/graph/v3/datastore/hbase/tables",
                "http://standby.example.net/graph/v3/datastore/hbase/tables",
            ),
            recorded.map { it.url().toString() },
        )
    }

    @Test
    fun `without fanout only the active side is reached`() {
        val client = clusterClient { respond("ok") }

        val sides = client.get("alpha", "/tables", fanout = false, stringBody).block()!!

        assertEquals(listOf(Side.ACTIVE), sides.map { it.side })
        assertEquals(1, recorded.size)
    }

    // The point of the per-side shape: a broken standby must not cost the operator the active answer.
    @Test
    fun `a failing side does not hide the other`() {
        val client =
            clusterClient { request ->
                if (request.url().host == "standby.example.net") {
                    Mono.error(IllegalStateException("connection refused"))
                } else {
                    respond("healthy")
                }
            }

        val sides = client.get("alpha", "/tables", fanout = true, stringBody).block()!!

        val active = sides.single { it.side == Side.ACTIVE }
        assertTrue(active.ok)
        assertEquals("healthy", active.body)

        val standby = sides.single { it.side == Side.STANDBY }
        assertTrue(!standby.ok)
        assertNull(standby.body)
        assertTrue(standby.error!!.contains("connection refused"), standby.error)
    }

    @Test
    fun `a non 2xx side reports the status rather than a body`() {
        val client = clusterClient { ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build().toMono() }

        val sides = client.get("alpha", "/tables", fanout = false, stringBody).block()!!

        assertTrue(!sides.single().ok)
        assertTrue(sides.single().error!!.contains("500"), sides.single().error)
    }

    @Test
    fun `a side that never answers is failed by the timeout`() {
        val client =
            clusterClient(timeout = Duration.ofMillis(50)) {
                respond("too late").delayElement(Duration.ofSeconds(5))
            }

        val sides = client.get("alpha", "/tables", fanout = false, stringBody).block()!!

        assertTrue(!sides.single().ok)
    }

    // The cluster must see who is asking, not who is relaying.
    @Test
    fun `forwards the caller's identity`() {
        val client = clusterClient { respond("ok") }

        client
            .get(
                "alpha",
                "/tables",
                fanout = false,
                stringBody,
                headers = mapOf("Authorization" to "token", "Actor-ROLE" to "ADMIN"),
            ).block()

        val sent = recorded.single().headers()
        assertEquals("token", sent.getFirst("Authorization"))
        assertEquals("ADMIN", sent.getFirst("Actor-ROLE"))
    }

    @Test
    fun `an unpaired tenant ignores fanout instead of failing`() {
        val client = clusterClient { respond("ok") }

        val sides = client.get("solo", "/tables", fanout = true, stringBody).block()!!

        assertEquals(listOf(Side.ACTIVE), sides.map { it.side })
    }

    private fun respond(body: String): Mono<ClientResponse> =
        ClientResponse
            .create(HttpStatus.OK)
            .header("Content-Type", MediaType.TEXT_PLAIN_VALUE)
            .body(body)
            .build()
            .toMono()

    private fun ClientResponse.toMono(): Mono<ClientResponse> = Mono.just(this)

    private fun clusterClient(
        timeout: Duration = Duration.ofSeconds(5),
        exchange: (ClientRequest) -> Mono<ClientResponse>,
    ): ClusterClient {
        val webClient =
            WebClient
                .builder()
                .exchangeFunction { request ->
                    recorded += request
                    exchange(request)
                }.build()
        return ClusterClient(topology(), webClient, timeout)
    }

    private fun topology(): Topology =
        TopologyProperties(
            mapOf(
                "alpha" to
                    TopologyProperties.Tenant(
                        env = Env.PROD,
                        namespace = "ab_alpha",
                        activeUrl = "http://active.example.net",
                        standbyUrl = "http://standby.example.net",
                    ),
                "solo" to
                    TopologyProperties.Tenant(
                        env = Env.DEV,
                        namespace = "ab_solo",
                        activeUrl = "http://solo.example.net",
                    ),
            ),
        ).toTopology()
}
