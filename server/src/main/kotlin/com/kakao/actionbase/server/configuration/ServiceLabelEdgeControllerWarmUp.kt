package com.kakao.actionbase.server.configuration

import com.kakao.actionbase.server.filter.TokenAuthenticationFilter
import com.kakao.actionbase.server.payload.EdgesRequest
import com.kakao.actionbase.v2.core.edge.Edge

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.WebFilter

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
@ConditionalOnProperty(name = ["kc.graph.warmup.enabled"], havingValue = "true")
class ServiceLabelEdgeControllerWarmUp(
    @Value("\${server.port:8080}") private val serverPort: Int,
    graphProperties: GraphProperties,
    private val serverProperties: ServerProperties,
    @Qualifier("tokenAuthenticationFilter") private val tokenAuthenticationFilter: WebFilter,
) : ApplicationListener<ApplicationReadyEvent> {
    private val log: Logger = LoggerFactory.getLogger(ServiceLabelEdgeControllerWarmUp::class.java)
    private val warmUpConfig = graphProperties.warmUp

    private val warmUpToken = graphProperties.tokens.lastOrNull()
    private val webClient: WebClient =
        WebClient
            .builder()
            .baseUrl("http://localhost:$serverPort")
            .apply {
                if (warmUpToken != null) {
                    it.defaultHeader("Authorization", warmUpToken)
                }
            }.build()

    init {
        log.info("WarmUp is added. serverPort: $serverPort, config: $warmUpConfig")
    }

    private val writeMethods = listOf(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)

    fun warmUpInfo(
        i: Int,
        props: Boolean,
    ): Edge =
        Edge(
            System.currentTimeMillis(),
            "origin",
            "warmup_$i",
            if (props) mapOf("props_active" to true, "message" to "warmup") else emptyMap(),
        )

    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        val writeWarmUp =
            Flux
                .range(0, warmUpConfig.count)
                .flatMap({ i ->
                    if (tokenAuthenticationFilter is TokenAuthenticationFilter) {
                        tokenAuthenticationFilter.warmUp()
                    }
                    val method = writeMethods.random()
                    val uri = "/graph/v2/service/sys/label/info/edge"
                    webClient
                        .method(method)
                        .uri(uri)
                        .apply {
                            when (method) {
                                HttpMethod.POST, HttpMethod.PUT -> {
                                    bodyValue(EdgesRequest(listOf(warmUpInfo(i, props = true))))
                                }
                                HttpMethod.DELETE -> {
                                    bodyValue(EdgesRequest(listOf(warmUpInfo(i, props = false))))
                                }
                                else -> {}
                            }
                        }.retrieve()
                        .bodyToMono(String::class.java)
                }, warmUpConfig.concurrency)
                .count()

        val readWarmUp =
            Flux
                .range(0, warmUpConfig.count)
                .flatMap({ i ->
                    if (tokenAuthenticationFilter is TokenAuthenticationFilter) {
                        tokenAuthenticationFilter.warmUp()
                    }
                    webClient
                        .get()
                        .uri("/graph/v2/service/sys/label/info/edge?src=origin&tgt=warmup")
                        .retrieve()
                        .bodyToMono(String::class.java)
                }, warmUpConfig.concurrency)
                .count()

        log.info("ServiceLabelEdgeControllerWarmUp is started")

        Mono
            .defer {
                if (serverProperties.readOnly) {
                    log.info("Skipping write warm-up (read-only mode)")
                    Mono.empty()
                } else {
                    writeWarmUp.then()
                }
            }.then(Mono.defer { readWarmUp.then() })
            .then(
                webClient
                    .put()
                    .uri("/graph/health/readiness")
                    .retrieve()
                    .bodyToMono(String::class.java)
                    .map {
                        log.info("💚 readiness UP  💚")
                    }.doOnError { ex ->
                        log.error("💔 readiness DOWN  💔", ex)
                    },
            ).subscribe {
                log.info("Warm-up is completed.")
            }
    }
}
