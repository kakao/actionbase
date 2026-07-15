package com.kakao.actionbase.v2.engine

import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.metadata.Metadata
import com.kakao.actionbase.v2.engine.test.GraphFixtures
import com.kakao.actionbase.v2.engine.test.shouldContainServicesExactly
import com.kakao.actionbase.v2.engine.test.shouldContainStoragesExactly
import com.kakao.actionbase.v2.engine.test.shouldContainSystemLabelsExactly
import com.kakao.actionbase.v2.engine.test.testFixtures

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import reactor.kotlin.test.test

class InitialSpec :
    StringSpec({

        lateinit var graph: Graph

        beforeTest {
            graph = GraphFixtures.create(withTestData = false)
        }

        afterTest {
            graph.close()
        }

        "check initial state" {
            graph shouldContainServicesExactly GraphFixtures.defaultServices
            graph shouldContainStoragesExactly GraphFixtures.defaultStorages
            graph shouldContainSystemLabelsExactly GraphFixtures.defaultLabels

            // use toString for equality check
            graph.testFixtures
                .getLocalMetadata()
                .map {
                    println("decodedValue: $it")
                }.collectList()
                .block()

            graph.testFixtures
                .getLocalMetadata()
                .filter { it.labelId != Metadata.infoLabelEntity.id }
                .map { listOf(EntityName.withPhase(it.src.toString(), it.tgt.toString()), it.labelId, it.direction) }
                .collectList()
                .test()
                .assertNext {
                    it shouldContainExactlyInAnyOrder GraphFixtures.defaultMetadata
                }.verifyComplete()

            // global metadata is empty
            graph.testFixtures
                .getGlobalMetadata()
                .test()
                .verifyComplete()
        }
    })
