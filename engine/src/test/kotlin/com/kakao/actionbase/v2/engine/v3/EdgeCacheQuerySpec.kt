package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest
import com.kakao.actionbase.core.edge.payload.EdgeMutationResponse
import com.kakao.actionbase.core.edge.payload.MultiEdgeBulkMutationRequest
import com.kakao.actionbase.core.java.codec.common.hbase.Order
import com.kakao.actionbase.core.metadata.common.Cache
import com.kakao.actionbase.core.metadata.common.IndexField
import com.kakao.actionbase.engine.service.MutationService
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.core.metadata.DirectionType
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.core.types.DataType
import com.kakao.actionbase.v2.core.types.EdgeSchema
import com.kakao.actionbase.v2.core.types.Field
import com.kakao.actionbase.v2.core.types.VertexField
import com.kakao.actionbase.v2.core.types.VertexType
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.service.ddl.LabelCreateRequest
import com.kakao.actionbase.v2.engine.test.GraphFixtures

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import reactor.kotlin.test.test

class EdgeCacheQuerySpec :
    StringSpec({

        lateinit var graph: Graph
        lateinit var mutationService: MutationService
        lateinit var queryService: V3QueryService

        beforeTest {
            graph = GraphFixtures.create()
            mutationService = MutationService(V2BackedEngine(graph))
            queryService = V3QueryService(graph)
        }

        afterTest {
            graph.close()
        }

        /**
         *
         * Mutation:
         * | source | target | createdAt |
         * |--------|--------|-----------|
         * | 1000   | 2000   | 100       |
         * | 1000   | 2001   | 200       |
         * | 1000   | 2002   | 300       |
         *
         * EdgeCache (source=1000, direction=OUT)
         * |       row key        |  qualifier (DESC)  |                   value                  |
         * |----------------------|--------------------|------------------------------------------|
         * | hash|1000|T|-6|OUT|C | ~300 | 2002        | version=1, permission=na, createdAt=300  |
         * |                      | ~200 | 2001        | version=1, permission=na, createdAt=200  |
         * |                      | ~100 | 2000        | version=1, permission=na, createdAt=100  |
         *
         * Query OUT (start=1000, limit=10, DESC)
         *
         * Expected: [2002, 2001, 2000]
         */
        "INSERT → seek OUT returns all edges in DESC order" {
            val database = GraphFixtures.serviceName
            val table = "cache_test"
            val cacheName = "created_at_desc"

            val createRequest =
                LabelCreateRequest(
                    desc = "cache round-trip test",
                    type = LabelType.INDEXED,
                    schema = GraphFixtures.sampleSchema,
                    dirType = DirectionType.BOTH,
                    storage = GraphFixtures.datastoreStorage,
                    indices = GraphFixtures.sampleIndices,
                    caches =
                        listOf(
                            Cache(
                                cache = cacheName,
                                fields = listOf(IndexField("createdAt", Order.DESC)),
                                limit = 100,
                            ),
                        ),
                )

            graph.labelDdl
                .create(EntityName(database, table), createRequest)
                .test()
                .assertNext { it.status.name shouldBe "CREATED" }
                .verifyComplete()

            val insertRequest =
                mapper.readValue<EdgeBulkMutationRequest>(
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
                .mutate(database, table, insertRequest.mutations)
                .map { EdgeMutationResponse.from(it) }
                .test()
                .assertNext { response ->
                    response.results.size shouldBe 3
                    response.results.all { it.status == "CREATED" } shouldBe true
                }.verifyComplete()

            queryService
                .seek(database, table, cacheName, "1000", Direction.OUT, 10)
                .test()
                .assertNext { payload ->
                    payload.count shouldBe 3
                    // DESC order: createdAt 300, 200, 100
                    payload.edges.map { it.target } shouldBe listOf(2002L, 2001L, 2000L)
                }.verifyComplete()
        }

        /**
         * Same data as above, but query with limit=2 + cursor pagination.
         *
         * EdgeCache (source=1000, direction=OUT)
         * |       row key        |  qualifier (DESC) |                  value                  |
         * |----------------------|-------------------|-----------------------------------------|
         * | hash|1000|T|-6|OUT|C | ~300 | 2002       | version=1, permission=na, createdAt=300 |
         * |                      | ~200 | 2001       | version=1, permission=na, createdAt=200 |
         * |                      | ~100 | 2000       | version=1, permission=na, createdAt=100 |
         *
         * Expected:
         *   - Page 1 (limit=2): [2002, 2001] — hasNext=true, offset=cursor
         *   - Page 2 (limit=2, offset=cursor): [2000] — hasNext=false
         */
        "INSERT → seek OUT with offset paginates results" {
            val database = GraphFixtures.serviceName
            val table = "cache_offset_test"
            val cacheName = "created_at_desc"

            val createRequest =
                LabelCreateRequest(
                    desc = "cache pagination test",
                    type = LabelType.INDEXED,
                    schema = GraphFixtures.sampleSchema,
                    dirType = DirectionType.BOTH,
                    storage = GraphFixtures.datastoreStorage,
                    indices = GraphFixtures.sampleIndices,
                    caches =
                        listOf(
                            Cache(
                                cache = cacheName,
                                fields = listOf(IndexField("createdAt", Order.DESC)),
                                limit = 100,
                            ),
                        ),
                )

            graph.labelDdl
                .create(EntityName(database, table), createRequest)
                .test()
                .assertNext { it.status.name shouldBe "CREATED" }
                .verifyComplete()

            val insertRequest =
                mapper.readValue<EdgeBulkMutationRequest>(
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
                .mutate(database, table, insertRequest.mutations)
                .test()
                .assertNext { }
                .verifyComplete()

            // Page 1: limit=2 → [2002, 2001], hasNext=true
            var nextOffset: String? = null
            queryService
                .seek(database, table, cacheName, "1000", Direction.OUT, 2)
                .test()
                .assertNext { payload ->
                    payload.count shouldBe 2
                    payload.edges.map { it.target } shouldBe listOf(2002L, 2001L)
                    payload.hasNext shouldBe true
                    payload.offset shouldBe payload.offset // not null
                    nextOffset = payload.offset
                }.verifyComplete()

            // Page 2: offset=cursor → [2000], hasNext=false
            queryService
                .seek(database, table, cacheName, "1000", Direction.OUT, 2, nextOffset)
                .test()
                .assertNext { payload ->
                    payload.count shouldBe 1
                    payload.edges.map { it.target } shouldBe listOf(2000L)
                    payload.hasNext shouldBe false
                    payload.offset shouldBe null
                }.verifyComplete()
        }

        /**
         * Same mutation as above (source=1000, targets=2000,2001,2002), but query IN direction.
         *
         * EdgeCache (source=2000, direction=IN)
         * |       row key       | qualifier (DESC) |                   value                 |
         * |---------------------|------------------|-----------------------------------------|
         * | hash|2000|T|-6|IN|C | ~100 | 1000      | version=1, permission=na, createdAt=100 |
         *
         * Query IN (start=2000, limit=10, DESC)
         *
         * Expected: [1000] — edge where target=2000, source=1000
         */
        "INSERT → seek IN returns edges for target vertex" {
            val database = GraphFixtures.serviceName
            val table = "cache_in_test"
            val cacheName = "created_at_desc"

            val createRequest =
                LabelCreateRequest(
                    desc = "cache IN direction test",
                    type = LabelType.INDEXED,
                    schema = GraphFixtures.sampleSchema,
                    dirType = DirectionType.BOTH,
                    storage = GraphFixtures.datastoreStorage,
                    indices = GraphFixtures.sampleIndices,
                    caches =
                        listOf(
                            Cache(
                                cache = cacheName,
                                fields = listOf(IndexField("createdAt", Order.DESC)),
                                limit = 100,
                            ),
                        ),
                )

            graph.labelDdl
                .create(EntityName(database, table), createRequest)
                .test()
                .assertNext { it.status.name shouldBe "CREATED" }
                .verifyComplete()

            val insertRequest =
                mapper.readValue<EdgeBulkMutationRequest>(
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
                .mutate(database, table, insertRequest.mutations)
                .test()
                .assertNext { }
                .verifyComplete()

            // Query IN with start=2000 — should find 1 edge (source=1000 → target=2000)
            queryService
                .seek(database, table, cacheName, "2000", Direction.IN, 10)
                .test()
                .assertNext { payload ->
                    payload.count shouldBe 1
                    payload.edges.first().source shouldBe 1000L
                    payload.edges.first().target shouldBe 2000L
                }.verifyComplete()

            // Query IN with start=1000 — no edges (1000 is source, not target)
            queryService
                .seek(database, table, cacheName, "1000", Direction.IN, 10)
                .test()
                .assertNext { payload ->
                    payload.count shouldBe 0
                }.verifyComplete()
        }

        /**
         * INSERT 2 edges, then DELETE target=2000.
         *
         * After INSERT:
         * |       row key        | qualifier (DESC) |                   value                 |
         * |----------------------|------------------|-----------------------------------------|
         * | hash|1000|T|-6|OUT|C | ~200 | 2001      | version=1, permission=na, createdAt=200 |
         * |                      | ~100 | 2000      | version=1, permission=na, createdAt=100 |
         *
         * After DELETE (target=2000):
         * |       row key        | qualifier (DESC) |                  value                  |
         * |----------------------|------------------|-----------------------------------------|
         * | hash|1000|T|-6|OUT|C | ~200 | 2001      | version=1, permission=na, createdAt=200 |
         *
         * Query OUT (start=1000, limit=10, DESC)
         *
         * Expected: [2001]
         */

        "DELETE → seek OUT excludes deleted edge" {
            val database = GraphFixtures.serviceName
            val table = "cache_delete_test"
            val cacheName = "created_at_desc"

            val createRequest =
                LabelCreateRequest(
                    desc = "cache delete test",
                    type = LabelType.INDEXED,
                    schema = GraphFixtures.sampleSchema,
                    dirType = DirectionType.BOTH,
                    storage = GraphFixtures.datastoreStorage,
                    indices = GraphFixtures.sampleIndices,
                    caches =
                        listOf(
                            Cache(
                                cache = cacheName,
                                fields = listOf(IndexField("createdAt", Order.DESC)),
                                limit = 100,
                            ),
                        ),
                )

            graph.labelDdl
                .create(EntityName(database, table), createRequest)
                .test()
                .assertNext { it.status.name shouldBe "CREATED" }
                .verifyComplete()

            val insertRequest =
                mapper.readValue<EdgeBulkMutationRequest>(
                    """
                    {
                      "mutations": [
                        {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2000", "properties": {"permission": "na", "createdAt": 100}}},
                        {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2001", "properties": {"permission": "na", "createdAt": 200}}}
                      ]
                    }
                    """.trimIndent(),
                )

            mutationService
                .mutate(database, table, insertRequest.mutations)
                .test()
                .assertNext { }
                .verifyComplete()

            // Delete one edge
            val deleteRequest =
                mapper.readValue<EdgeBulkMutationRequest>(
                    """
                    {
                      "mutations": [
                        {"type": "DELETE", "edge": {"version": 2, "source": "1000", "target": "2000"}}
                      ]
                    }
                    """.trimIndent(),
                )

            mutationService
                .mutate(database, table, deleteRequest.mutations)
                .test()
                .assertNext { response ->
                    EdgeMutationResponse
                        .from(response)
                        .results
                        .first()
                        .status shouldBe "DELETED"
                }.verifyComplete()

            // Query cache — should return only 1 edge
            queryService
                .seek(database, table, cacheName, "1000", Direction.OUT, 10)
                .test()
                .assertNext { payload ->
                    payload.count shouldBe 1
                    payload.edges.first().target shouldBe 2001L
                }.verifyComplete()
        }
        /**
         * MultiEdge cache test — directedTarget is always id.
         *
         * Mutation:
         * | id  | source | target | paidAt |
         * |-----|--------|--------|--------|
         * | 100 | 1000   | 2000   | 300    |
         * | 101 | 1000   | 2000   | 200    |
         * | 102 | 1000   | 2001   | 100    |
         *
         * EdgeCache (source=1000, direction=OUT)
         * |       row key        | qualifier (DESC) |               value                |
         * |----------------------|------------------|------------------------------------|
         * | hash|1000|T|-6|OUT|C | ~300 | 100       | version=1, paidAt=300, productId=1 |
         * |                      | ~200 | 101       | version=1, paidAt=200, productId=2 |
         * |                      | ~100 | 102       | version=1, paidAt=100, productId=3 |
         *
         * Query OUT (start=1000, limit=10, DESC)
         *
         * Expected: targets=[100, 101, 102] — id is directedTarget
         */
        "INSERT MultiEdge → seek OUT returns edges with id as target" {
            val database = GraphFixtures.serviceName
            val table = "cache_multi_edge_test"
            val cacheName = "paid_at_desc"

            val multiEdgeSchema =
                EdgeSchema(
                    VertexField(VertexType.LONG),
                    VertexField(VertexType.LONG),
                    listOf(
                        Field("_id", DataType.LONG, false),
                        Field("paidAt", DataType.LONG, false),
                        Field("productId", DataType.LONG, false),
                    ),
                )

            val createRequest =
                LabelCreateRequest(
                    desc = "multi edge cache test",
                    type = LabelType.MULTI_EDGE,
                    schema = multiEdgeSchema,
                    dirType = DirectionType.BOTH,
                    storage = GraphFixtures.datastoreStorage,
                    indices = emptyList(),
                    readOnly = true,
                    caches =
                        listOf(
                            Cache(
                                cache = cacheName,
                                fields = listOf(IndexField("paidAt", Order.DESC)),
                                limit = 100,
                            ),
                        ),
                )

            graph.labelDdl
                .create(EntityName(database, table), createRequest)
                .test()
                .assertNext { it.status.name shouldBe "CREATED" }
                .verifyComplete()

            val insertRequest =
                mapper.readValue<MultiEdgeBulkMutationRequest>(
                    """
                    {
                      "mutations": [
                        {"type": "INSERT", "edge": {"version": 1, "id": 100, "source": 1000, "target": 2000, "properties": {"paidAt": 300, "productId": 1}}},
                        {"type": "INSERT", "edge": {"version": 1, "id": 101, "source": 1000, "target": 2000, "properties": {"paidAt": 200, "productId": 2}}},
                        {"type": "INSERT", "edge": {"version": 1, "id": 102, "source": 1000, "target": 2001, "properties": {"paidAt": 100, "productId": 3}}}
                      ]
                    }
                    """.trimIndent(),
                )

            mutationService
                .mutate(database, table, insertRequest.mutations)
                .test()
                .assertNext { }
                .verifyComplete()

            // Query OUT — directedTarget is id, not target
            queryService
                .seek(database, table, cacheName, "1000", Direction.OUT, 10)
                .test()
                .assertNext { payload ->
                    payload.count shouldBe 3
                    // DESC paidAt order: 300, 200, 100
                    payload.edges.map { it.target } shouldBe listOf(100L, 101L, 102L)
                }.verifyComplete()
        }
    }) {
    companion object {
        val mapper = jacksonObjectMapper()
    }
}
