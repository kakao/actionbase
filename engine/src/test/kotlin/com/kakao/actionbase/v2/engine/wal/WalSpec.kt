package com.kakao.actionbase.v2.engine.wal

import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.producer.Producer
import com.kakao.actionbase.v2.engine.producer.ProducerList

import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import reactor.core.publisher.Mono
import reactor.kotlin.test.test

class WalSpec :
    StringSpec({

        "primary success, wal write succeed" {

            val mockWal1 = mockk<Producer>()
            every { mockWal1.produce(any()) } returns Mono.empty()

            val mockWal2 = mockk<Producer>()
            every { mockWal2.produce(any()) } returns Mono.error(RuntimeException("mock error"))

            val walLog =
                WalLog(
                    EntityName("test", "alias"),
                    EntityName("test", "label"),
                    Edge(1, "src", "tgt", emptyMap<String, Any>()).toTraceEdge(),
                    EdgeOperation.INSERT,
                )
            val wal = DefaultWal(ProducerList(listOf(mockWal1, mockWal2)))
            wal.write(walLog).test().verifyComplete()
        }

        "primary failed, but secondary success, wal write succeed" {

            val mockWal1 = mockk<Producer>()
            every { mockWal1.produce(any()) } returns Mono.empty()

            val mockWal2 = mockk<Producer>()
            every { mockWal2.produce(any()) } returns Mono.error(RuntimeException("mock error"))

            val walLog =
                WalLog(
                    EntityName("test", "alias"),
                    EntityName("test", "label"),
                    Edge(1, "src", "tgt", emptyMap<String, Any>()).toTraceEdge(),
                    EdgeOperation.INSERT,
                )
            val wal = DefaultWal(ProducerList(listOf(mockWal2, mockWal1)))
            wal.write(walLog).test().verifyComplete()
        }

        "all wals failed, wal write failed" {
            val mockWal2 = mockk<Producer>()
            every { mockWal2.produce(any()) } returns Mono.error(RuntimeException("mock error"))

            val mockWal1 = mockk<Producer>()
            every { mockWal1.produce(any()) } returns Mono.error(RuntimeException("mock error"))

            val walLog =
                WalLog(
                    EntityName("test", "alias"),
                    EntityName("test", "label"),
                    Edge(1, "src", "tgt", emptyMap<String, Any>()).toTraceEdge(),
                    EdgeOperation.INSERT,
                )
            val wal = DefaultWal(ProducerList(listOf(mockWal1, mockWal2)))
            wal.write(walLog).test().verifyError()
        }
    })
