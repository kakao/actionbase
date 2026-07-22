package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.edge.payload.AggregationsItemResponse
import com.kakao.actionbase.core.edge.payload.DataFrameEdgePayload
import com.kakao.actionbase.core.metadata.common.AggregationConstants.TOPK_DATABASE
import com.kakao.actionbase.core.metadata.common.AggregationConstants.TOPK_REFRESH_TABLE
import com.kakao.actionbase.core.metadata.common.AggregationType
import com.kakao.actionbase.core.metadata.payload.AggregationsListResponse
import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MetadataAggControllerE2ETest : E2ETestBase() {
    private val db = "commerce"
    private val table = "purchases"
    private val rankFqn = "$db.${table}__topk"

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
                  "table": "$table",
                  "schema": {
                    "type": "MULTI_EDGE",
                    "id": {"type": "long", "comment": "purchase id"},
                    "source": {"type": "long", "comment": "user"},
                    "target": {"type": "long", "comment": "item"},
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
                          "rank": "$rankFqn"
                        }]
                      }
                    }],
                    "caches": []
                  },
                  "storage": "datastore://test_namespace/$table",
                  "mode": "SYNC",
                  "comment": "purchases"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$TOPK_DATABASE", "comment": "test"}""")
            .exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/graph/v3/databases/$TOPK_DATABASE/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "$TOPK_REFRESH_TABLE",
                  "schema": {
                    "type": "EDGE",
                    "source": {"type": "long", "comment": "partition = hash(database,table,topk,entity,topkDimensionValue,dimensionValues) % 2310"},
                    "target": {"type": "string", "comment": "refresh_at"},
                    "properties": [
                      {"name": "refreshAt", "type": "long", "comment": "next refresh time ms", "nullable": false},
                      {"name": "database", "type": "string", "comment": "source database", "nullable": false},
                      {"name": "table", "type": "string", "comment": "source table", "nullable": false},
                      {"name": "topk", "type": "string", "comment": "topk name", "nullable": false},
                      {"name": "source", "type": "string", "comment": "original edge source", "nullable": false},
                      {"name": "target", "type": "string", "comment": "original edge target", "nullable": false},
                      {"name": "direction", "type": "string", "comment": "direction", "nullable": false},
                      {"name": "ranges", "type": "string", "comment": "interpolated ranges", "nullable": false},
                      {"name": "entity", "type": "string", "comment": "entity", "nullable": false},
                      {"name": "topkDimensionValue", "type": "string", "comment": "topk dimension value", "nullable": false},
                      {"name": "dimensionValues", "type": "string", "comment": "|joined dimension values", "nullable": false}
                    ],
                    "direction": "OUT",
                    "indexes": [
                      {"index": "refresh_at_asc", "fields": [{"field": "refreshAt", "order": "ASC"}]}
                    ],
                    "groups": [],
                    "caches": []
                  },
                  "storage": "datastore://test_namespace/$TOPK_REFRESH_TABLE",
                  "mode": "SYNC",
                  "comment": "TopK refresh tracker"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }

    @Test
    fun `aggregate joins non-bucket group fields into the score row target and skips bucket fields`() {
        val bucketedDb = "commerce_bucket"
        val bucketedTable = "orders"
        val bucketedRank = "orders__topk"
        val bucketedRankFqn = "$bucketedDb.$bucketedRank"
        val topkName = "top_purchased_1y"

        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$bucketedDb", "comment": "test"}""")
            .exchange()

        client
            .post()
            .uri("/graph/v3/databases/$bucketedDb/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "$bucketedRank",
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
                  "storage": "datastore://test_namespace/$bucketedRank",
                  "mode": "SYNC",
                  "comment": "topk score"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/graph/v3/databases/$bucketedDb/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "$bucketedTable",
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
                          "rank": "$bucketedRankFqn"
                        }]
                      }
                    }],
                    "caches": []
                  },
                  "storage": "datastore://test_namespace/$bucketedTable",
                  "mode": "SYNC",
                  "comment": "bucketed"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/graph/v3/databases/$bucketedDb/tables/$bucketedTable/multi-edges")
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
            .uri("/graph/v3/aggregations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "type": "TOPK",
                  "items": [
                    {"database": "$bucketedDb", "table": "$bucketedTable",
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
                "/graph/v3/databases/$bucketedDb/tables/$bucketedRank/edges/scan/metric_desc" +
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

    @Test
    fun `aggregate builds the score row target from a properties-backed segment field when the group has no endpoint field`() {
        val segmentDb = "commerce_segment"
        val segmentTable = "orders_segment"
        val segmentRank = "orders_segment__topk"
        val segmentRankFqn = "$segmentDb.$segmentRank"
        val topkName = "top_purchased_by_category"

        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$segmentDb", "comment": "test"}""")
            .exchange()

        client
            .post()
            .uri("/graph/v3/databases/$segmentDb/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "$segmentRank",
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
                  "storage": "datastore://test_namespace/$segmentRank",
                  "mode": "SYNC",
                  "comment": "topk score"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/graph/v3/databases/$segmentDb/tables")
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
                          "rank": "$segmentRankFqn"
                        }]
                      }
                    }],
                    "caches": []
                  },
                  "storage": "datastore://test_namespace/$segmentTable",
                  "mode": "SYNC",
                  "comment": "segment-only"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/graph/v3/databases/$segmentDb/tables/$segmentTable/multi-edges")
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
            .uri("/graph/v3/aggregations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "type": "TOPK",
                  "items": [
                    {"database": "$segmentDb", "table": "$segmentTable",
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
                "/graph/v3/databases/$segmentDb/tables/$segmentRank/edges/scan/metric_desc" +
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

    @Test
    fun `GET aggregations exposes tables with topk aggregations`() {
        val response =
            client
                .get()
                .uri("/graph/v3/aggregations")
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
}
