package com.kakao.actionbase.v2.engine.dml

import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.metadata.MutationMode
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.GraphConfig
import com.kakao.actionbase.v2.engine.edge.MutationResult
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.label.EdgeOperationStatus
import com.kakao.actionbase.v2.engine.label.InsertEdgeRequest
import com.kakao.actionbase.v2.engine.label.Label
import com.kakao.actionbase.v2.engine.service.ddl.LabelCreateRequest
import com.kakao.actionbase.v2.engine.service.ddl.ServiceCreateRequest
import com.kakao.actionbase.v2.engine.test.GraphFixtures
import com.kakao.actionbase.v2.engine.test.cdc.InMemoryCdc
import com.kakao.actionbase.v2.engine.test.wal.InMemoryWal

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import reactor.kotlin.test.test

/**
 * Locks in v2-only behavior the v3 patch (#336) did not have to consider:
 * IGNORE is a v2 mode (not in v3 `EventType`), so the SYNC-shaped status mapping must NOT apply
 * when `system=IGNORE` or when the table's own mode is `IGNORE` — both must keep returning QUEUED.
 */
class GraphSystemAsyncIgnoreSpec :
    StringSpec({

        val database = GraphFixtures.serviceName
        val syncEdgeName = "sync_edge"
        val ignoreEdgeName = "ignore_edge"

        lateinit var graph: Graph
        lateinit var syncEdge: Label
        lateinit var ignoreEdge: Label

        fun syncEdgeRef() = EntityName(database, syncEdgeName)

        fun ignoreEdgeRef() = EntityName(database, ignoreEdgeName)

        fun verifyWal(table: Label, expectedSize: Int) {
            val actual = (graph.wal as InMemoryWal).readWal().filter { it.label == table.name }
            actual.size shouldBe expectedSize
            actual.all { it.mode.queue } shouldBe true
        }

        fun verifyCdc(table: Label) {
            (graph.cdc as InMemoryCdc).readCdc().filter { it.label == table.name }.shouldBeEmpty()
        }

        fun statuses(result: MutationResult) = result.result.map { it.status }

        fun setupGraph(systemMode: MutationMode?) {
            val builder =
                GraphConfig.Builder().let {
                    if (systemMode != null) it.withSystemMutationMode(systemMode) else it
                }
            graph = GraphFixtures.create(configBuilder = builder, withTestData = false)

            graph.serviceDdl
                .create(EntityName.fromOrigin(database), ServiceCreateRequest(desc = "test service"))
                .block()
            graph.labelDdl.create(syncEdgeRef(), mapper.readValue<LabelCreateRequest>(syncEdgeDescriptor)).block()
            graph.labelDdl.create(ignoreEdgeRef(), mapper.readValue<LabelCreateRequest>(ignoreEdgeDescriptor)).block()

            syncEdge = graph.getLabel(syncEdgeRef())
            ignoreEdge = graph.getLabel(ignoreEdgeRef())
        }

        afterTest {
            graph.close()
            (graph.wal as InMemoryWal).init()
            (graph.cdc as InMemoryCdc).init()
        }

        "system=IGNORE + SYNC EDGE table keeps QUEUED (no SYNC-shaped mapping)" {
            setupGraph(MutationMode.IGNORE)

            val request =
                InsertEdgeRequest(
                    label = "$database.$syncEdgeName",
                    edges = listOf(Edge(10L, 1000L, 9000L, mapOf("permission" to "na", "createdAt" to 10L))),
                )

            graph
                .upsert(request)
                .test()
                .assertNext { statuses(it) shouldBe listOf(EdgeOperationStatus.QUEUED) }
                .verifyComplete()

            verifyWal(syncEdge, 1)
            verifyCdc(syncEdge)
        }

        "table=IGNORE keeps QUEUED with no system override" {
            setupGraph(systemMode = null)

            val request =
                InsertEdgeRequest(
                    label = "$database.$ignoreEdgeName",
                    edges = listOf(Edge(10L, 1000L, 9000L, mapOf("paidAt" to 1L, "productId" to 200L))),
                )

            graph
                .upsert(request)
                .test()
                .assertNext { statuses(it) shouldBe listOf(EdgeOperationStatus.QUEUED) }
                .verifyComplete()

            verifyWal(ignoreEdge, 1)
            verifyCdc(ignoreEdge)
        }
    }) {
    companion object {
        private val mapper = jacksonObjectMapper()

        private val syncEdgeDescriptor =
            """
            {
              "desc": "sync edge for system-ignore test",
              "type": "INDEXED",
              "schema": {
                "src": {"type": "LONG", "desc": "sender"},
                "tgt": {"type": "LONG", "desc": "receiver"},
                "fields": [
                  {"name": "permission", "type": "STRING", "nullable": false, "desc": "permission"},
                  {"name": "createdAt", "type": "LONG", "nullable": false, "desc": "created at"}
                ]
              },
              "dirType": "BOTH",
              "storage": "${GraphFixtures.datastoreStorage}",
              "indices": [
                {"name": "created_at_desc", "fields": [{"name": "createdAt", "order": "DESC"}], "desc": "recently created first"}
              ],
              "event": false
            }
            """.trimIndent()

        private val ignoreEdgeDescriptor =
            """
            {
              "desc": "ignore edge for system-ignore test",
              "type": "INDEXED",
              "schema": {
                "src": {"type": "LONG", "desc": "sender"},
                "tgt": {"type": "LONG", "desc": "receiver"},
                "fields": [
                  {"name": "paidAt", "type": "LONG", "nullable": false, "desc": "payment time"},
                  {"name": "productId", "type": "LONG", "nullable": false, "desc": "product id"}
                ]
              },
              "dirType": "BOTH",
              "storage": "${GraphFixtures.datastoreStorage}",
              "indices": [
                {"name": "paid_at_desc", "fields": [{"name": "paidAt", "order": "DESC"}], "desc": "recently paid first"}
              ],
              "event": false,
              "mode": "IGNORE"
            }
            """.trimIndent()
    }
}
