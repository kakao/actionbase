package com.kakao.actionbase.v2.engine.dml

import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.core.metadata.MutationMode
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.GraphConfig
import com.kakao.actionbase.v2.engine.edge.MutationResult
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.label.DeleteEdgeRequest
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
 * v2 counterpart of the v3 `MutationServiceSystemAsyncSpec` (#336). Locks in the SYNC response
 * contract when `systemMutationMode=ASYNC` overrides a SYNC table.
 *
 * Covered:
 * - SYNC EDGE table × INSERT / UPDATE / DELETE — operation-based status
 * - `system=ASYNC` overrides `request=SYNC` on ASYNC table → QUEUED
 * - `force=true + request=SYNC` overrides `system=ASYNC` → actual sync result
 * - `force=true + request=ASYNC` on SYNC table → QUEUED (forced ASYNC, no contract to preserve)
 *
 * Not covered (intentional):
 * - MULTI_EDGE: officially v3-only; v2 keeps compatibility but has no production integrations.
 * - PURGE: not exercised in production. The `PURGE -> PURGED` mapping in `Graph.kt` is defensive.
 * - Multi-event tie-break (same-version / highest-version wins): v2 `mutate()` takes a single
 *   `EdgeOperation` per call, so the case cannot arise.
 */
class GraphSystemAsyncSpec :
    StringSpec({

        val database = GraphFixtures.serviceName
        val syncEdgeName = "sync_edge"
        val asyncEdgeName = "async_edge"

        lateinit var graph: Graph
        lateinit var syncEdge: Label
        lateinit var asyncEdge: Label

        fun syncEdgeRef() = EntityName(database, syncEdgeName)

        fun asyncEdgeRef() = EntityName(database, asyncEdgeName)

        beforeTest {
            graph =
                GraphFixtures.create(
                    configBuilder = GraphConfig.Builder().withSystemMutationMode(MutationMode.ASYNC),
                    withTestData = false,
                )

            graph.serviceDdl
                .create(EntityName.fromOrigin(database), ServiceCreateRequest(desc = "test service"))
                .block()
            graph.labelDdl.create(syncEdgeRef(), mapper.readValue<LabelCreateRequest>(syncEdgeDescriptor)).block()
            graph.labelDdl.create(asyncEdgeRef(), mapper.readValue<LabelCreateRequest>(asyncEdgeDescriptor)).block()

            syncEdge = graph.getLabel(syncEdgeRef())
            asyncEdge = graph.getLabel(asyncEdgeRef())
        }

        afterTest {
            graph.close()
            (graph.wal as InMemoryWal).init()
            (graph.cdc as InMemoryCdc).init()
        }

        fun verifyWal(
            table: Label,
            expectedSize: Int,
            queue: Boolean,
        ) {
            val actual = (graph.wal as InMemoryWal).readWal().filter { it.label == table.name }
            actual.size shouldBe expectedSize
            actual.all { it.mode.queue == queue } shouldBe true
        }

        fun verifyCdc(
            table: Label,
            expectedSize: Int = 0,
        ) {
            val actual = (graph.cdc as InMemoryCdc).readCdc().filter { it.label == table.name }
            if (expectedSize == 0) actual.shouldBeEmpty() else actual.size shouldBe expectedSize
        }

        fun statuses(result: MutationResult) = result.result.map { it.status }

        // ---- scenario 1: system=ASYNC overrides SYNC table — preserve SYNC response contract ----

        "system=ASYNC + SYNC EDGE table maps INSERT to CREATED" {
            val request =
                InsertEdgeRequest(
                    label = "$database.$syncEdgeName",
                    edges = listOf(Edge(10L, 1000L, 9000L, mapOf("permission" to "na", "createdAt" to 10L))),
                )

            graph
                .upsert(request)
                .test()
                .assertNext { statuses(it) shouldBe listOf(EdgeOperationStatus.CREATED) }
                .verifyComplete()

            verifyWal(syncEdge, 1, queue = true)
            verifyCdc(syncEdge)
        }

        "system=ASYNC + SYNC EDGE table maps UPDATE to UPDATED" {
            val request =
                InsertEdgeRequest(
                    label = "$database.$syncEdgeName",
                    edges = listOf(Edge(10L, 1000L, 9000L, mapOf("permission" to "rw"))),
                )

            graph
                .update(request)
                .test()
                .assertNext { statuses(it) shouldBe listOf(EdgeOperationStatus.UPDATED) }
                .verifyComplete()

            verifyWal(syncEdge, 1, queue = true)
            verifyCdc(syncEdge)
        }

        "system=ASYNC + SYNC EDGE table maps DELETE to DELETED" {
            val request =
                DeleteEdgeRequest(
                    label = "$database.$syncEdgeName",
                    edges = listOf(Edge(10L, 1000L, 9000L)),
                )

            graph
                .delete(request)
                .test()
                .assertNext { statuses(it) shouldBe listOf(EdgeOperationStatus.DELETED) }
                .verifyComplete()

            verifyWal(syncEdge, 1, queue = true)
            verifyCdc(syncEdge)
        }

        // ---- scenario 2: system=ASYNC overrides request=SYNC on ASYNC table ----

        "system=ASYNC overrides request=SYNC on ASYNC EDGE table" {
            graph
                .mutate(
                    asyncEdgeRef(),
                    asyncEdge,
                    listOf(Edge(10L, 1000L, 9000L, mapOf("paidAt" to 1L, "productId" to 200L)).toTraceEdge()),
                    EdgeOperation.INSERT,
                    mode = MutationMode.SYNC,
                ).test()
                .assertNext { statuses(it) shouldBe listOf(EdgeOperationStatus.QUEUED) }
                .verifyComplete()

            verifyWal(asyncEdge, 1, queue = true)
            verifyCdc(asyncEdge)
        }

        // ---- scenario 3: force=true + request=SYNC overrides system=ASYNC ----

        "force=true request=SYNC overrides system=ASYNC on ASYNC EDGE table" {
            graph
                .mutate(
                    asyncEdgeRef(),
                    asyncEdge,
                    listOf(Edge(10L, 1000L, 9000L, mapOf("paidAt" to 1L, "productId" to 200L)).toTraceEdge()),
                    EdgeOperation.INSERT,
                    mode = MutationMode.SYNC,
                    force = true,
                ).test()
                .assertNext { statuses(it) shouldBe listOf(EdgeOperationStatus.CREATED) }
                .verifyComplete()

            verifyWal(asyncEdge, 1, queue = false)
            verifyCdc(asyncEdge, 1)
        }

        // ---- scenario 4: force=true + request=ASYNC keeps QUEUED on SYNC table ----
        // Client explicitly forced ASYNC; the SYNC contract preservation must not apply.

        "force=true request=ASYNC on SYNC EDGE table returns QUEUED" {
            graph
                .mutate(
                    syncEdgeRef(),
                    syncEdge,
                    listOf(Edge(10L, 1000L, 9000L, mapOf("permission" to "na", "createdAt" to 10L)).toTraceEdge()),
                    EdgeOperation.INSERT,
                    mode = MutationMode.ASYNC,
                    force = true,
                ).test()
                .assertNext { statuses(it) shouldBe listOf(EdgeOperationStatus.QUEUED) }
                .verifyComplete()

            verifyWal(syncEdge, 1, queue = true)
            verifyCdc(syncEdge)
        }
    }) {
    companion object {
        private val mapper = jacksonObjectMapper()

        private val syncEdgeDescriptor =
            """
            {
              "desc": "sync edge for system-async test",
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

        private val asyncEdgeDescriptor =
            """
            {
              "desc": "async edge for system-async test",
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
              "mode": "ASYNC"
            }
            """.trimIndent()
    }
}
