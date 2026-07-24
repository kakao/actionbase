package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.edge.payload.AggregationsItemResponse
import com.kakao.actionbase.core.edge.payload.AggregationsSweepResponse
import com.kakao.actionbase.core.edge.payload.DataFrameEdgePayload
import com.kakao.actionbase.core.metadata.common.AggregationConstants
import com.kakao.actionbase.core.metadata.common.AggregationConstants.Topk.DATABASE
import com.kakao.actionbase.core.metadata.common.AggregationConstants.Topk.REFRESH_TABLE
import com.kakao.actionbase.core.metadata.common.AggregationType
import com.kakao.actionbase.core.metadata.payload.AggregationsListResponse
import com.kakao.actionbase.engine.queue.PartitionHasher
import com.kakao.actionbase.engine.queue.PollResponse
import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody

/**
 * E2E for `POST /aggregations/v1/aggregate`, which materializes per-entity top-K rankings from an edge
 * event into two tables.
 *
 * Rank table `<db>.<table>__topk` — a plain edge table read as a sorted index via `metric_desc`:
 *
 * |          row key (source)           |       target       | value  |
 * |-------------------------------------|--------------------|--------|
 * | topk | entity | dimensionValues...  | topkDimensionValue | metric |
 *
 * Refresh queue `topk`/`refresh` — a queue/v1 table the aggregate flow enqueues onto when a top-K
 * declares `refreshAfterMillis`:
 *
 * |   partition   |                key                  |      seq      |       value        |
 * |---------------|-------------------------------------|---------------|--------------------|
 * | hash(key) % N | db|table|topk|entity|dimVal|dims... | refreshAt(ms) | {type, item:{ … }} |
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MetadataAggControllerE2ETest : E2ETestBase() {
    private val db = "commerce"
    private val table = "purchases"
    private val rank = "${table}__topk"
    private val rankFqn = "$db.$rank"
    private val refreshPartitions = 4
    private val refreshAfter = 3_600_000L

    @BeforeAll
    fun setup() {
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$db", "comment": "test"}""")
            .exchange()
            .expectStatus()
            .isOk

        createRankTable(rank)

        // A source edge table whose COUNT group declares a top-K ranking with refresh tracking on.
        client
            .post()
            .uri("/graph/v3/databases/$db/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "$table",
                  "schema": {
                    "type": "MULTI_EDGE",
                    "id": {"type": "long", "comment": "purchase id"},
                    "source": {"type": "string", "comment": "user"},
                    "target": {"type": "string", "comment": "item"},
                    "properties": [],
                    "direction": "BOTH",
                    "indexes": [],
                    "groups": [{
                      "group": "purchased_count",
                      "type": "COUNT",
                      "fields": [{"name": "_target"}],
                      "directionType": "OUT",
                      "aggregations": {
                        "topk": [{
                          "topk": "top_purchased",
                          "entity": "source",
                          "ranges": "_target:eq:{_target}",
                          "dimension": "target",
                          "refreshAfterMillis": $refreshAfter,
                          "rank": "$rankFqn"
                        }]
                      }
                    }],
                    "caches": []
                  },
                  "storage": "datastore://test_namespace/$table",
                  "mode": "SYNC",
                  "comment": "user-to-item purchase edges"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        // The refresh tracker lives under the reserved `topk` database as a queue/v1 table: the
        // aggregate flow enqueues due-refresh messages onto it and a worker polls them back.
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$DATABASE", "comment": "test"}""")
            .exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/queue/v1/namespaces/$DATABASE/queues")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """{"queue": "$REFRESH_TABLE", "storage": "datastore://test_namespace/$REFRESH_TABLE", "partitions": $refreshPartitions}""",
            ).exchange()
            .expectStatus()
            .isOk
    }

    /**
     * Ranking with a group that mixes an endpoint field (`_target`), a plain dimension (`category`),
     * and a bucketed field (`purchasedAt`).
     *
     * 1. Create the rank table and a bucketed source table under `commerce`.
     * 2. Record one edge:
     *
     *    commerce.orders (source)
     *    | source | target | category | purchasedAt   |
     *    |--------|--------|----------|---------------|
     *    | user1  | item1  | fruit    | 1710000000000 |
     *
     * 3. Run the aggregation. The rank row key joins the entity with the non-bucket dimension only
     *    (`category`); the bucketed field is consumed by `ranges`, never by the key:
     *
     *    commerce.orders__topk (rank)
     *    |             row key (source)     | target | metric |
     *    |----------------------------------|--------|--------|
     *    | top_purchased_1y | user1 | fruit | item1  |      1 |
     *
     * 4. Scan `metric_desc` from the entity prefix -> the top row's target is `item1`.
     */
    @Test
    fun `running a topk aggregation writes a rank row keyed by entity and non-bucket dimensions`() {
        val ordersTable = "orders"
        val ordersRank = "orders__topk"
        val topkName = "top_purchased_1y"

        createRankTable(ordersRank)

        client
            .post()
            .uri("/graph/v3/databases/$db/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "$ordersTable",
                  "schema": {
                    "type": "MULTI_EDGE",
                    "id": {"type": "long", "comment": "order id"},
                    "source": {"type": "string", "comment": "user"},
                    "target": {"type": "string", "comment": "item"},
                    "properties": [
                      {"name": "category", "type": "string", "comment": "category", "nullable": false},
                      {"name": "purchasedAt", "type": "long", "comment": "purchase time ms", "nullable": false}
                    ],
                    "direction": "OUT",
                    "indexes": [],
                    "groups": [{
                      "group": "purchased_bucketed",
                      "type": "COUNT",
                      "fields": [
                        {"name": "_target"},
                        {"name": "category"},
                        {"name": "purchasedAt", "bucket": {"type": "date", "name": "day", "unit": "MILLISECOND", "timezone": "UTC", "format": "yyyy-MM-dd"}}
                      ],
                      "directionType": "OUT",
                      "aggregations": {
                        "topk": [{
                          "topk": "$topkName",
                          "entity": "source",
                          "ranges": "_target:eq:{_target};category:eq:{category};day:bt:1700000000000,1731536000000",
                          "dimension": "target",
                          "rank": "$db.$ordersRank"
                        }]
                      }
                    }],
                    "caches": []
                  },
                  "storage": "datastore://test_namespace/$ordersTable",
                  "mode": "SYNC",
                  "comment": "orders with a category and a day bucket"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$ordersTable/multi-edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "id": 1, "source": "user1", "target": "item1",
                      "properties": {"category": "fruit", "purchasedAt": 1710000000000}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/aggregations/v1/aggregate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "type": "TOPK",
                  "items": [
                    {"database": "$db", "table": "$ordersTable",
                     "edge": {"version": 1, "source": "user1", "target": "item1",
                              "properties": {"category": "fruit", "purchasedAt": 1710000000000}, "context": {}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody<AggregationsItemResponse>()
            .returnResult()

        client
            .get()
            .uri(
                "/graph/v3/databases/$db/tables/$ordersRank/edges/scan/metric_desc" +
                    "?start=$topkName|user1|fruit&direction=OUT",
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody<DataFrameEdgePayload>()
            .returnResult()
            .responseBody!!
            .let { payload ->
                assertEquals(1, payload.count)
                assertEquals(
                    "item1",
                    payload.edges
                        .single()
                        .target
                        .toString(),
                )
            }
    }

    /**
     * Ranking with a group that carries no endpoint field; the ranked dimension (`category`) is a
     * property, so the rank target is resolved from edge properties.
     *
     * 1. Create the rank table and a segment-only source table under `commerce`.
     * 2. Record one edge:
     *
     *    commerce.orders_segment (source)
     *    | source | target | category | purchasedAt   |
     *    |--------|--------|----------|---------------|
     *    | user1  | item1  | fruit    | 1710000000000 |
     *
     * 3. Run the aggregation. With no non-bucket dimension left over, the key is just the entity and
     *    the property value (`fruit`) becomes the rank target:
     *
     *    commerce.orders_segment__topk (rank)
     *    |                row key            | target | metric |
     *    |-----------------------------------|--------|--------|
     *    | top_purchased_by_category | user1 | fruit  |      1 |
     *
     * 4. Scan `metric_desc` from the entity prefix -> the top row's target is `fruit`.
     */
    @Test
    fun `running a topk aggregation resolves the rank target from a property-backed dimension`() {
        val segmentTable = "orders_segment"
        val segmentRank = "orders_segment__topk"
        val topkName = "top_purchased_by_category"

        createRankTable(segmentRank)

        client
            .post()
            .uri("/graph/v3/databases/$db/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "$segmentTable",
                  "schema": {
                    "type": "MULTI_EDGE",
                    "id": {"type": "long", "comment": "order id"},
                    "source": {"type": "string", "comment": "user"},
                    "target": {"type": "string", "comment": "item"},
                    "properties": [
                      {"name": "category", "type": "string", "comment": "category", "nullable": false},
                      {"name": "purchasedAt", "type": "long", "comment": "purchase time ms", "nullable": false}
                    ],
                    "direction": "OUT",
                    "indexes": [],
                    "groups": [{
                      "group": "purchased_by_category",
                      "type": "COUNT",
                      "fields": [
                        {"name": "category"},
                        {"name": "purchasedAt", "bucket": {"type": "date", "name": "day", "unit": "MILLISECOND", "timezone": "UTC", "format": "yyyy-MM-dd"}}
                      ],
                      "directionType": "OUT",
                      "aggregations": {
                        "topk": [{
                          "topk": "$topkName",
                          "entity": "source",
                          "ranges": "category:eq:{category};day:bt:1700000000000,1731536000000",
                          "dimension": "category",
                          "rank": "$db.$segmentRank"
                        }]
                      }
                    }],
                    "caches": []
                  },
                  "storage": "datastore://test_namespace/$segmentTable",
                  "mode": "SYNC",
                  "comment": "orders ranked by a category segment only"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$segmentTable/multi-edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "id": 1, "source": "user1", "target": "item1",
                      "properties": {"category": "fruit", "purchasedAt": 1710000000000}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/aggregations/v1/aggregate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "type": "TOPK",
                  "items": [
                    {"database": "$db", "table": "$segmentTable",
                     "edge": {"version": 1, "source": "user1", "target": "item1",
                              "properties": {"category": "fruit", "purchasedAt": 1710000000000}, "context": {}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody<AggregationsItemResponse>()
            .returnResult()

        client
            .get()
            .uri(
                "/graph/v3/databases/$db/tables/$segmentRank/edges/scan/metric_desc" +
                    "?start=$topkName|user1&direction=OUT",
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody<DataFrameEdgePayload>()
            .returnResult()
            .responseBody!!
            .let { payload ->
                assertEquals(1, payload.count)
                assertEquals(
                    "fruit",
                    payload.edges
                        .single()
                        .target
                        .toString(),
                )
            }
    }

    /**
     * The listing endpoint surfaces every table that declares a top-K aggregation, keyed by
     * (database, table).
     */
    @Test
    fun `listing aggregations returns the tables that declare a topk aggregation`() {
        val response =
            client
                .get()
                .uri("/aggregations/v1/metadata")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody<AggregationsListResponse>()
                .returnResult()
                .responseBody!!

        val byLocation = response.items.associateBy { it.database to it.table }

        val source = byLocation[db to table]
        assertNotNull(source)
        assertEquals(AggregationType.TOPK, source?.type)
        assertEquals(db, source?.database)
        assertEquals(table, source?.table)
    }

    /**
     * `PUT /aggregations/v1/sweep` recomputes a ranking on demand from an already-resolved
     * refresh target and re-writes its rank row.
     *
     * 1. Create a rank table and a source table under `commerce_sweep`.
     * 2. Record one edge user1 -> item1.
     * 3. Sweep the target. The response reports the recomputed ranking:
     *
     *    commerce_sweep.orders__topk (rank)
     *    |        row key        | target | metric |
     *    |-----------------------|--------|--------|
     *    | top_purchased | user1 | item1  |      1 |
     *
     * 4. Scan `metric_desc` from the entity prefix -> the top row's target is `item1`.
     */
    @Test
    fun `sweep recomputes the ranking and re-writes the rank row`() {
        val sweepDb = "commerce_sweep"
        val sweepTable = "orders"
        val sweepRank = "orders__topk"
        val sweepRankFqn = "$sweepDb.$sweepRank"
        val topkName = "top_purchased"

        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$sweepDb", "comment": "test"}""")
            .exchange()

        client
            .post()
            .uri("/graph/v3/databases/$sweepDb/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "$sweepRank",
                  "schema": {
                    "type": "EDGE",
                    "source": {"type": "string", "comment": "topk|entity|dimensionValues"},
                    "target": {"type": "string", "comment": "topkDimensionValue"},
                    "properties": [
                      {"name": "metric", "type": "long", "comment": "metric", "nullable": false}
                    ],
                    "direction": "OUT",
                    "indexes": [
                      {"index": "metric_desc", "fields": [{"field": "metric", "order": "DESC"}]}
                    ],
                    "groups": [],
                    "caches": []
                  },
                  "storage": "datastore://test_namespace/$sweepRank",
                  "mode": "SYNC",
                  "comment": "topk rank rows"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/graph/v3/databases/$sweepDb/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "$sweepTable",
                  "schema": {
                    "type": "MULTI_EDGE",
                    "id": {"type": "long", "comment": "order id"},
                    "source": {"type": "string", "comment": "user"},
                    "target": {"type": "string", "comment": "item"},
                    "properties": [],
                    "direction": "OUT",
                    "indexes": [],
                    "groups": [{
                      "group": "purchased_count",
                      "type": "COUNT",
                      "fields": [{"name": "_target"}],
                      "directionType": "OUT",
                      "aggregations": {
                        "topk": [{
                          "topk": "$topkName",
                          "entity": "source",
                          "ranges": "_target:eq:{_target}",
                          "dimension": "target",
                          "rank": "$sweepRankFqn"
                        }]
                      }
                    }],
                    "caches": []
                  },
                  "storage": "datastore://test_namespace/$sweepTable",
                  "mode": "SYNC",
                  "comment": "orders"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/graph/v3/databases/$sweepDb/tables/$sweepTable/multi-edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "id": 1, "source": "user1", "target": "item1", "properties": {}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        val response =
            client
                .put()
                .uri("/aggregations/v1/sweep")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    """
                    {
                      "items": [
                        {
                          "type": "TOPK",
                          "item": {"database": "$sweepDb", "table": "$sweepTable", "topk": "$topkName",
                                   "source": "user1", "target": "item1", "direction": "OUT", "ranges": "_target:eq:item1",
                                   "entity": "user1", "topkDimensionValue": "item1", "dimensionValues": "", "refreshAt": 123}
                        }
                      ]
                    }
                    """.trimIndent(),
                ).exchange()
                .expectStatus()
                .isOk
                .expectBody<AggregationsSweepResponse>()
                .returnResult()
                .responseBody!!

        assertEquals(1, response.items.size)
        assertEquals(sweepDb, response.items[0].database)
        assertEquals(sweepTable, response.items[0].table)
        assertEquals(topkName, response.items[0].topk)
        assertEquals("user1", response.items[0].entity)
        assertEquals("SUCCESS", response.items[0].status)

        client
            .get()
            .uri(
                "/graph/v3/databases/$sweepDb/tables/$sweepRank/edges/scan/metric_desc" +
                    "?start=$topkName|user1&direction=OUT",
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody<DataFrameEdgePayload>()
            .returnResult()
            .responseBody!!
            .let { payload ->
                assertEquals(1, payload.count)
                assertEquals(
                    "item1",
                    payload.edges
                        .single()
                        .target
                        .toString(),
                )
            }
    }

    /**
     * The `commerce.purchases` top-K enables `refreshAfterMillis`, so an aggregation also enqueues a
     * refresh message.
     *
     * 1. Record one edge user1 -> item1 on `commerce.purchases`.
     * 2. Run the aggregation. Besides the rank row it enqueues one message onto the refresh queue,
     *    keyed by the same composite the rank row uses and ordered by `seq` = refreshAt:
     *
     *    topk/refresh (queue; poll partition = hash(key) % 4)
     *    |      seq (refreshAt)      | value.type |                 value.item                  |
     *    |--------------------------|------------|---------------------------------------------|
     *    | now + refreshAfterMillis | TOPK       | {database, table, topk, source, target, … } |
     *
     * 3. Poll that partition -> exactly one message carrying the full recompute payload.
     */
    @Test
    fun `running a topk aggregation enqueues a refresh message when refreshAfterMillis is set`() {
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$table/multi-edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "id": 1, "source": "user1", "target": "item1", "properties": {}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        val before = System.currentTimeMillis()
        client
            .post()
            .uri("/aggregations/v1/aggregate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "type": "TOPK",
                  "items": [
                    {"database": "$db", "table": "$table",
                     "edge": {"version": 1, "source": "user1", "target": "item1", "properties": {}, "context": {}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
        val after = System.currentTimeMillis()

        val key =
            AggregationConstants.Topk.refreshKey(
                database = db,
                table = table,
                topk = "top_purchased",
                entity = "user1",
                topkDimensionValue = "item1",
                dimensionValues = emptyList(),
            )
        val partition = PartitionHasher.partition(key, refreshPartitions)

        val page =
            client
                .get()
                .uri("/queue/v1/namespaces/$DATABASE/queues/$REFRESH_TABLE/partitions/$partition/poll?limit=10")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody<PollResponse>()
                .returnResult()
                .responseBody!!

        val message = page.messages.single()
        assertTrue(message.seq >= before + refreshAfter)
        assertTrue(message.seq <= after + refreshAfter)

        val value = message.value as Map<*, *>
        assertEquals("TOPK", value["type"])
        val refreshItem = value["item"] as Map<*, *>
        assertEquals(db, refreshItem["database"])
        assertEquals(table, refreshItem["table"])
        assertEquals("top_purchased", refreshItem["topk"])
        assertEquals("user1", refreshItem["source"])
        assertEquals("item1", refreshItem["target"])
        assertEquals("OUT", refreshItem["direction"])
        assertEquals("user1", refreshItem["entity"])
        assertEquals("item1", refreshItem["topkDimensionValue"])
        assertEquals("", refreshItem["dimensionValues"])
        assertEquals(message.seq, (refreshItem["refreshAt"] as Number).toLong())
    }

    /** A rank table is a plain edge table read as a sorted index: `source` is the composite key,
     *  `target` is the ranked dimension value, and the `metric_desc` index orders by `metric`. */
    private fun createRankTable(rankTable: String) {
        client
            .post()
            .uri("/graph/v3/databases/$db/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "$rankTable",
                  "schema": {
                    "type": "EDGE",
                    "source": {"type": "string", "comment": "topk|entity|dimensionValues"},
                    "target": {"type": "string", "comment": "topkDimensionValue"},
                    "properties": [
                      {"name": "metric", "type": "long", "comment": "aggregated metric", "nullable": false}
                    ],
                    "direction": "OUT",
                    "indexes": [
                      {"index": "metric_desc", "fields": [{"field": "metric", "order": "DESC"}]}
                    ],
                    "groups": [],
                    "caches": []
                  },
                  "storage": "datastore://test_namespace/$rankTable",
                  "mode": "SYNC",
                  "comment": "topk rank rows"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }
}
