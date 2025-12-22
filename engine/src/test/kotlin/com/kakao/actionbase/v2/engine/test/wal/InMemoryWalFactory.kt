package com.kakao.actionbase.v2.engine.test.wal

import com.kakao.actionbase.v2.engine.client.kafka.KafkaClientFactory
import com.kakao.actionbase.v2.engine.wal.Wal
import com.kakao.actionbase.v2.engine.wal.WalFactory

import java.util.Properties

object InMemoryWalFactory : WalFactory {
    override fun create(
        properties: List<Properties>,
        kafkaClientFactory: KafkaClientFactory,
    ): Wal = InMemoryWal()
}
