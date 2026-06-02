package com.kakao.actionbase.v2.engine.dml

import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.audit.Audit
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.label.EdgeOperationStatus
import com.kakao.actionbase.v2.engine.label.InsertEdgeRequest
import com.kakao.actionbase.v2.engine.sql.select
import com.kakao.actionbase.v2.engine.sql.toRowFlux
import com.kakao.actionbase.v2.engine.test.GraphFixtures
import com.kakao.actionbase.v2.engine.test.shouldContainTestLabel
import com.kakao.actionbase.v2.engine.test.testFixtures
import com.kakao.actionbase.v2.engine.util.objectMapper

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import reactor.kotlin.test.test

class DmlTests :
    StringSpec({

        lateinit var graph: Graph

        beforeSpec {
            graph = GraphFixtures.create()
        }

        afterSpec {
            graph.close()
        }

        "check simple insert" {

            graph.testFixtures.createLabel(EntityName("test", "test"))
            graph shouldContainTestLabel EntityName("test", "test")

            val request =
                InsertEdgeRequest(
                    label = "test.test",
                    edges = listOf(Edge(1L, "a", "b", mapOf("permission" to "me", "json" to mapOf("a" to 1)))),
                    audit = Audit.default,
                    requestId = "no_request_id",
                )

            graph
                .upsert(request)
                .test()
                .assertNext {
                    it.result.map { result -> result.status } shouldContainAll listOf(EdgeOperationStatus.CREATED)
                }.verifyComplete()

            graph
                .queryScan(EntityName("test", "test"), "a")
                .select("src", "tgt")
                .toRowFlux()
                .test()
                .assertNext {
                    it.row.array shouldBe arrayOf("a", "b")
                }.verifyComplete()

            graph
                .queryScan(EntityName("test", "test"), "a")
                .test()
                .assertNext {
                    val node = objectMapper.createObjectNode().apply { put("a", 1) }
                    it.rows[0].array shouldBe arrayOf(Direction.OUT.name, 1L, "a", "b", "me", null, node)
                }.verifyComplete()
        }
    })
