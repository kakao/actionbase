package com.kakao.actionbase.v2.engine.test.wal

import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.wal.Wal
import com.kakao.actionbase.v2.engine.wal.WalLog

import java.util.concurrent.ConcurrentLinkedQueue

import reactor.core.publisher.Mono

class InMemoryWal : Wal {
    private val publishedWalSet = ConcurrentLinkedQueue<WalLog>()

    fun readWal(): List<WalLog> = publishedWalSet.toList()

    override fun write(walLog: WalLog): Mono<Void> =
        Mono
            .fromCallable {
                publishedWalSet.add(walLog)
            }.then()

    override fun writeHeartBeat(
        labelName: EntityName,
        hostName: String,
    ): Mono<Void> = Mono.empty()

    fun init() {
        publishedWalSet.clear()
    }
}
