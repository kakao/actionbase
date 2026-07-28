package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.edge.payload.AggregationsItemResponse
import com.kakao.actionbase.core.edge.payload.AggregationsTopkResponse
import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody

/**
 * E2E for `GET /aggregations/v1/databases/{db}/tables/{table}/topks/{topk}`, which reads back a
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
            .uri("/aggregations/v1/aggregate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
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
            .uri("/aggregations/v1/databases/$db/tables/$table/topks/$topkName?entity=user1")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody<AggregationsTopkResponse>()
            .returnResult()
            .responseBody!!
            .let { response ->
                assertEquals(2, response.count)
                assertEquals(listOf("item1", "item2"), response.topks.map { it.value })
                assertEquals(listOf(2L, 1L), response.topks.map { it.metric })
            }
    }

    /**
     * A top-K may declare extra `properties` (edge fields, incl. `source`/`target`) to carry onto each
     * rank row alongside `metric`, so the query can return them.
     *
     * 1. Record one edge carrying `productGroupId = grocery`.
     * 2. Aggregate. The rank row stores the resolved property next to the metric:
     *
     *    commerce_query.orders_props__topk (rank)
     *    |       row key (source)      | target | metric | productGroupId |
     *    |-----------------------------|--------|--------|----------------|
     *    | top_purchased_props | user1 | item1  |      1 | grocery        |
     *
     * 3. Query the top-K -> the returned row carries `productGroupId`.
     */
    @Test
    fun `querying a topk returns the declared extra properties on each rank row`() {
        val propsTable = "orders_props"
        val propsRank = "orders_props__topk"
        val propsTopk = "top_purchased_props"

        client
            .post()
            .uri("/graph/v3/databases/$db/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "$propsRank",
                  "schema": {
                    "type": "EDGE",
                    "source": {"type": "string", "comment": "topk|entity|dimensionValues"},
                    "target": {"type": "string", "comment": "topkDimensionValue"},
                    "properties": [
                      {"name": "metric", "type": "long", "comment": "aggregated metric", "nullable": false},
                      {"name": "productGroupId", "type": "string", "comment": "carried property", "nullable": true}
                    ],
                    "direction": "OUT",
                    "indexes": [
                      {"index": "metric_desc", "fields": [{"field": "metric", "order": "DESC"}]}
                    ],
                    "groups": [],
                    "caches": []
                  },
                  "storage": "datastore://test_namespace/$propsRank",
                  "mode": "SYNC",
                  "comment": "topk rank rows with a carried property"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/graph/v3/databases/$db/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "$propsTable",
                  "schema": {
                    "type": "MULTI_EDGE",
                    "id": {"type": "long", "comment": "order id"},
                    "source": {"type": "string", "comment": "user"},
                    "target": {"type": "string", "comment": "item"},
                    "properties": [
                      {"name": "productGroupId", "type": "string", "comment": "product group", "nullable": false}
                    ],
                    "direction": "OUT",
                    "indexes": [],
                    "groups": [{
                      "group": "purchased_count",
                      "type": "COUNT",
                      "fields": [{"name": "_target"}],
                      "directionType": "OUT",
                      "aggregations": {
                        "topk": [{
                          "topk": "$propsTopk",
                          "entity": "source",
                          "ranges": "_target:eq:{_target}",
                          "dimension": "target",
                          "rank": "$db.$propsRank",
                          "additionalProperties": ["productGroupId"]
                        }]
                      }
                    }],
                    "caches": []
                  },
                  "storage": "datastore://test_namespace/$propsTable",
                  "mode": "SYNC",
                  "comment": "purchases carrying a product group"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$propsTable/multi-edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "id": 1, "source": "user1", "target": "item1",
                      "properties": {"productGroupId": "grocery"}}}
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
                  "items": [
                    {"database": "$db", "table": "$propsTable",
                     "edge": {"version": 1, "source": "user1", "target": "item1",
                              "properties": {"productGroupId": "grocery"}, "context": {}}}
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
            .uri("/aggregations/v1/databases/$db/tables/$propsTable/topks/$propsTopk?entity=user1")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody<AggregationsTopkResponse>()
            .returnResult()
            .responseBody!!
            .let { response ->
                assertEquals(1, response.count)
                val rank = response.topks.single()
                assertEquals("item1", rank.value)
                assertEquals(1L, rank.metric)
                assertEquals("grocery", rank.properties["productGroupId"])
            }
    }
}
