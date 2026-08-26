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
import com.kakao.actionbase.engine.query.PreparedQuery
import com.kakao.actionbase.engine.service.MutationService
import com.kakao.actionbase.v2.core.code.Index
import com.kakao.actionbase.v2.core.code.hbase.Order
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
 * The whole request over real tables: a `TOPK` step reads a materialized ranking, a `GET` step reads
 * when each ranked target was last paid for, and a `SQL` transform joins the two.
 *
 * Why the join is an outer one shows up here — a product group can rank high and still have no recent
 * order, and it has to survive with a fallback rather than disappear.
 */
class TransformQuerySpec :
    StringSpec(
        {
            val database = GraphFixtures.serviceName
            val orderTable = "orders"
            val rankTable = "orders__topk"
            val paidTable = "paid"
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

            fun createOrderTable() {
                graph.labelDdl
                    .create(
                        EntityName(database, orderTable),
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
                                        fields =
                                            listOf(
                                                Group.Field(
                                                    "purchasedAt",
                                                    bucket =
                                                        Bucket.Date(
                                                            name = "day",
                                                            unit = Bucket.ValueUnit.MILLISECOND,
                                                            timezone = "UTC",
                                                            format = "yyyy-MM-dd",
                                                        ),
                                                ),
                                            ),
                                        directionType = V3DirectionType.OUT,
                                        aggregations =
                                            Aggregations(
                                                topk = listOf(Topk(topk = topkName, entity = "source", dimension = "target", rank = "$database.$rankTable")),
                                            ),
                                    ),
                                ),
                        ),
                    ).test()
                    .assertNext { it.status.name shouldBe "CREATED" }
                    .verifyComplete()
            }

            fun createPaidTable() {
                graph.labelDdl
                    .create(
                        EntityName(database, paidTable),
                        LabelCreateRequest(
                            desc = "last payment per item",
                            type = LabelType.INDEXED,
                            schema =
                                EdgeSchema(
                                    VertexField(VertexType.STRING),
                                    VertexField(VertexType.STRING),
                                    listOf(Field("paidAt", DataType.LONG, false)),
                                ),
                            dirType = DirectionType.OUT,
                            storage = GraphFixtures.datastoreStorage,
                        ),
                    ).test()
                    .assertNext { it.status.name shouldBe "CREATED" }
                    .verifyComplete()
            }

            fun createTables() {
                createRankTable()
                createOrderTable()
                createPaidTable()
            }

            fun seed(
                table: String,
                mutations: String,
            ) {
                mutationService
                    .mutate(database, table, mapper.readValue<EdgeBulkMutationRequest>(mutations).mutations)
                    .test()
                    .assertNext { }
                    .verifyComplete()
            }

            /**
             * The ranking's `source` is the composed rank key, not the entity, so the join matches on `target`
             * alone. Both sides are already limited to one entity by the steps that read them.
             */
            fun preparedQuery(): PreparedQuery =
                PreparedQuery.of(
                    ActionbaseQuery(
                        fetch =
                            listOf(
                                ActionbaseQuery.Item.Topk(
                                    name = "hop1",
                                    database = database,
                                    table = orderTable,
                                    topk = topkName,
                                    entity = ActionbaseQuery.Vertex.Value(listOf("{entity}")),
                                    limit = 100,
                                ),
                                ActionbaseQuery.Item.Get(
                                    name = "hop2",
                                    database = database,
                                    table = paidTable,
                                    source = ActionbaseQuery.Vertex.Value(listOf("{entity}")),
                                    target = ActionbaseQuery.Vertex.Ref(ref = "hop1", field = "target"),
                                ),
                            ),
                        transform =
                            listOf(
                                ActionbaseQuery.Transform.Sql(
                                    name = "result",
                                    sql =
                                        """
                                        SELECT hop1.target AS productGroupId
                                             , hop1.metric AS metric
                                             , IFNULL(hop2.paidAt, -1) AS paidAt
                                        FROM      hop1
                                        LEFT JOIN hop2 ON hop1.target = hop2.target
                                        ORDER BY  metric DESC, paidAt DESC
                                        """.trimIndent(),
                                ),
                            ),
                    ),
                )

            /**
             * test.orders__topk (ranking for user1)     test.paid
             * | target | metric |                       | target | paidAt     |
             * |--------|--------|                       |--------|------------|
             * | item1  |     30 |                       | item1  | 1700000000 |
             * | item2  |     20 |                       | item3  | 1600000000 |
             * | item3  |     10 |
             *
             * `item2` is ranked but never paid for, so the transform has to keep it at `-1`.
             */
            "a SQL transform joins a materialized ranking with a GET over real tables" {
                createTables()
                seed(
                    rankTable,
                    """
                    {
                      "mutations": [
                        {"type": "INSERT", "edge": {"version": 1, "source": "test|orders|top_purchased|user1", "target": "item1", "properties": {"metric": 30}}},
                        {"type": "INSERT", "edge": {"version": 1, "source": "test|orders|top_purchased|user1", "target": "item2", "properties": {"metric": 20}}},
                        {"type": "INSERT", "edge": {"version": 1, "source": "test|orders|top_purchased|user1", "target": "item3", "properties": {"metric": 10}}}
                      ]
                    }
                    """.trimIndent(),
                )
                seed(
                    paidTable,
                    """
                    {
                      "mutations": [
                        {"type": "INSERT", "edge": {"version": 1, "source": "user1", "target": "item1", "properties": {"paidAt": 1700000000}}},
                        {"type": "INSERT", "edge": {"version": 1, "source": "user1", "target": "item3", "properties": {"paidAt": 1600000000}}}
                      ]
                    }
                    """.trimIndent(),
                )

                val prepared = preparedQuery()
                prepared.parameters shouldBe setOf("entity")

                queryExecutor
                    .query(prepared, mapOf("entity" to "user1"))
                    .test()
                    .assertNext { result ->
                        // Only the transform comes back: neither fetch step asked to be included.
                        result.keys shouldBe setOf("result")
                        result
                            .getValue("result")
                            .rows
                            .map { listOf(it.data["productGroupId"], it.data["metric"], it.data["paidAt"]) } shouldBe
                            listOf(
                                listOf("item1", 30L, 1_700_000_000L),
                                listOf("item2", 20L, -1L),
                                listOf("item3", 10L, 1_600_000_000L),
                            )
                    }.verifyComplete()
            }

            /**
             * An entity nobody ranked is an ordinary request, not an error. The step finds no rows, and a
             * frame with no rows still has to say what its columns are — a transform planned against a
             * schema-less frame cannot resolve a single column name.
             */
            "an entity with no ranking returns no rows, under the columns it would have had" {
                createTables()

                queryExecutor
                    .query(preparedQuery(), mapOf("entity" to "nobody"))
                    .test()
                    .assertNext { result ->
                        val transformed = result.getValue("result")
                        transformed.rows shouldBe emptyList()
                        transformed.schema.fields.map { it.name } shouldBe listOf("productGroupId", "metric", "paidAt")
                    }.verifyComplete()
            }
        },
    )
