package com.kakao.actionbase.v2.engine.v3.edge

import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest
import com.kakao.actionbase.v2.core.metadata.MutationMode
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.GraphConfig
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.metadata.MutationModeContext
import com.kakao.actionbase.v2.engine.service.ddl.LabelCreateRequest
import com.kakao.actionbase.v2.engine.service.ddl.ServiceCreateRequest
import com.kakao.actionbase.v2.engine.test.GraphFixtures
import com.kakao.actionbase.v2.engine.test.cdc.InMemoryCdc
import com.kakao.actionbase.v2.engine.test.wal.InMemoryWal
import com.kakao.actionbase.v2.engine.v3.V3MutationService
import com.kakao.actionbase.v2.engine.v3.V3QueryService

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import reactor.kotlin.test.test

class V3MutationServiceGlobalAsyncSpec :
    StringSpec({

        val database = GraphFixtures.serviceName
        val table = "sync_edge_for_global_test"
        val syncTableName = EntityName(database, table)

        lateinit var graph: Graph
        lateinit var wal: InMemoryWal
        lateinit var cdc: InMemoryCdc
        lateinit var v3MutationService: V3MutationService
        lateinit var v3QueryService: V3QueryService

        beforeTest {
            graph =
                GraphFixtures.create(
                    configBuilder = GraphConfig.Builder().withGlobalMutationMode(MutationMode.ASYNC),
                    withTestData = false,
                )
            wal = graph.wal as InMemoryWal
            cdc = graph.cdc as InMemoryCdc

            graph.serviceDdl
                .create(EntityName.fromOrigin(database), ServiceCreateRequest(desc = "test service"))
                .block()
            graph.labelDdl
                .create(
                    syncTableName,
                    mapper.readValue<LabelCreateRequest>(syncEdgeDescriptor),
                ).block()

            v3MutationService = V3MutationService(graph)
            v3QueryService = V3QueryService(graph)
        }

        afterTest {
            graph.close()
            wal.init()
            cdc.init()
        }

        fun verifyWal(
            tableName: EntityName,
            expectedSize: Int,
            expectedMode: MutationModeContext,
        ) {
            val walActual = wal.readWal().filter { it.label == tableName }
            walActual.size shouldBe expectedSize
            walActual.all { it.mode == expectedMode } shouldBe true
        }

        fun verifyCdc(
            tableName: EntityName,
            expectedSize: Int = 0,
        ) {
            val cdcActual = cdc.readCdc().filter { it.label == tableName }
            if (expectedSize == 0) {
                cdcActual.shouldBeEmpty()
            } else {
                cdcActual.size shouldBe expectedSize
            }
        }

        fun verifyEmptyQuery(
            tableName: EntityName,
            sources: List<Long>,
            targets: List<Long>,
        ) {
            v3QueryService
                .gets(tableName.service, tableName.nameNotNull, sources, targets)
                .test()
                .assertNext { it.edges.size shouldBe 0 }
                .verifyComplete()
        }

        "global=ASYNC makes SYNC table queue mutations" {
            val request =
                mapper.readValue<EdgeBulkMutationRequest>(
                    """
                    {
                      "mutations": [
                        {"type": "INSERT", "edge": {"version": 10, "source": "1000", "target": "9000", "properties": {"permission": "na", "createdAt": 10}}}
                      ]
                    }
                    """.trimIndent(),
                )

            v3MutationService
                .mutateEdge(database, table, request)
                .test()
                .assertNext {
                    mapper.writeValueAsString(it) shouldBe """{"results":[{"source":1000,"target":9000,"status":"QUEUED","count":1}]}"""
                }.verifyComplete()

            verifyWal(syncTableName, 1, MutationModeContext.of(table = MutationMode.SYNC, request = null, global = MutationMode.ASYNC, internal = null))
            verifyCdc(syncTableName)
            verifyEmptyQuery(syncTableName, listOf(1000L), listOf(9000L))
        }

        "internal=SYNC overrides global=ASYNC - mutations are written synchronously" {
            val request =
                mapper.readValue<EdgeBulkMutationRequest>(
                    """
                    {
                      "mutations": [
                        {"type": "INSERT", "edge": {"version": 10, "source": "1000", "target": "9000", "properties": {"permission": "na", "createdAt": 10}}}
                      ]
                    }
                    """.trimIndent(),
                )

            v3MutationService
                .internalMutateEdge(database, table, request, internal = MutationMode.SYNC)
                .test()
                .assertNext {
                    mapper.writeValueAsString(it) shouldBe """{"results":[{"source":1000,"target":9000,"status":"CREATED","count":1}]}"""
                }.verifyComplete()

            verifyWal(syncTableName, 1, MutationModeContext.of(table = MutationMode.SYNC, request = null, global = MutationMode.ASYNC, internal = MutationMode.SYNC))
            verifyCdc(syncTableName, 1)

            v3QueryService
                .gets(database, table, listOf(1000L), listOf(9000L))
                .test()
                .assertNext { it.edges.size shouldBe 1 }
                .verifyComplete()
        }
    }) {
    companion object {
        private val mapper = jacksonObjectMapper()

        private val syncEdgeDescriptor =
            """
            {
              "desc": "sync edge for global mode test",
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
              "event": false,
              "readOnly": true
            }
            """.trimIndent()
    }
}
