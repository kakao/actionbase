package com.kakao.actionbase.v2.engine.wal

import com.kakao.actionbase.v2.engine.client.kafka.KafkaClientFactory

import java.util.Properties

interface WalFactory {
    fun create(
        properties: List<Properties>,
        kafkaClientFactory: KafkaClientFactory,
    ): Wal
}
