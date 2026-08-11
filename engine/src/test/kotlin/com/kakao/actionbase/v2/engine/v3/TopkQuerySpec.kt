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

class TopkQuerySpec :
    StringSpec(
        {
            val database = GraphFixtures.serviceName
            val sourceTable = "orders"
            val rankTable = "orders__topk"
            val topkName = "top_purchased"
            val globalTopkName = "top_purchased_global"
            val categoryTopkName = "top_purchased_by_category"
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
                                    listOf(Field("purchasedAt", DataType.LONG, false), Field("category", DataType.STRING, false)),
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
                                                        Topk(topk = topkName, entity = "source", dimension = "target", rank = "$database.$rankTable"),
                                                        Topk(topk = globalTopkName, entity = "__GLOBAL__", dimension = "target", rank = "$database.$rankTable"),
                                                    ),
                                            ),
                                    ),
                                    Group(
                                        group = "purchased_count_by_category",
                                        type = GroupType.COUNT,
                                        fields = listOf(Group.Field("category"), Group.Field("purchasedAt", bucket = Bucket.Date(name = "day", unit = Bucket.ValueUnit.MILLISECOND, timezone = "UTC", format = "yyyy-MM-dd"))),
                                        directionType = V3DirectionType.OUT,
                                        aggregations =
                                            Aggregations(
                                                topk = listOf(Topk(topk = categoryTopkName, entity = "source", dimension = "target", rank = "$database.$rankTable")),
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
             * |           row key (source)         | target | metric |
             * |------------------------------------|--------|--------|
             * | test\|orders\|top_purchased\|user1 | item1 |      3 |
             * | test\|orders\|top_purchased\|user1 | item2 |      1 |
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
                        fetch =
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
             * |           row key (source)         | target | metric |
             * |------------------------------------|--------|--------|
             * | test\|orders\|top_purchased\|user2 | itemA |      5 |
             * | test\|orders\|top_purchased\|user3 | itemB |      2 |
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
                        fetch =
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

            /**
             * `top_purchased_global` declares the sentinel as its entity, so it holds one ranking for everyone:
             *
             * test.orders__topk (rank)
             * |                row key (source)                 | target | metric |
             * |-------------------------------------------------|--------|--------|
             * | test\|orders\|top_purchased_global\|__GLOBAL__  | item1  |      3 |
             * | test\|orders\|top_purchased_global\|__GLOBAL__  | item2  |      1 |
             *
             * The caller has no entity to name, and the step fills the sentinel from the config on its own.
             */
            "TOPK step reads a global ranking without being given an entity" {
                createRankTable()
                createSourceTable()
                seedRankRows(
                    """
                    {
                      "mutations": [
                        {"type": "INSERT", "edge": {"version": 1, "source": "test|orders|top_purchased_global|__GLOBAL__", "target": "item1", "properties": {"metric": 3}}},
                        {"type": "INSERT", "edge": {"version": 1, "source": "test|orders|top_purchased_global|__GLOBAL__", "target": "item2", "properties": {"metric": 1}}}
                      ]
                    }
                    """.trimIndent(),
                )

                val query =
                    ActionbaseQuery(
                        fetch =
                            listOf(
                                ActionbaseQuery.Item.Topk(
                                    name = "ranked",
                                    database = database,
                                    table = sourceTable,
                                    topk = globalTopkName,
                                    limit = 10,
                                    include = true,
                                ),
                            ),
                    )

                queryExecutor
                    .query(query)
                    .test()
                    .assertNext { result ->
                        result.getValue("ranked").getColumn("target") shouldBe listOf("item1", "item2")
                    }.verifyComplete()
            }

            /**
             * `top_purchased_by_category` is declared on a group that carries an unbucketed `category`, so
             * `user1` has one ranking per category:
             *
             * test.orders__topk (rank)
             * |                    row key (source)                     | target | metric |
             * |---------------------------------------------------------|--------|--------|
             * | test\|orders\|top_purchased_by_category\|user1\|fruit   | itemA  |      5 |
             * | test\|orders\|top_purchased_by_category\|user1\|meat    | itemB  |      2 |
             *
             * The caller names `category` and the step puts that value where the group says it goes, so asking
             * for `fruit` leaves the meat ranking out.
             */
            "TOPK step picks the ranking named by its dimension values" {
                createRankTable()
                createSourceTable()
                seedRankRows(
                    """
                    {
                      "mutations": [
                        {"type": "INSERT", "edge": {"version": 1, "source": "test|orders|top_purchased_by_category|user1|fruit", "target": "itemA", "properties": {"metric": 5}}},
                        {"type": "INSERT", "edge": {"version": 1, "source": "test|orders|top_purchased_by_category|user1|meat", "target": "itemB", "properties": {"metric": 2}}}
                      ]
                    }
                    """.trimIndent(),
                )

                val query =
                    ActionbaseQuery(
                        fetch =
                            listOf(
                                ActionbaseQuery.Item.Topk(
                                    name = "ranked",
                                    database = database,
                                    table = sourceTable,
                                    topk = categoryTopkName,
                                    entity = ActionbaseQuery.Vertex.Value(listOf("user1")),
                                    dimensionValues = mapOf("category" to "fruit"),
                                    limit = 10,
                                    include = true,
                                ),
                            ),
                    )

                queryExecutor
                    .query(query)
                    .test()
                    .assertNext { result ->
                        result.getValue("ranked").getColumn("target") shouldBe listOf("itemA")
                    }.verifyComplete()
            }
        },
    )
