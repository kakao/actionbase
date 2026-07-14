package com.kakao.actionbase.v2.engine.test.cdc

import com.kakao.actionbase.v2.engine.cdc.Cdc
import com.kakao.actionbase.v2.engine.cdc.CdcContext
import com.kakao.actionbase.v2.engine.entity.EntityName

import java.util.concurrent.ConcurrentLinkedQueue

import reactor.core.publisher.Mono

class InMemoryCdc : Cdc {
    private val publishedCdcSet = ConcurrentLinkedQueue<CdcContext>()

    fun readCdc(): List<CdcContext> = publishedCdcSet.toList()

    override fun write(log: CdcContext): Mono<Void> {
        publishedCdcSet.add(log)
        return Mono.empty()
    }

    override fun writeHeartBeat(
        labelName: EntityName,
        hostName: String,
    ): Mono<Void> = Mono.empty()

    fun init() {
        publishedCdcSet.clear()
    }
}
