package com.kakao.actionbase.v2.engine.test.cdc

import com.kakao.actionbase.v2.engine.cdc.Cdc
import com.kakao.actionbase.v2.engine.cdc.CdcFactory
import com.kakao.actionbase.v2.engine.client.kafka.KafkaClientFactory

import java.util.Properties

object InMemoryCdcFactory : CdcFactory {
    override fun create(
        properties: List<Properties>,
        kafkaClientFactory: KafkaClientFactory,
    ): Cdc = InMemoryCdc()
}
