package com.kakao.actionbase.v2.engine.cdc

import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.label.EdgeOperationStatus
import com.kakao.actionbase.v2.engine.producer.Producer
import com.kakao.actionbase.v2.engine.producer.ProducerList

import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import reactor.core.publisher.Mono
import reactor.kotlin.test.test

class CdcSpec :
    StringSpec({

        "all producers succeed, cdc write succeeds" {

            val mockWal1 = mockk<Producer>()
            every { mockWal1.produce(any()) } returns Mono.empty()

            val mockWal2 = mockk<Producer>()
            every { mockWal2.produce(any()) } returns Mono.empty()

            val cdcLog =
                CdcContext(
                    EntityName("test", "alias"),
                    Edge(1, "src", "tgt", emptyMap<String, Any>()).toTraceEdge(),
                    EdgeOperation.INSERT,
                    EdgeOperationStatus.CREATED,
                    null,
                    null,
                    0,
                    EntityName("test", "alias"),
                    emptyList(),
                )
            val cdc = DefaultCdc(ProducerList(listOf(mockWal1, mockWal2)))
            cdc.write(cdcLog).test().verifyComplete()
        }

        "any producer fails, cdc write fails" {

            val mockWal1 = mockk<Producer>()
            every { mockWal1.produce(any()) } returns Mono.empty()

            val mockWal2 = mockk<Producer>()
            every { mockWal2.produce(any()) } returns Mono.error(RuntimeException("mock error"))

            val cdcLog =
                CdcContext(
                    EntityName("test", "alias"),
                    Edge(1, "src", "tgt", emptyMap<String, Any>()).toTraceEdge(),
                    EdgeOperation.INSERT,
                    EdgeOperationStatus.CREATED,
                    null,
                    null,
                    0,
                    EntityName("test", "alias"),
                    emptyList(),
                )
            val cdc = DefaultCdc(ProducerList(listOf(mockWal1, mockWal2)))
            cdc.write(cdcLog).test().verifyError()
        }
    })
