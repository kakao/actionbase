package com.kakao.actionbase.v2.engine.producer

import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.util.getLogger

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class ProducerList(
    private val producers: List<Producer>,
) : Producer {
    init {
        require(producers.isNotEmpty())
    }

    companion object {
        private val logger = getLogger()
    }

    override fun produce(message: Log): Mono<Void> =
        Flux
            .fromIterable(producers)
            .flatMap { it.produce(message) }
            .then()

    override fun produceHeartBeat(
        labelName: EntityName,
        hostName: String,
    ): Mono<Void> =
        Flux
            .fromIterable(producers)
            .flatMap { producer ->
                val mono =
                    producer
                        .produceHeartBeat(labelName, hostName)
                        .onErrorContinue { error, _ -> logger.warn("heartbeat send failed: {}", error.toString(), error) }
                mono
            }.collectList()
            .then()
}
