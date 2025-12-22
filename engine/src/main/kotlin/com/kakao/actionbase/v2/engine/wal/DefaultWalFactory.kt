package com.kakao.actionbase.v2.engine.wal

import com.kakao.actionbase.v2.engine.client.kafka.KafkaClientFactory
import com.kakao.actionbase.v2.engine.producer.KafkaProducer
import com.kakao.actionbase.v2.engine.producer.LoggerProducer
import com.kakao.actionbase.v2.engine.producer.Producer
import com.kakao.actionbase.v2.engine.producer.ProducerList

import java.util.Properties

object DefaultWalFactory : WalFactory {
    override fun create(
        properties: List<Properties>,
        kafkaClientFactory: KafkaClientFactory,
    ): Wal {
        val producers =
            if (properties.isNotEmpty()) {
                properties.map { createWalProducer(it, kafkaClientFactory) }
            } else {
                listOf(LoggerProducer())
            }.let { ProducerList(it) }
        return DefaultWal(producers)
    }

    private fun createWalProducer(
        properties: Properties,
        kafkaClientFactory: KafkaClientFactory,
    ): Producer =
        if (properties.getProperty("bootstrap.servers", "") != "" && properties.getProperty("topic", "") != "") {
            val client = kafkaClientFactory.create(properties)
            KafkaProducer.createWalProducer(client, properties)
        } else {
            LoggerProducer()
        }
}
