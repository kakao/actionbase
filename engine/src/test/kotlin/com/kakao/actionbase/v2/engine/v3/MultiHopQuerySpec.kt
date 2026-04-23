package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest
import com.kakao.actionbase.core.java.codec.common.hbase.Order
import com.kakao.actionbase.core.metadata.common.Cache
import com.kakao.actionbase.core.metadata.common.IndexField
import com.kakao.actionbase.engine.query.ActionbaseQuery
import com.kakao.actionbase.engine.query.ActionbaseQueryExecutor
import com.kakao.actionbase.engine.service.MutationService
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

class MultiHopQuerySpec :
    StringSpec({

        lateinit var graph: Graph
        lateinit var queryExecutor: ActionbaseQueryExecutor
        lateinit var mutationService: MutationService

        beforeTest {
            graph = GraphFixtures.create()
            val engine = V2BackedEngine(graph)
            queryExecutor = ActionbaseQueryExecutor(engine)
            mutationService = MutationService(engine)
        }

        afterTest {
            graph.close()
        }

        /**
         * 2-hop: user1's friends' wishlists
         *
         * hop1: SCAN follows (index: created_at_desc)
         * |             row key             | qualifier |           value          |
         * |---------------------------------|-----------|--------------------------|
         * | hash|1000|T|IDX|OUT|I|~200|2001 | default   | version=1, createdAt=200 |
         * | hash|1000|T|IDX|OUT|I|~100|2000 | default   | version=1, createdAt=100 |
         *
         * hop2: SEEK wishlist (cache: created_at_desc)
         * |          row key     | qualifier (DESC) |           value          |
         * |----------------------|------------------|--------------------------|
         * | hash|2000|T|-6|OUT|C | ~300 | 5000      | version=1, createdAt=300 |
         * | hash|2001|T|-6|OUT|C | ~500 | 5002      | version=1, createdAt=500 |
         * |                      | ~400 | 5001      | version=1, createdAt=400 |
         *
         * Expected: [5000,5001,5002]
         */
        "2-hop: scan → cache returns hop2 results" {
            val database = GraphFixtures.serviceName
            val followsTable = "follows"
            val wishlistTable = "wishlist"
            val cacheName = "created_at_desc"

            // Create follows table (hop1: scan by index)
            graph.labelDdl
                .create(
                    EntityName(database, followsTable),
                    LabelCreateRequest(
                        desc = "follows",
                        type = LabelType.INDEXED,
                        schema = GraphFixtures.sampleSchema,
                        dirType = DirectionType.BOTH,
                        storage = GraphFixtures.datastoreStorage,
                        indices = GraphFixtures.sampleIndices,
                    ),
                ).test()
                .assertNext { it.status.name shouldBe "CREATED" }
                .verifyComplete()

            // Create wishlist table (hop2: cache query)
            graph.labelDdl
                .create(
                    EntityName(database, wishlistTable),
                    LabelCreateRequest(
                        desc = "wishlist",
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
                    ),
                ).test()
                .assertNext { it.status.name shouldBe "CREATED" }
                .verifyComplete()

            // Insert follows: user1 → user2, user1 → user3
            mutationService
                .mutate(
                    database,
                    followsTable,
                    mapper
                        .readValue<EdgeBulkMutationRequest>(
                            """
                            {
                              "mutations": [
                                {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2000", "properties": {"permission": "na", "createdAt": 100}}},
                                {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2001", "properties": {"permission": "na", "createdAt": 200}}}
                              ]
                            }
                            """.trimIndent(),
                        ).mutations,
                ).test()
                .assertNext { }
                .verifyComplete()

            // Insert wishlist: user2 → productA, user3 → productB, user3 → productC
            mutationService
                .mutate(
                    database,
                    wishlistTable,
                    mapper
                        .readValue<EdgeBulkMutationRequest>(
                            """
                            {
                              "mutations": [
                                {"type": "INSERT", "edge": {"version": 1, "source": "2000", "target": "5000", "properties": {"permission": "na", "createdAt": 300}}},
                                {"type": "INSERT", "edge": {"version": 1, "source": "2001", "target": "5001", "properties": {"permission": "na", "createdAt": 400}}},
                                {"type": "INSERT", "edge": {"version": 1, "source": "2001", "target": "5002", "properties": {"permission": "na", "createdAt": 500}}}
                              ]
                            }
                            """.trimIndent(),
                        ).mutations,
                ).test()
                .assertNext { }
                .verifyComplete()

            // 2-hop query: user1's friends' wishlists
            val query =
                ActionbaseQuery(
                    query =
                        listOf(
                            ActionbaseQuery.Item.Scan(
                                name = "hop1",
                                database = database,
                                table = followsTable,
                                source = ActionbaseQuery.Vertex.Value(listOf("1000")),
                                direction = Direction.OUT,
                                index = GraphFixtures.index2,
                                limit = 100,
                                include = false,
                            ),
                            ActionbaseQuery.Item.Seek(
                                name = "hop2",
                                database = database,
                                table = wishlistTable,
                                source = ActionbaseQuery.Vertex.Ref(ref = "hop1", field = "target"),
                                direction = Direction.OUT,
                                cache = cacheName,
                                limit = 10,
                                include = true,
                            ),
                        ),
                )

            queryExecutor
                .query(query)
                .test()
                .assertNext { result ->
                    val hop2 = result["hop2"]!!
                    hop2.rows.size shouldBe 3

                    val targets = hop2.getColumn("target").filterNotNull().toSet()
                    targets shouldBe setOf(5000L, 5001L, 5002L)
                }.verifyComplete()
        }

        /**
         * 3-hop: user1's friends' wishlists' reviews
         *
         * hop1: SCAN follows (index: created_at_desc)
         * |             row key             | qualifier |           value          |
         * |---------------------------------|-----------|--------------------------|
         * | hash|1000|T|IDX|OUT|I|~200|2001 | default   | version=1, createdAt=200 |
         * | hash|1000|T|IDX|OUT|I|~100|2000 | default   | version=1, createdAt=100 |
         *
         * hop2: SEEK wishlist (cache: created_at_desc)
         * |        row key       | qualifier (DESC) |           value          |
         * |----------------------|------------------|--------------------------|
         * | hash|2000|T|-6|OUT|C | ~300 | 5000      | version=1, createdAt=300 |
         * | hash|2001|T|-6|OUT|C | ~400 | 5001      | version=1, createdAt=400 |
         *
         * hop3: SEEK reviews (cache: created_at_desc)
         * |        row key       | qualifier (DESC) |           value          |
         * |----------------------|------------------|--------------------------|
         * | hash|5000|T|-6|OUT|C | ~500 | 8000      | version=1, createdAt=500 |
         * | hash|5001|T|-6|OUT|C | ~700 | 8002      | version=1, createdAt=700 |
         * |                      | ~600 | 8001      | version=1, createdAt=600 |
         *
         * Expected: [8000,8001,8002]
         */
        "3-hop: scan → cache → cache (N-hop context chaining)" {
            val database = GraphFixtures.serviceName
            val followsTable = "follows_3hop"
            val wishlistTable = "wishlist_3hop"
            val reviewsTable = "reviews_3hop"
            val cacheName = "created_at_desc"
            val cacheConfig =
                listOf(
                    Cache(
                        cache = cacheName,
                        fields = listOf(IndexField("createdAt", Order.DESC)),
                        limit = 100,
                    ),
                )

            // Create 3 tables
            graph.labelDdl
                .create(
                    EntityName(database, followsTable),
                    LabelCreateRequest(
                        desc = "follows",
                        type = LabelType.INDEXED,
                        schema = GraphFixtures.sampleSchema,
                        dirType = DirectionType.BOTH,
                        storage = GraphFixtures.datastoreStorage,
                        indices = GraphFixtures.sampleIndices,
                    ),
                ).test()
                .assertNext { it.status.name shouldBe "CREATED" }
                .verifyComplete()

            graph.labelDdl
                .create(
                    EntityName(database, wishlistTable),
                    LabelCreateRequest(
                        desc = "wishlist",
                        type = LabelType.INDEXED,
                        schema = GraphFixtures.sampleSchema,
                        dirType = DirectionType.BOTH,
                        storage = GraphFixtures.datastoreStorage,
                        indices = GraphFixtures.sampleIndices,
                        caches = cacheConfig,
                    ),
                ).test()
                .assertNext { it.status.name shouldBe "CREATED" }
                .verifyComplete()

            graph.labelDdl
                .create(
                    EntityName(database, reviewsTable),
                    LabelCreateRequest(
                        desc = "reviews",
                        type = LabelType.INDEXED,
                        schema = GraphFixtures.sampleSchema,
                        dirType = DirectionType.BOTH,
                        storage = GraphFixtures.datastoreStorage,
                        indices = GraphFixtures.sampleIndices,
                        caches = cacheConfig,
                    ),
                ).test()
                .assertNext { it.status.name shouldBe "CREATED" }
                .verifyComplete()

            // hop1 data: user1 → user2, user1 → user3
            mutationService
                .mutate(
                    database,
                    followsTable,
                    mapper
                        .readValue<EdgeBulkMutationRequest>(
                            """
                            {
                              "mutations": [
                                {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2000", "properties": {"permission": "na", "createdAt": 100}}},
                                {"type": "INSERT", "edge": {"version": 1, "source": "1000", "target": "2001", "properties": {"permission": "na", "createdAt": 200}}}
                              ]
                            }
                            """.trimIndent(),
                        ).mutations,
                ).test()
                .assertNext { }
                .verifyComplete()

            // hop2 data: user2 → productA, user3 → productB
            mutationService
                .mutate(
                    database,
                    wishlistTable,
                    mapper
                        .readValue<EdgeBulkMutationRequest>(
                            """
                            {
                              "mutations": [
                                {"type": "INSERT", "edge": {"version": 1, "source": "2000", "target": "5000", "properties": {"permission": "na", "createdAt": 300}}},
                                {"type": "INSERT", "edge": {"version": 1, "source": "2001", "target": "5001", "properties": {"permission": "na", "createdAt": 400}}}
                              ]
                            }
                            """.trimIndent(),
                        ).mutations,
                ).test()
                .assertNext { }
                .verifyComplete()

            // hop3 data: productA → reviewX, productB → reviewY, productB → reviewZ
            mutationService
                .mutate(
                    database,
                    reviewsTable,
                    mapper
                        .readValue<EdgeBulkMutationRequest>(
                            """
                            {
                              "mutations": [
                                {"type": "INSERT", "edge": {"version": 1, "source": "5000", "target": "8000", "properties": {"permission": "na", "createdAt": 500}}},
                                {"type": "INSERT", "edge": {"version": 1, "source": "5001", "target": "8001", "properties": {"permission": "na", "createdAt": 600}}},
                                {"type": "INSERT", "edge": {"version": 1, "source": "5001", "target": "8002", "properties": {"permission": "na", "createdAt": 700}}}
                              ]
                            }
                            """.trimIndent(),
                        ).mutations,
                ).test()
                .assertNext { }
                .verifyComplete()

            // 3-hop query: user1's friends' wishlists' reviews
            val query =
                ActionbaseQuery(
                    query =
                        listOf(
                            ActionbaseQuery.Item.Scan(
                                name = "hop1",
                                database = database,
                                table = followsTable,
                                source = ActionbaseQuery.Vertex.Value(listOf("1000")),
                                direction = Direction.OUT,
                                index = GraphFixtures.index2,
                                limit = 100,
                                include = false,
                            ),
                            ActionbaseQuery.Item.Seek(
                                name = "hop2",
                                database = database,
                                table = wishlistTable,
                                source = ActionbaseQuery.Vertex.Ref(ref = "hop1", field = "target"),
                                direction = Direction.OUT,
                                cache = cacheName,
                                limit = 10,
                                include = false,
                            ),
                            ActionbaseQuery.Item.Seek(
                                name = "hop3",
                                database = database,
                                table = reviewsTable,
                                source = ActionbaseQuery.Vertex.Ref(ref = "hop2", field = "target"),
                                direction = Direction.OUT,
                                cache = cacheName,
                                limit = 10,
                                include = true,
                            ),
                        ),
                )

            queryExecutor
                .query(query)
                .test()
                .assertNext { result ->
                    val hop3 = result["hop3"]!!
                    hop3.rows.size shouldBe 3

                    val targets = hop3.getColumn("target").filterNotNull().toSet()
                    targets shouldBe setOf(8000L, 8001L, 8002L)
                }.verifyComplete()
        }
    }) {
    companion object {
        val mapper = jacksonObjectMapper()
    }
}
