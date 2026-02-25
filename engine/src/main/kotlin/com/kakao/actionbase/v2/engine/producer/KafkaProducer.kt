package com.kakao.actionbase.v2.engine.producer

import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.core.metadata.MutationMode
import com.kakao.actionbase.v2.engine.cdc.CdcContext
import com.kakao.actionbase.v2.engine.client.kafka.KafkaClient
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.label.EdgeOperationStatus
import com.kakao.actionbase.v2.engine.metadata.MutationModeContext
import com.kakao.actionbase.v2.engine.wal.WalLog

import java.util.Properties

import reactor.core.publisher.Mono

class KafkaProducer(
    private val client: KafkaClient,
    private val properties: Properties,
    val makeHeartbeat: (labelName: EntityName, edge: Edge) -> Log,
) : Producer {
    companion object {
        fun createWalProducer(
            client: KafkaClient,
            properties: Properties,
        ): KafkaProducer =
            KafkaProducer(client, properties) { entityName, edge ->
                WalLog(
                    entityName,
                    entityName,
                    edge.toTraceEdge(),
                    EdgeOperation.INSERT,
                    MutationModeContext.of(table = MutationMode.IGNORE, request = MutationMode.SYNC, force = true),
                )
            }

        fun createCdcProducer(
            client: KafkaClient,
            properties: Properties,
        ): KafkaProducer =
            KafkaProducer(client, properties) { entityName, edge ->
                CdcContext(entityName, edge.toTraceEdge(), EdgeOperation.INSERT, EdgeOperationStatus.CREATED, null, null, 0L, null, emptyList())
            }
    }

    private val brokerHost: String
        get() = properties.getProperty("bootstrap.servers") ?: "unknown"

    override fun produce(message: Log): Mono<Void> = client.send(Mono.just(message.toJsonBytes()))

    override fun produceHeartBeat(
        labelName: EntityName,
        hostName: String,
    ): Mono<Void> {
        val edge = Edge(System.currentTimeMillis(), hostName, brokerHost)
        val heartbeat = makeHeartbeat(labelName, edge)
        return produce(heartbeat)
    }
}
