package com.kakao.actionbase.v2.engine.storage.slatedb

import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.GraphConfig
import com.kakao.actionbase.v2.engine.client.kafka.impl.DefaultKafkaClientFactory
import com.kakao.actionbase.v2.engine.client.web.impl.DefaultWebClientFactory
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.metadata.StorageType
import com.kakao.actionbase.v2.engine.test.GraphFixtures
import com.kakao.actionbase.v2.engine.test.cdc.InMemoryCdcFactory
import com.kakao.actionbase.v2.engine.test.wal.InMemoryWalFactory

import java.util.UUID

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

import reactor.kotlin.test.test

class SlateDbStorageTest {
    private lateinit var graph: Graph

    private fun createGraph(): Graph {
        val config =
            GraphConfig
                .Builder()
                .withMetastoreUrl("jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=MYSQL")
                .build()
        return Graph.create(config, InMemoryWalFactory, InMemoryCdcFactory, DefaultKafkaClientFactory, DefaultWebClientFactory)
    }

    @BeforeEach
    fun setUp() {
        graph = createGraph()
        graph.updateAllMetadata().block()
    }

    @AfterEach
    fun tearDown() {
        graph.close()
    }

    @Test
    fun `create SlateDB storage entity`() {
        val conf =
            jacksonObjectMapper().createObjectNode().apply {
                put("path", "test-data")
                put("url", "file:///tmp/slatedb-test")
            }

        GraphFixtures.createStorage(graph, "slatedb_test", StorageType.SLATEDB, conf)

        graph.storageDdl
            .getSingle(EntityName.fromOrigin("slatedb_test"))
            .test()
            .assertNext { storage ->
                assert(storage.type == StorageType.SLATEDB)
                assert(storage.active)
            }.verifyComplete()
    }
}
