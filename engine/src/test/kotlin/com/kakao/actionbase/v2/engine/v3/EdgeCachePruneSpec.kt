package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.core.metadata.common.Direction as CoreDirection

import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest
import com.kakao.actionbase.core.edge.payload.PruneStatus
import com.kakao.actionbase.core.edge.payload.PruneTarget
import com.kakao.actionbase.core.edge.payload.PruneType
import com.kakao.actionbase.core.java.codec.common.hbase.Order
import com.kakao.actionbase.core.metadata.common.Cache
import com.kakao.actionbase.core.metadata.common.CacheField
import com.kakao.actionbase.engine.service.MutationService
import com.kakao.actionbase.engine.service.QueryService
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.core.metadata.DirectionType
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.service.ddl.LabelCreateRequest
import com.kakao.actionbase.v2.engine.test.GraphFixtures

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import reactor.kotlin.test.test

class EdgeCachePruneSpec :
    StringSpec({

        lateinit var graph: Graph
        lateinit var mutationService: MutationService
        lateinit var queryService: QueryService

        beforeTest {
            graph = GraphFixtures.create()
            val engine = V2BackedEngine(graph)
            mutationService = MutationService(engine)
            queryService = QueryService(engine)
        }

        afterTest {
            graph.close()
        }

        fun createTable(
            database: String,
            table: String,
            caches: List<Cache>,
        ) {
            graph.labelDdl
                .create(
                    EntityName(database, table),
                    LabelCreateRequest(
                        desc = "cache prune test",
                        type = LabelType.INDEXED,
                        schema = GraphFixtures.sampleSchema,
                        dirType = DirectionType.BOTH,
                        storage = GraphFixtures.datastoreStorage,
                        indices = GraphFixtures.sampleIndices,
                        caches = caches,
                    ),
                ).test()
                .assertNext { it.status.name shouldBe "CREATED" }
                .verifyComplete()
        }

        fun insert(
            database: String,
            table: String,
            json: String,
        ) {
            mutationService
                .mutate(database, table, mapper.readValue<EdgeBulkMutationRequest>(json).mutations)
                .test()
                .assertNext { }
                .verifyComplete()
        }

        /**
         * Wiring only: the mocked [MutationService.prune] returns the empty contract shape.
         * Becomes a real no-cache assertion once the service is wired to the prune data plane.
         */
        "prune on a table without caches returns no results" {
            val database = GraphFixtures.serviceName
            val table = "prune_no_cache_test"
            createTable(database, table, caches = emptyList())

            mutationService
                .prune(database, table, PruneType.CACHE, listOf(PruneTarget("1000", CoreDirection.OUT)))
                .test()
                .assertNext { results -> results.size shouldBe 0 }
                .verifyComplete()
        }

        /**
         * EdgeCache (source=1000, direction=OUT, cache=created_at_desc)
         * `limit=2, tolerance=1`
         *
         * Before:
         * |       row key        | qualifier (DESC) |                  value                  |
         * |----------------------|------------------|-----------------------------------------|
         * | hash|1000|T|-6|OUT|C | ~500 | 2004      | version=1, permission=na, createdAt=500 |
         * |                      | ~400 | 2003      | version=1, permission=na, createdAt=400 | ← limit
         * |                      | ~300 | 2002      | version=1, permission=na, createdAt=300 | ← tolerance
         * |                      | ~200 | 2001      | version=1, permission=na, createdAt=200 | ← over-limit
         * |                      | ~100 | 2000      | version=1, permission=na, createdAt=100 | ← over-limit
         *
         * After:
         * |       row key        | qualifier (DESC) |                  value                  |
         * |----------------------|------------------|-----------------------------------------|
         * | hash|1000|T|-6|OUT|C | ~500 | 2004      | version=1, permission=na, createdAt=500 |
         * |                      | ~400 | 2003      | version=1, permission=na, createdAt=400 |
         * |                      | ~300 | 2002      | version=1, permission=na, createdAt=300 |
         *
         * Expected: status=PRUNED, seek OUT → [2004, 2003, 2002]
         */
        "prune evicts entries beyond limit + tolerance".config(enabled = false) {
            val database = GraphFixtures.serviceName
            val table = "prune_evict_test"
            val cacheName = "created_at_desc"
            createTable(
                database,
                table,
                caches = listOf(Cache(cacheName, listOf(CacheField("createdAt", Order.DESC)), limit = 2, tolerance = 1)),
            )

            insert(
                database,
                table,
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2000", "properties": {"permission": "na", "createdAt": 100}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2001", "properties": {"permission": "na", "createdAt": 200}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2002", "properties": {"permission": "na", "createdAt": 300}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2003", "properties": {"permission": "na", "createdAt": 400}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2004", "properties": {"permission": "na", "createdAt": 500}}}
                  ]
                }
                """.trimIndent(),
            )

            mutationService
                .prune(database, table, PruneType.CACHE, listOf(PruneTarget("1000", CoreDirection.OUT)))
                .test()
                .assertNext { results ->
                    results.size shouldBe 1
                    results.first().type shouldBe PruneType.CACHE
                    results.first().name shouldBe cacheName
                    results.first().status shouldBe PruneStatus.PRUNED
                }.verifyComplete()

            // Retained top-3 by createdAt DESC: 500, 400, 300
            queryService
                .seek(database, table, cacheName, listOf("1000"), Direction.OUT, 10)
                .test()
                .assertNext { payload ->
                    payload.count shouldBe 3
                    payload.edges.map { it.target } shouldBe listOf(2004L, 2003L, 2002L)
                }.verifyComplete()
        }

        /**
         * EdgeCache (source=1000, direction=OUT, cache=created_at_desc) — limit=2, tolerance=1
         * Exactly `limit + tolerance` entries (3) → nothing beyond the bound → SKIPPED, untouched.
         *
         * Before & after (identical — nothing exceeds):
         * |       row key        | qualifier (DESC) |                  value                  |
         * |----------------------|------------------|-----------------------------------------|
         * | hash|1000|T|-6|OUT|C | ~300 | 2002      | version=1, permission=na, createdAt=300 |
         * |                      | ~200 | 2001      | version=1, permission=na, createdAt=200 |
         * |                      | ~100 | 2000      | version=1, permission=na, createdAt=100 |
         *
         * Expected: status=SKIPPED, seek OUT → 3 edges
         */
        "prune is SKIPPED when within limit + tolerance".config(enabled = false) {
            val database = GraphFixtures.serviceName
            val table = "prune_skip_test"
            val cacheName = "created_at_desc"
            createTable(
                database,
                table,
                caches = listOf(Cache(cacheName, listOf(CacheField("createdAt", Order.DESC)), limit = 2, tolerance = 1)),
            )

            insert(
                database,
                table,
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2000", "properties": {"permission": "na", "createdAt": 100}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2001", "properties": {"permission": "na", "createdAt": 200}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2002", "properties": {"permission": "na", "createdAt": 300}}}
                  ]
                }
                """.trimIndent(),
            )

            mutationService
                .prune(database, table, PruneType.CACHE, listOf(PruneTarget("1000", CoreDirection.OUT)))
                .test()
                .assertNext { results ->
                    results.size shouldBe 1
                    results.first().status shouldBe PruneStatus.SKIPPED
                }.verifyComplete()

            queryService
                .seek(database, table, cacheName, listOf("1000"), Direction.OUT, 10)
                .test()
                .assertNext { payload -> payload.count shouldBe 3 }
                .verifyComplete()
        }

        /**
         * Dimension fields define independent top-N buckets, so `limit` applies per bucket.
         * (untouched). Status is PRUNED because at least one bucket was trimmed.
         *
         * EdgeCache (source=1000, direction=OUT, cache=permission_created_at_desc)
         * `limit=1, tolerance=1`
         *
         * Before:
         * |       row key        | qualifier (perm ASC, createdAt DESC) |     value     |
         * |----------------------|--------------------------------------|---------------|
         * | hash|1000|T|-6|OUT|C | me     | ~300 | 2002                  | createdAt=300 |
         * |                      | me     | ~200 | 2001                  | createdAt=200 |
         * |                      | me     | ~100 | 2000                  | createdAt=100 | ← over-limit in "me", pruned
         * |                      | others | ~200 | 2004                  | createdAt=200 |
         * |                      | others | ~100 | 2003                  | createdAt=100 |
         *
         * After ("me" trimmed to top 2; "others" within bound, untouched):
         * |       row key        | qualifier (perm ASC, createdAt DESC) |     value     |
         * |----------------------|--------------------------------------|---------------|
         * | hash|1000|T|-6|OUT|C | me     | ~300 | 2002                  | createdAt=300 |
         * |                      | me     | ~200 | 2001                  | createdAt=200 |
         * |                      | others | ~200 | 2004                  | createdAt=200 |
         * |                      | others | ~100 | 2003                  | createdAt=100 |
         *
         * Expected: status=PRUNED, seek (permission=me) → [2002, 2001]; seek (permission=others) → 2 edges
         */
        "prune enforces limit per dimension bucket independently".config(enabled = false) {
            val database = GraphFixtures.serviceName
            val table = "prune_dimension_test"
            val cacheName = "permission_created_at_desc"
            createTable(
                database,
                table,
                caches =
                    listOf(
                        Cache(
                            cacheName,
                            listOf(
                                CacheField("permission", Order.ASC, dimension = setOf("me", "others")),
                                CacheField("createdAt", Order.DESC),
                            ),
                            limit = 1,
                            tolerance = 1,
                        ),
                    ),
            )

            insert(
                database,
                table,
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2000", "properties": {"permission": "me",     "createdAt": 100}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2001", "properties": {"permission": "me",     "createdAt": 200}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2002", "properties": {"permission": "me",     "createdAt": 300}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2003", "properties": {"permission": "others", "createdAt": 100}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2004", "properties": {"permission": "others", "createdAt": 200}}}
                  ]
                }
                """.trimIndent(),
            )

            mutationService
                .prune(database, table, PruneType.CACHE, listOf(PruneTarget("1000", CoreDirection.OUT)))
                .test()
                .assertNext { results -> results.first().status shouldBe PruneStatus.PRUNED }
                .verifyComplete()

            // "me" bucket trimmed to top-2 (300, 200); the lowest (100) evicted.
            queryService
                .seek(database, table, cacheName, listOf("1000"), Direction.OUT, 10, ranges = "permission=me")
                .test()
                .assertNext { payload ->
                    payload.count shouldBe 2
                    payload.edges.map { it.target } shouldBe listOf(2002L, 2001L)
                }.verifyComplete()

            // "others" bucket within bound — untouched.
            queryService
                .seek(database, table, cacheName, listOf("1000"), Direction.OUT, 10, ranges = "permission=others")
                .test()
                .assertNext { payload -> payload.count shouldBe 2 }
                .verifyComplete()
        }
    }) {
    companion object {
        val mapper = jacksonObjectMapper()
    }
}
