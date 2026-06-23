package com.kakao.actionbase.v2.engine.producer

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import reactor.core.publisher.Mono
import reactor.kotlin.test.test

class ProducerListSpec :
    StringSpec({

        "all producers receive the message" {
            val message = mockk<Log>()
            val producer1 = mockk<Producer>()
            val producer2 = mockk<Producer>()
            every { producer1.produce(message) } returns Mono.empty()
            every { producer2.produce(message) } returns Mono.empty()

            ProducerList(listOf(producer1, producer2)).produce(message).test().verifyComplete()

            verify { producer1.produce(message) }
            verify { producer2.produce(message) }
        }

        "any producer fails, produce fails" {
            val message = mockk<Log>()
            val producer1 = mockk<Producer>()
            val producer2 = mockk<Producer>()
            every { producer1.produce(message) } returns Mono.empty()
            every { producer2.produce(message) } returns Mono.error(RuntimeException("mock error"))

            ProducerList(listOf(producer1, producer2)).produce(message).test().verifyError()
        }

        "empty producer list throws IllegalArgumentException" {
            shouldThrow<IllegalArgumentException> {
                ProducerList(emptyList())
            }
        }

        "fanout works with more than two producers" {
            val message = mockk<Log>()
            val producer1 = mockk<Producer>()
            val producer2 = mockk<Producer>()
            val producer3 = mockk<Producer>()
            every { producer1.produce(message) } returns Mono.empty()
            every { producer2.produce(message) } returns Mono.empty()
            every { producer3.produce(message) } returns Mono.empty()

            ProducerList(listOf(producer1, producer2, producer3)).produce(message).test().verifyComplete()

            verify { producer1.produce(message) }
            verify { producer2.produce(message) }
            verify { producer3.produce(message) }
        }
    })
