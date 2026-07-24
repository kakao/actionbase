package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.edge.payload.AggregationsItemResponse
import com.kakao.actionbase.core.edge.payload.DataFrameEdgePayload
import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody

/**
 * E2E for `GET /graph/v3/databases/{db}/tables/{table}/aggregations/topk/{topk}`, which reads back a
 * materialized top-K ranking.
 *
 * The endpoint resolves the rank table from the top-K config and scans its `metric_desc` index from
 * the `topk | entity | dimensionValues...` prefix, so results come back ordered by metric descending:
 *
 * | row key (source)     | target | metric |
 * |----------------------|--------|--------|
 * | top_purchased | user1 | item1  |      2 |
 * | top_purchased | user1 | item2  |      1 |
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MetadataAggQueryControllerE2ETest : E2ETestBase() {
    private val db = "commerce_query"
    private val table = "orders"
    private val rank = "${table}__topk"
    private val rankFqn = "$db.$rank"
    private val topkName = "top_purchased"

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

        // Rank table: a plain edge table read as a sorted index via `metric_desc`.
        client
            .post()
            .uri("/graph/v3/databases/$db/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "$rank",
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
                  "storage": "datastore://test_namespace/$rank",
                  "mode": "SYNC",
                  "comment": "topk rank rows"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        // Source edge table: a COUNT group declaring a per-entity top-K over `target`.
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
    }

    /**
     * 1. Record three edges for `user1`: two to `item1`, one to `item2`.
     *
     *    commerce_query.orders (source)
     *    | source | target |
     *    |--------|--------|
     *    | user1  | item1  |
     *    | user1  | item1  |
     *    | user1  | item2  |
     *
     * 2. Aggregate both targets, materializing two rank rows (metric = edge count):
     *
     *    commerce_query.orders__topk (rank)
     *    |     row key (source)   | target | metric |
     *    |------------------------|--------|--------|
     *    | top_purchased  | user1 | item1  |      2 |
     *    | top_purchased  | user1 | item2  |      1 |
     *
     * 3. Query the top-K for `user1` -> rows come back ordered by metric: `item1` then `item2`.
     */
    @Test
    fun `querying a topk returns the ranked targets ordered by metric`() {
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$table/multi-edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "id": 1, "source": "user1", "target": "item1", "properties": {}}},
                    {"type": "INSERT", "edge": {"version": 1, "id": 2, "source": "user1", "target": "item1", "properties": {}}},
                    {"type": "INSERT", "edge": {"version": 1, "id": 3, "source": "user1", "target": "item2", "properties": {}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/graph/v3/aggregations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "type": "TOPK",
                  "items": [
                    {"database": "$db", "table": "$table",
                     "edge": {"version": 1, "source": "user1", "target": "item1", "properties": {}, "context": {}}},
                    {"database": "$db", "table": "$table",
                     "edge": {"version": 1, "source": "user1", "target": "item2", "properties": {}, "context": {}}}
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
            .uri("/graph/v3/databases/$db/tables/$table/aggregations/topk/$topkName?entity=user1")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody<DataFrameEdgePayload>()
            .returnResult()
            .responseBody!!
            .let { payload ->
                assertEquals(2, payload.count)
                assertEquals(
                    listOf("item1", "item2"),
                    payload.edges.map { it.target.toString() },
                )
            }
    }
}
