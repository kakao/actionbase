package com.kakao.actionbase.server.test

import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.producer.Log
import com.kakao.actionbase.v2.engine.producer.LoggerProducer
import com.kakao.actionbase.v2.engine.producer.Producer
import com.kakao.actionbase.v2.engine.producer.ProducerList
import com.kakao.actionbase.v2.engine.wal.DefaultWal
import com.kakao.actionbase.v2.engine.wal.Wal

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

import reactor.core.publisher.Mono

@TestConfiguration
class InvalidWalConfig {
    @Bean
    @Primary
    fun wal(): Wal =
        DefaultWal(
            ProducerList(
                listOf(
                    LoggerProducer(),
                    object : Producer {
                        override fun produce(message: Log): Mono<Void> = Mono.error(RuntimeException("simulated invalid producer"))

                        override fun produceHeartBeat(
                            labelName: EntityName,
                            hostName: String,
                        ): Mono<Void> = Mono.empty()
                    },
                ),
            ),
        )
}
