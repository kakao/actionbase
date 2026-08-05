package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.core.metadata.common.DirectionType as V3DirectionType

import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest
import com.kakao.actionbase.core.metadata.common.Aggregations
import com.kakao.actionbase.core.metadata.common.Bucket
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.GroupType
import com.kakao.actionbase.core.metadata.common.Topk
import com.kakao.actionbase.engine.query.ActionbaseQuery
import com.kakao.actionbase.engine.query.ActionbaseQueryExecutor
import com.kakao.actionbase.engine.service.MutationService
import com.kakao.actionbase.v2.core.code.Index
import com.kakao.actionbase.v2.core.code.hbase.Order
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

/**
 * The `TOPK` query step reads a *already materialized* ranking. Writing rank rows is the aggregation
 * path's job (see `TopkAggregationHandlerTest`), so these tests seed the rank table directly and then
 * assert what the step itself owns: resolving the rank table from the top-K config, recomposing the
 * rank key, and returning the rows in rank order.
 */
class TopkQuerySpec :
    StringSpec({
        val database = GraphFixtures.serviceName
        val sourceTable = "orders"
        val rankTable = "orders__topk"
        val topkName = "top_purchased"
        val mapper = jacksonObjectMapper()

        lateinit var graph: Graph
        lateinit var mutationService: MutationService
        lateinit var queryExecutor: ActionbaseQueryExecutor

        beforeTest {
            graph = GraphFixtures.create()
            val engine = V2BackedEngine(graph)
            mutationService = MutationService(engine)
            queryExecutor = ActionbaseQueryExecutor(engine)
        }

        afterTest {
            graph.close()
        }

        // One row per ranked value, indexed by `metric` DESC — that index *is* the top-K read order.
        fun createRankTable() {
            graph.labelDdl
                .create(
                    EntityName(database, rankTable),
                    LabelCreateRequest(
                        desc = "rank rows",
                        type = LabelType.INDEXED,
                        schema =
                            EdgeSchema(
                                VertexField(VertexType.STRING),
                                VertexField(VertexType.STRING),
                                listOf(Field("metric", DataType.LONG, false)),
                            ),
                        dirType = DirectionType.OUT,
                        storage = GraphFixtures.datastoreStorage,
                        indices = listOf(Index("metric_desc", listOf(Index.Field("metric", Order.DESC)))),
                    ),
                ).test()
                .assertNext { it.status.name shouldBe "CREATED" }
                .verifyComplete()
        }

        // The table the ranking is declared on. Only the top-K config steers the read, so the group
        // itself stays minimal.
        fun createSourceTable() {
            graph.labelDdl
                .create(
                    EntityName(database, sourceTable),
                    LabelCreateRequest(
                        desc = "purchases",
                        type = LabelType.INDEXED,
                        schema =
                            EdgeSchema(
                                VertexField(VertexType.STRING),
                                VertexField(VertexType.STRING),
                                listOf(Field("purchasedAt", DataType.LONG, false)),
                            ),
                        dirType = DirectionType.BOTH,
                        storage = GraphFixtures.datastoreStorage,
                        groups =
                            listOf(
                                Group(
                                    group = "purchased_count",
                                    type = GroupType.COUNT,
                                    fields = listOf(Group.Field("purchasedAt", bucket = Bucket.Date(name = "day", unit = Bucket.ValueUnit.MILLISECOND, timezone = "UTC", format = "yyyy-MM-dd"))),
                                    directionType = V3DirectionType.OUT,
                                    aggregations =
                                        Aggregations(
                                            topk =
                                                listOf(
                                                    Topk(
                                                        topk = topkName,
                                                        entity = "source",
                                                        dimension = "target",
                                                        rank = "$database.$rankTable",
                                                    ),
                                                ),
                                        ),
                                ),
                            ),
                    ),
                ).test()
                .assertNext { it.status.name shouldBe "CREATED" }
                .verifyComplete()
        }

        fun seedRankRows(mutations: String) {
            mutationService
                .mutate(database, rankTable, mapper.readValue<EdgeBulkMutationRequest>(mutations).mutations)
                .test()
                .assertNext { }
                .verifyComplete()
        }

        /**
         * test.orders__topk (rank)
         * |           row key (source)       | target | metric |
         * |----------------------------------|--------|--------|
         * | test\|orders\|top_purchased\|user1 | item1  |      3 |
         * | test\|orders\|top_purchased\|user1 | item2  |      1 |
         *
         * The step names the *source* table (`orders`) and recomposes that key itself, so the caller
         * never addresses `orders__topk`.
         */
        "TOPK step returns a materialized ranking in metric order" {
            createRankTable()
            createSourceTable()
            seedRankRows(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "source": "test|orders|top_purchased|user1", "target": "item1", "properties": {"metric": 3}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": "test|orders|top_purchased|user1", "target": "item2", "properties": {"metric": 1}}}
                  ]
                }
                """.trimIndent(),
            )

            val query =
                ActionbaseQuery(
                    query =
                        listOf(
                            ActionbaseQuery.Item.Topk(
                                name = "ranked",
                                database = database,
                                table = sourceTable,
                                topk = topkName,
                                entity = ActionbaseQuery.Vertex.Value(listOf("user1")),
                                limit = 10,
                                include = true,
                            ),
                        ),
                )

            queryExecutor
                .query(query)
                .test()
                .assertNext { result ->
                    val ranked = result.getValue("ranked")
                    ranked.getColumn("target").filterNotNull() shouldBe listOf("item1", "item2")
                    ranked.getColumn("metric").filterNotNull() shouldBe listOf(3L, 1L)
                }.verifyComplete()
        }

        /**
         * Per-entity rankings are the point of `entity` being a vertex: hop1 scans who `user1` follows,
         * and the step then reads one ranking per followed user.
         *
         * test.follows (hop1: scan by created_at_desc)
         * | source | target | createdAt |
         * |--------|--------|-----------|
         * | user1  | user2  |       100 |
         * | user1  | user3  |       200 |
         *
         * test.orders__topk (rank)
         * |           row key (source)        | target | metric |
         * |-----------------------------------|--------|--------|
         * | test\|orders\|top_purchased\|user2 | itemA  |      5 |
         * | test\|orders\|top_purchased\|user3 | itemB  |      2 |
         */
        "TOPK step reads one ranking per entity produced by the previous hop" {
            val followsTable = "follows"

            createRankTable()
            createSourceTable()

            graph.labelDdl
                .create(
                    EntityName(database, followsTable),
                    LabelCreateRequest(
                        desc = "follows",
                        type = LabelType.INDEXED,
                        schema =
                            EdgeSchema(
                                VertexField(VertexType.STRING),
                                VertexField(VertexType.STRING),
                                listOf(Field("createdAt", DataType.LONG, false)),
                            ),
                        dirType = DirectionType.BOTH,
                        storage = GraphFixtures.datastoreStorage,
                        indices = listOf(Index("created_at_desc", listOf(Index.Field("createdAt", Order.DESC)))),
                    ),
                ).test()
                .assertNext { it.status.name shouldBe "CREATED" }
                .verifyComplete()

            mutationService
                .mutate(
                    database,
                    followsTable,
                    mapper
                        .readValue<EdgeBulkMutationRequest>(
                            """
                            {
                              "mutations": [
                                {"type": "INSERT", "edge": {"version": 1, "source": "user1", "target": "user2", "properties": {"createdAt": 100}}},
                                {"type": "INSERT", "edge": {"version": 1, "source": "user1", "target": "user3", "properties": {"createdAt": 200}}}
                              ]
                            }
                            """.trimIndent(),
                        ).mutations,
                ).test()
                .assertNext { }
                .verifyComplete()

            seedRankRows(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "source": "test|orders|top_purchased|user2", "target": "itemA", "properties": {"metric": 5}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": "test|orders|top_purchased|user3", "target": "itemB", "properties": {"metric": 2}}}
                  ]
                }
                """.trimIndent(),
            )

            val query =
                ActionbaseQuery(
                    query =
                        listOf(
                            ActionbaseQuery.Item.Scan(
                                name = "hop1",
                                database = database,
                                table = followsTable,
                                source = ActionbaseQuery.Vertex.Value(listOf("user1")),
                                direction = Direction.OUT,
                                index = "created_at_desc",
                                limit = 100,
                            ),
                            ActionbaseQuery.Item.Topk(
                                name = "ranked",
                                database = database,
                                table = sourceTable,
                                topk = topkName,
                                entity = ActionbaseQuery.Vertex.Ref(ref = "hop1", field = "target"),
                                limit = 10,
                                include = true,
                            ),
                        ),
                )

            queryExecutor
                .query(query)
                .test()
                .assertNext { result ->
                    val ranked = result.getValue("ranked")
                    ranked.getColumn("target").filterNotNull().toSet() shouldBe setOf("itemA", "itemB")
                }.verifyComplete()
        }
    })
