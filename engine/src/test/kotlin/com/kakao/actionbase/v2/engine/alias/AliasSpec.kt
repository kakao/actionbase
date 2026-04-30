package com.kakao.actionbase.v2.engine.alias

import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.label.EdgeOperationStatus
import com.kakao.actionbase.v2.engine.service.ddl.AliasCreateRequest
import com.kakao.actionbase.v2.engine.service.ddl.DdlStatus
import com.kakao.actionbase.v2.engine.sql.toRowFlux
import com.kakao.actionbase.v2.engine.test.GraphFixtures

import kotlin.math.absoluteValue
import kotlin.random.Random

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import reactor.kotlin.test.test

class AliasSpec :
    StringSpec({

        lateinit var graph: Graph

        beforeTest {
            graph = GraphFixtures.create()
        }

        afterTest {
            graph.close()
        }

        "create alias and get alias" {
            graph.aliasDdl
                .create(
                    EntityName("sys", "service_alias"),
                    AliasCreateRequest("", "sys.service"),
                ).test()
                .assertNext {
                    it.status shouldBe DdlStatus.Status.CREATED
                }.verifyComplete()

            graph
                .queryScan(EntityName("sys", "service_alias"), EntityName.origin.phaseServiceName)
                .toRowFlux()
                .map {
                    it.getString("tgt")
                }.collectList()
                .test()
                .assertNext {
                    it shouldContainExactlyInAnyOrder listOf("sys", GraphFixtures.serviceName)
                }.verifyComplete()
        }

        "query on alias" {
            val src = GraphFixtures.sampleEdges.first().src
            val aliasName = EntityName(GraphFixtures.serviceName, "alias_${Random.nextInt().absoluteValue}")
            val targetLabelName = EntityName(GraphFixtures.serviceName, GraphFixtures.jdbcHash)
            graph.aliasDdl
                .create(
                    aliasName,
                    AliasCreateRequest("", targetLabelName.fullQualifiedName),
                ).test()
                .assertNext {
                    it.status shouldBe DdlStatus.Status.CREATED
                }.verifyComplete()

            graph
                .queryScan(aliasName, src)
                .test()
                .assertNext {
                    it.rows.size shouldBe GraphFixtures.sampleEdges.count { edge -> edge.src == src }
                }.verifyComplete()
        }

        "mutate on alias" {
            val aliasName = EntityName(GraphFixtures.serviceName, "alias_${Random.nextInt().absoluteValue}")
            val targetLabelName = EntityName(GraphFixtures.serviceName, GraphFixtures.jdbcHash)
            graph.aliasDdl
                .create(
                    aliasName,
                    AliasCreateRequest("", targetLabelName.fullQualifiedName),
                ).test()
                .assertNext {
                    it.status shouldBe DdlStatus.Status.CREATED
                }.verifyComplete()

            val src = 199
            val edges =
                listOf(
                    Edge(90, src, 1990, mapOf("permission" to "me", "createdAt" to 90)),
                    Edge(91, src, 1991, mapOf("permission" to "me", "createdAt" to 91)),
                )
            val label = graph.getLabel(aliasName)

            graph
                .mutate(label.name, label, edges.map { it.toTraceEdge() }, EdgeOperation.INSERT)
                .test()
                .assertNext {
                    it.result.count { result -> result.status == EdgeOperationStatus.CREATED } shouldBe edges.size
                }.verifyComplete()

            graph
                .queryScan(aliasName, src)
                .test()
                .assertNext {
                    it.rows.size shouldBe edges.count { edge -> edge.src == src }
                }.verifyComplete()

            graph
                .queryScan(targetLabelName, src)
                .test()
                .assertNext {
                    it.rows.size shouldBe edges.count { edge -> edge.src == src }
                }.verifyComplete()
        }

        "alias cannot be made if a label has a same name" {
            graph.aliasDdl
                .create(
                    EntityName(GraphFixtures.serviceName, GraphFixtures.jdbcHash),
                    AliasCreateRequest("", "test.like_hbase_hash"),
                ).test()
                .verifyError(IllegalArgumentException::class.java)
        }
    })
