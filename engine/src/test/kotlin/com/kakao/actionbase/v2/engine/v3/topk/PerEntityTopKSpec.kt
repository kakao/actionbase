package com.kakao.actionbase.v2.engine.v3.topk

import com.kakao.actionbase.core.metadata.common.Bucket
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.GroupType
import com.kakao.actionbase.engine.service.QueryService
import com.kakao.actionbase.v2.core.code.Index
import com.kakao.actionbase.v2.core.code.hbase.Order
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.core.metadata.DirectionType
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.core.metadata.MutationMode
import com.kakao.actionbase.v2.core.types.DataType
import com.kakao.actionbase.v2.core.types.EdgeSchema
import com.kakao.actionbase.v2.core.types.Field
import com.kakao.actionbase.v2.core.types.VertexField
import com.kakao.actionbase.v2.core.types.VertexType
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.service.ddl.LabelCreateRequest
import com.kakao.actionbase.v2.engine.test.GraphFixtures
import com.kakao.actionbase.v2.engine.v3.V2BackedEngine
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import reactor.kotlin.test.test

/**
 * E2E scenario for Per-Entity Top-K (issue #369).
 *
 * Covers three layers:
 *   - Metadata : purchase (MULTI_EDGE with topk-declared groups) and _purchase_score (EDGE with score index)
 *   - Mutation : purchase events increment EdgeGroup(_count, _count_1y) automatically
 *   - Query    : topk API scans the score index and returns results in O(K)
 *
 * The background job (Job1 and TTL Scheduler) is simulated inline rather than run as a separate process.
 */
class PerEntityTopKSpec :
    StringSpec({

        val database = GraphFixtures.serviceName
        val purchaseTable = "purchase"
        val scoreTable = "_purchase_score"

        lateinit var graph: Graph
        lateinit var queryService: QueryService

        // purchase table (MULTI_EDGE) - groups declare topk targets
        val purchaseRequest =
            LabelCreateRequest(
                desc = "purchase events",
                type = LabelType.MULTI_EDGE,
                schema =
                    EdgeSchema(
                        VertexField(VertexType.STRING, "user_id"),
                        VertexField(VertexType.STRING, "item_id"),
                        listOf(
                            Field("_id", DataType.STRING, false, "event id"),
                            Field("day", DataType.LONG, false, "purchase timestamp ms"),
                        ),
                    ),
                dirType = DirectionType.BOTH,
                storage = GraphFixtures.datastoreStorage,
                groups =
                    listOf(
                        Group(
                            group = "_count",
                            type = GroupType.COUNT,
                            fields = listOf(Group.Field(name = "_target")),
                            directionType = com.kakao.actionbase.core.metadata.common.DirectionType.OUT,
                            ttl = Long.MAX_VALUE,
                            topk = "top_purchased",
                        ),
                        Group(
                            group = "_count_1y",
                            type = GroupType.COUNT,
                            fields =
                                listOf(
                                    Group.Field(name = "_target"),
                                    Group.Field(
                                        name = "day",
                                        bucket =
                                            Bucket.Date(
                                                name = "day",
                                                unit = Bucket.ValueUnit.MILLISECOND,
                                                timezone = "+09:00",
                                                format = "yyyy-MM-dd",
                                            ),
                                    ),
                                ),
                            directionType = com.kakao.actionbase.core.metadata.common.DirectionType.OUT,
                            ttl = 31536000000L,
                            topk = "top_purchased_1y",
                        ),
                    ),
                readOnly = true,
                mode = MutationMode.SYNC,
            )

        // score table (EDGE) - single score field with DESC index for O(K) scan
        val scoreRequest =
            LabelCreateRequest(
                desc = "per-user item top-k",
                type = LabelType.INDEXED,
                schema =
                    EdgeSchema(
                        VertexField(VertexType.STRING, "{user}:{topk_name}"),
                        VertexField(VertexType.STRING, "item_id"),
                        listOf(
                            Field("score", DataType.LONG, false, "aggregated score"),
                        ),
                    ),
                dirType = DirectionType.OUT,
                storage = GraphFixtures.datastoreStorage,
                indices =
                    listOf(
                        Index(
                            "score",
                            listOf(Index.Field("score", Order.DESC)),
                            "top-k by score descending",
                        ),
                    ),
                mode = MutationMode.SYNC,
            )

        beforeTest {
            graph = GraphFixtures.create()
            queryService = QueryService(V2BackedEngine(graph))

            graph.labelDdl.create(EntityName(database, purchaseTable), purchaseRequest).block()
            graph.labelDdl.create(EntityName(database, scoreTable), scoreRequest).block()
            graph.updateAllMetadata().block()
        }

        afterTest {
            graph.close()
        }

        // Mutation tests

        "mutation - EdgeGroup(_count) incremented per (source, target)" {
            // given: two purchases of item_X and one purchase of item_Y by user_A
            val label = graph.getLabel(EntityName(database, purchaseTable))
            listOf(
                com.kakao.actionbase.v2.core.edge.Edge(1000L, "evt-001", "evt-001", mapOf("_source" to "user_A", "_target" to "item_X", "day" to 1749945600000L)),
                com.kakao.actionbase.v2.core.edge.Edge(1001L, "evt-002", "evt-002", mapOf("_source" to "user_A", "_target" to "item_X", "day" to 1749945700000L)),
                com.kakao.actionbase.v2.core.edge.Edge(1002L, "evt-003", "evt-003", mapOf("_source" to "user_A", "_target" to "item_Y", "day" to 1749945800000L)),
            ).forEach { edge ->
                graph.mutate(label.name, label, listOf(edge.toTraceEdge()), com.kakao.actionbase.v2.core.metadata.EdgeOperation.INSERT).block()
            }

            // then: counts reflect two item_X and one item_Y
            queryService
                .multiEdgeCount(database, purchaseTable, start = listOf("user_A"), direction = Direction.OUT, target = "item_X")
                .test()
                .assertNext { it.counts.first().count shouldBe 2L }
                .verifyComplete()

            queryService
                .multiEdgeCount(database, purchaseTable, start = listOf("user_A"), direction = Direction.OUT, target = "item_Y")
                .test()
                .assertNext { it.counts.first().count shouldBe 1L }
                .verifyComplete()
        }

        "mutation - EdgeGroup(_count_1y) incremented with day bucket" {
            // given: two purchases of item_X by user_A
            val label = graph.getLabel(EntityName(database, purchaseTable))
            listOf(
                com.kakao.actionbase.v2.core.edge.Edge(1000L, "evt-001", "evt-001", mapOf("_source" to "user_A", "_target" to "item_X", "day" to 1749945600000L)),
                com.kakao.actionbase.v2.core.edge.Edge(1001L, "evt-002", "evt-002", mapOf("_source" to "user_A", "_target" to "item_X", "day" to 1749945700000L)),
            ).forEach { edge ->
                graph.mutate(label.name, label, listOf(edge.toTraceEdge()), com.kakao.actionbase.v2.core.metadata.EdgeOperation.INSERT).block()
            }

            // then: 1y window count equals 2
            queryService
                .multiEdgeCount(
                    database, purchaseTable,
                    start = listOf("user_A"), direction = Direction.OUT, target = "item_X",
                    group = "_count_1y", ranges = "day:between:now-365d:now",
                )
                .test()
                .assertNext { it.counts.first().count shouldBe 2L }
                .verifyComplete()
        }

        // Background job simulation tests

        "background job (Job1) - score upsert after multiEdgeCount (all-time)" {
            // given: two purchases of item_X by user_A
            val purchaseLabel = graph.getLabel(EntityName(database, purchaseTable))
            listOf(
                com.kakao.actionbase.v2.core.edge.Edge(1000L, "evt-001", "evt-001", mapOf("_source" to "user_A", "_target" to "item_X", "day" to 1749945600000L)),
                com.kakao.actionbase.v2.core.edge.Edge(1001L, "evt-002", "evt-002", mapOf("_source" to "user_A", "_target" to "item_X", "day" to 1749945700000L)),
            ).forEach { edge ->
                graph.mutate(purchaseLabel.name, purchaseLabel, listOf(edge.toTraceEdge()), com.kakao.actionbase.v2.core.metadata.EdgeOperation.INSERT).block()
            }

            // when: Job1 step 3.1 — fetch all-time count for (user_A, item_X)
            val count =
                queryService
                    .multiEdgeCount(database, purchaseTable, start = listOf("user_A"), direction = Direction.OUT, target = "item_X")
                    .block()!!
                    .counts.first().count

            // when: Job1 step 3.2 — upsert score with composite source key "user_A:top_purchased"
            val scoreLabel = graph.getLabel(EntityName(database, scoreTable))
            val scoreEdge = com.kakao.actionbase.v2.core.edge.Edge(count, "user_A:top_purchased", "item_X", mapOf("score" to count))
            graph.mutate(scoreLabel.name, scoreLabel, listOf(scoreEdge.toTraceEdge()), com.kakao.actionbase.v2.core.metadata.EdgeOperation.INSERT).block()

            // then: score table stores the aggregated count
            queryService
                .gets(database, scoreTable, listOf("user_A:top_purchased"), listOf("item_X"))
                .test()
                .assertNext { result ->
                    result.edges.first().properties["score"] shouldBe 2L
                }.verifyComplete()
        }

        "background job (Job1) - score upsert after multiEdgeCount (1y window)" {
            // given: one purchase of item_X by user_A within the last year
            val purchaseLabel = graph.getLabel(EntityName(database, purchaseTable))
            val edge = com.kakao.actionbase.v2.core.edge.Edge(1000L, "evt-001", "evt-001", mapOf("_source" to "user_A", "_target" to "item_X", "day" to 1749945600000L))
            graph.mutate(purchaseLabel.name, purchaseLabel, listOf(edge.toTraceEdge()), com.kakao.actionbase.v2.core.metadata.EdgeOperation.INSERT).block()

            // when: Job1 step 3.1 — fetch 1y window count for (user_A, item_X)
            val count =
                queryService
                    .multiEdgeCount(
                        database, purchaseTable,
                        start = listOf("user_A"), direction = Direction.OUT, target = "item_X",
                        group = "_count_1y", ranges = "day:between:now-365d:now",
                    )
                    .block()!!
                    .counts.first().count

            // when: Job1 step 3.2 — upsert score with composite source key "user_A:top_purchased_1y"
            val scoreLabel = graph.getLabel(EntityName(database, scoreTable))
            val scoreEdge = com.kakao.actionbase.v2.core.edge.Edge(count, "user_A:top_purchased_1y", "item_X", mapOf("score" to count))
            graph.mutate(scoreLabel.name, scoreLabel, listOf(scoreEdge.toTraceEdge()), com.kakao.actionbase.v2.core.metadata.EdgeOperation.INSERT).block()

            // then: score table stores the 1y window count
            queryService
                .gets(database, scoreTable, listOf("user_A:top_purchased_1y"), listOf("item_X"))
                .test()
                .assertNext { result ->
                    result.edges.first().properties["score"] shouldBe 1L
                }.verifyComplete()
        }

        // Top-K query tests

        "top-k (all-time) - items ordered by score DESC" {
            // given: score entries for user_A — item_X:5, item_Y:3, item_Z:1
            val scoreLabel = graph.getLabel(EntityName(database, scoreTable))
            listOf("item_X" to 5L, "item_Y" to 3L, "item_Z" to 1L).forEach { (item, score) ->
                val edge = com.kakao.actionbase.v2.core.edge.Edge(score, "user_A:top_purchased", item, mapOf("score" to score))
                graph.mutate(scoreLabel.name, scoreLabel, listOf(edge.toTraceEdge()), com.kakao.actionbase.v2.core.metadata.EdgeOperation.INSERT).block()
            }

            // when: top-k query with limit=10
            queryService
                .topk(database, scoreTable, "top_purchased", "user_A", Direction.OUT, limit = 10)
                .test()
                .assertNext { result ->
                    result.edges.size shouldBe 3
                    result.edges[0].target shouldBe "item_X"
                    result.edges[0].properties["score"] shouldBe 5L
                    result.edges[1].target shouldBe "item_Y"
                    result.edges[1].properties["score"] shouldBe 3L
                    result.edges[2].target shouldBe "item_Z"
                    result.edges[2].properties["score"] shouldBe 1L
                }.verifyComplete()
        }

        "top-k (1y window) - items ordered by score DESC" {
            // given: 1y score entries for user_A — item_X:5, item_Y:3
            val scoreLabel = graph.getLabel(EntityName(database, scoreTable))
            listOf("item_X" to 5L, "item_Y" to 3L).forEach { (item, score) ->
                val edge = com.kakao.actionbase.v2.core.edge.Edge(score, "user_A:top_purchased_1y", item, mapOf("score" to score))
                graph.mutate(scoreLabel.name, scoreLabel, listOf(edge.toTraceEdge()), com.kakao.actionbase.v2.core.metadata.EdgeOperation.INSERT).block()
            }

            // when: top-k query for 1y window
            queryService
                .topk(database, scoreTable, "top_purchased_1y", "user_A", Direction.OUT, limit = 10)
                .test()
                .assertNext { result ->
                    result.edges.size shouldBe 2
                    result.edges[0].target shouldBe "item_X"
                    result.edges[0].properties["score"] shouldBe 5L
                    result.edges[1].target shouldBe "item_Y"
                    result.edges[1].properties["score"] shouldBe 3L
                }.verifyComplete()
        }

        "top-k - different topk_names are independent" {
            // given: all-time score for item_X and 1y score for item_Y stored under separate topk keys
            val scoreLabel = graph.getLabel(EntityName(database, scoreTable))
            val edgeA = com.kakao.actionbase.v2.core.edge.Edge(42L, "user_A:top_purchased", "item_X", mapOf("score" to 42L))
            val edgeB = com.kakao.actionbase.v2.core.edge.Edge(30L, "user_A:top_purchased_1y", "item_Y", mapOf("score" to 30L))
            graph.mutate(scoreLabel.name, scoreLabel, listOf(edgeA.toTraceEdge()), com.kakao.actionbase.v2.core.metadata.EdgeOperation.INSERT).block()
            graph.mutate(scoreLabel.name, scoreLabel, listOf(edgeB.toTraceEdge()), com.kakao.actionbase.v2.core.metadata.EdgeOperation.INSERT).block()

            // when: querying top_purchased returns only item_X
            queryService
                .topk(database, scoreTable, "top_purchased", "user_A", Direction.OUT, limit = 10)
                .test()
                .assertNext { result ->
                    result.edges.size shouldBe 1
                    result.edges[0].target shouldBe "item_X"
                    result.edges[0].properties["score"] shouldBe 42L
                }.verifyComplete()

            // when: querying top_purchased_1y returns only item_Y
            queryService
                .topk(database, scoreTable, "top_purchased_1y", "user_A", Direction.OUT, limit = 10)
                .test()
                .assertNext { result ->
                    result.edges.size shouldBe 1
                    result.edges[0].target shouldBe "item_Y"
                    result.edges[0].properties["score"] shouldBe 30L
                }.verifyComplete()
        }

        "top-k - different users are independent" {
            // given: user_A purchased item_X, user_B purchased item_Y
            val scoreLabel = graph.getLabel(EntityName(database, scoreTable))
            val edgeA = com.kakao.actionbase.v2.core.edge.Edge(5L, "user_A:top_purchased", "item_X", mapOf("score" to 5L))
            val edgeB = com.kakao.actionbase.v2.core.edge.Edge(10L, "user_B:top_purchased", "item_Y", mapOf("score" to 10L))
            graph.mutate(scoreLabel.name, scoreLabel, listOf(edgeA.toTraceEdge()), com.kakao.actionbase.v2.core.metadata.EdgeOperation.INSERT).block()
            graph.mutate(scoreLabel.name, scoreLabel, listOf(edgeB.toTraceEdge()), com.kakao.actionbase.v2.core.metadata.EdgeOperation.INSERT).block()

            // when: user_A top-k returns item_X
            queryService
                .topk(database, scoreTable, "top_purchased", "user_A", Direction.OUT, limit = 10)
                .test()
                .assertNext { result ->
                    result.edges.size shouldBe 1
                    result.edges[0].target shouldBe "item_X"
                }.verifyComplete()

            // when: user_B top-k returns item_Y
            queryService
                .topk(database, scoreTable, "top_purchased", "user_B", Direction.OUT, limit = 10)
                .test()
                .assertNext { result ->
                    result.edges.size shouldBe 1
                    result.edges[0].target shouldBe "item_Y"
                }.verifyComplete()
        }

        "top-k - score update reflects new ranking" {
            // given: initial scores — item_X:3, item_Y:5 (item_Y leads)
            val scoreLabel = graph.getLabel(EntityName(database, scoreTable))
            listOf("item_X" to 3L, "item_Y" to 5L).forEach { (item, score) ->
                val edge = com.kakao.actionbase.v2.core.edge.Edge(score, "user_A:top_purchased", item, mapOf("score" to score))
                graph.mutate(scoreLabel.name, scoreLabel, listOf(edge.toTraceEdge()), com.kakao.actionbase.v2.core.metadata.EdgeOperation.INSERT).block()
            }

            // when: item_X score is updated to 10, overtaking item_Y
            val updated = com.kakao.actionbase.v2.core.edge.Edge(10L, "user_A:top_purchased", "item_X", mapOf("score" to 10L))
            graph.mutate(scoreLabel.name, scoreLabel, listOf(updated.toTraceEdge()), com.kakao.actionbase.v2.core.metadata.EdgeOperation.UPDATE).block()

            // then: item_X ranks first with score 10
            queryService
                .topk(database, scoreTable, "top_purchased", "user_A", Direction.OUT, limit = 10)
                .test()
                .assertNext { result ->
                    result.edges[0].target shouldBe "item_X"
                    result.edges[0].properties["score"] shouldBe 10L
                    result.edges[1].target shouldBe "item_Y"
                    result.edges[1].properties["score"] shouldBe 5L
                }.verifyComplete()
        }

        "top-k - limit respected (O(K))" {
            // given: five items with distinct scores
            val scoreLabel = graph.getLabel(EntityName(database, scoreTable))
            listOf("item_A" to 50L, "item_B" to 40L, "item_C" to 30L, "item_D" to 20L, "item_E" to 10L).forEach { (item, score) ->
                val edge = com.kakao.actionbase.v2.core.edge.Edge(score, "user_A:top_purchased", item, mapOf("score" to score))
                graph.mutate(scoreLabel.name, scoreLabel, listOf(edge.toTraceEdge()), com.kakao.actionbase.v2.core.metadata.EdgeOperation.INSERT).block()
            }

            // when: querying with limit=3 returns only the top 3
            queryService
                .topk(database, scoreTable, "top_purchased", "user_A", Direction.OUT, limit = 3)
                .test()
                .assertNext { result ->
                    result.edges.size shouldBe 3
                    result.edges[0].target shouldBe "item_A"
                    result.edges[1].target shouldBe "item_B"
                    result.edges[2].target shouldBe "item_C"
                }.verifyComplete()
        }
    })
