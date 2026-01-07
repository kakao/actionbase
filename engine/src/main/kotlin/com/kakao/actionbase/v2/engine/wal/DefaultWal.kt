package com.kakao.actionbase.v2.engine.wal

import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.producer.Producer

import reactor.core.publisher.Mono

class DefaultWal(
    val producer: Producer,
) : Wal {
    override fun write(walLog: WalLog): Mono<Void> = producer.produce(walLog)

    override fun writeHeartBeat(
        labelName: EntityName,
        hostName: String,
    ): Mono<Void> = producer.produceHeartBeat(labelName, hostName)
}
