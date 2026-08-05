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

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MetadataAggQueryControllerE2ETest : E2ETestBase() {
    private val db = "commerce"
    private val table = "orders_table"
    private val alias = "orders"
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
                    "source": {"type": "string", "comment": "database|table|topk|entity|dimensionValues"},
                    "target": {"type": "string", "comment": "topkDimensionValue"},
                    "properties": [
                      {"name": "metric", "type": "long", "comment": "aggregated metric", "nullable": false},
                      {"name": "additionalProperties", "type": "string", "comment": "carried properties as JSON", "nullable": true}
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
                    "properties": [
                      {"name": "productGroupId", "type": "string", "comment": "product group", "nullable": false},
                      {"name": "purchasedAt", "type": "long", "comment": "purchase time ms", "nullable": false}
                    ],
                    "direction": "OUT",
                    "indexes": [],
                    "groups": [{
                      "group": "purchased_count",
                      "type": "COUNT",
                      "fields": [
                        {"name": "_target"},
                        {"name": "purchasedAt", "bucket": {"type": "date", "name": "day", "unit": "MILLISECOND", "timezone": "UTC", "format": "yyyy-MM-dd"}}
                      ],
                      "directionType": "OUT",
                      "aggregations": {
                        "topk": [{
                          "topk": "$topkName",
                          "entity": "source",
                          "ranges": "_target:eq:{_target};day:bt:2023-11-14,2024-11-13",
                          "dimension": "target",
                          "rank": "$rankFqn",
                          "additionalProperties": ["productGroupId"]
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

        // An alias pointing at the source table; queries go through it.
        client
            .post()
            .uri("/graph/v3/databases/$db/aliases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"alias": "$alias", "table": "$table", "comment": "query alias"}""")
            .exchange()
            .expectStatus()
            .isOk
    }

    /**
     * The group also declares a bucketed `purchasedAt` (day) field: `ranges` constrains the metric to
     * a day window, and the bucket field is excluded from the rank key, so the key stays
     * `db|table|topk|entity` and the alias query still finds the rows.
     *
     * 1. Record three edges for `user1`, each carrying `productGroupId = grocery` and a `purchasedAt`
     *    inside the window: two to `item1`, one to `item2`.
     *
     *    commerce.orders_table (source)
     *    | source | target | productGroupId | purchasedAt   |
     *    |--------|--------|----------------|---------------|
     *    | user1  | item1  | grocery        | 1710000000000 |
     *    | user1  | item1  | grocery        | 1710000000000 |
     *    | user1  | item2  | grocery        | 1710000000000 |
     *
     * 2. Aggregate both targets by the real table name (as a streaming writer would), materializing
     *    two rank rows (metric = edge count) with the carried property:
     *
     *    commerce.orders_table__topk (rank)
     *    |            row key (source)             | target | metric | productGroupId |
     *    |-----------------------------------------|--------|--------|----------------|
     *    | commerce|orders_table|top_purchased|user1 | item1  |      2 | grocery      |
     *    | commerce|orders_table|top_purchased|user1 | item2  |      1 | grocery      |
     *
     * 3. Query the top-K for `user1` *through the alias* -> rows come back ordered by metric
     *    (`item1` then `item2`), each carrying `productGroupId`.
     */
    @Test
    fun `querying a topk through an alias returns the ranked targets with their carried properties`() {
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$table/multi-edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "id": 1, "source": "user1", "target": "item1", "properties": {"productGroupId": "grocery", "purchasedAt": 1710000000000}}},
                    {"type": "INSERT", "edge": {"version": 1, "id": 2, "source": "user1", "target": "item1", "properties": {"productGroupId": "grocery", "purchasedAt": 1710000000000}}},
                    {"type": "INSERT", "edge": {"version": 1, "id": 3, "source": "user1", "target": "item2", "properties": {"productGroupId": "grocery", "purchasedAt": 1710000000000}}}
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
                     "edge": {"version": 1, "source": "user1", "target": "item1", "properties": {"productGroupId": "grocery", "purchasedAt": 1710000000000}, "context": {}}},
                    {"database": "$db", "table": "$table",
                     "edge": {"version": 1, "source": "user1", "target": "item2", "properties": {"productGroupId": "grocery", "purchasedAt": 1710000000000}, "context": {}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody<AggregationsItemResponse>()
            .returnResult()

        val ranking: (AggregationsTopkResponse) -> Unit = { response ->
            assertEquals(2, response.count)
            assertEquals(listOf("item1", "item2"), response.topks.map { it.value })
            assertEquals(listOf(2L, 1L), response.topks.map { it.metric })
            assertEquals(listOf("grocery", "grocery"), response.topks.map { it.properties["productGroupId"] })
        }

        client
            .post()
            .uri("/aggregations/v1/databases/$db/tables/$alias/topks/$topkName")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"entity": "user1"}""")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody<AggregationsTopkResponse>()
            .returnResult()
            .responseBody!!
            .let(ranking)

        client
            .get()
            .uri("/aggregations/v1/databases/$db/tables/$alias/topks/$topkName?entity=user1")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody<AggregationsTopkResponse>()
            .returnResult()
            .responseBody!!
            .let(ranking)
    }
}
