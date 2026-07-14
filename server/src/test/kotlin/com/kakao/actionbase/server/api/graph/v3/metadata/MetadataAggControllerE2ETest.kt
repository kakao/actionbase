package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.edge.payload.AggregationsItemResponse
import com.kakao.actionbase.core.edge.payload.DataFrameEdgePayload
import com.kakao.actionbase.core.metadata.common.AggregationConstants.TOPK_DATABASE
import com.kakao.actionbase.core.metadata.common.AggregationConstants.TOPK_EXPIRE_TABLE
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
    private val scoreFqn = "$db.${table}__topk"
    private val expireFqn = "$TOPK_DATABASE.$TOPK_EXPIRE_TABLE"

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
                          "ranges": "_target:eq:{_target}",
                          "expire": false,
                          "table": {"score": "$scoreFqn", "expire": "$expireFqn"}
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
                  "table": "$TOPK_EXPIRE_TABLE",
                  "schema": {
                    "type": "EDGE",
                    "source": {"type": "long", "comment": "partition = hash(table,topk,entity,target) % 2310"},
                    "target": {"type": "string", "comment": "table|topk|entity|target|expires_at"},
                    "properties": [
                      {"name": "expiresAt", "type": "long", "comment": "expire time ms", "nullable": false},
                      {"name": "table", "type": "string", "comment": "source table", "nullable": false},
                      {"name": "topk", "type": "string", "comment": "topk name", "nullable": false},
                      {"name": "source", "type": "string", "comment": "original source", "nullable": false},
                      {"name": "target", "type": "string", "comment": "original target", "nullable": false},
                      {"name": "direction", "type": "string", "comment": "direction", "nullable": false},
                      {"name": "ranges", "type": "string", "comment": "interpolated ranges", "nullable": false},
                      {"name": "processed", "type": "boolean", "comment": "processed", "nullable": false}
                    ],
                    "direction": "OUT",
                    "indexes": [
                      {"index": "expires_at_asc", "fields": [{"field": "expiresAt", "order": "ASC"}]}
                    ],
                    "groups": [],
                    "caches": []
                  },
                  "storage": "datastore://test_namespace/$TOPK_EXPIRE_TABLE",
                  "mode": "SYNC",
                  "comment": "TopK expire tracker"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }

    @Test
    fun `aggregate joins non-bucket group fields into the score row target and skips bucket fields`() {
        val bucketedDb = "commerce-bucket"
        val bucketedTable = "orders"
        val bucketedScore = "orders__topk"
        val bucketedScoreFqn = "$bucketedDb.$bucketedScore"
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
                  "table": "$bucketedScore",
                  "schema": {
                    "type": "EDGE",
                    "source": {"type": "string", "comment": "entity|topk"},
                    "target": {"type": "string", "comment": "ranked target"},
                    "properties": [
                      {"name": "score", "type": "long", "comment": "score", "nullable": false}
                    ],
                    "direction": "OUT",
                    "indexes": [
                      {"index": "score_desc", "fields": [{"field": "score", "order": "DESC"}]}
                    ],
                    "groups": [],
                    "caches": []
                  },
                  "storage": "datastore://test_namespace/$bucketedScore",
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
                          "ranges": "_target:eq:{_target};category:eq:{category};day:bt:1700000000000,1731536000000",
                          "table": {"score": "$bucketedScoreFqn", "expire": "$expireFqn"}
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
                "/graph/v3/databases/$bucketedDb/tables/$bucketedScore/edges/scan/score_desc" +
                    "?start=user1|$topkName&direction=OUT",
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody<DataFrameEdgePayload>()
            .returnResult()
            .responseBody!!
            .let { payload ->
                assertEquals(1, payload.count)
                assertEquals(
                    "item1|fruit",
                    payload.edges
                        .single()
                        .target
                        .toString(),
                )
            }
    }

    @Test
    fun `GET aggregations exposes topk tables with the correct expire flag`() {
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

        val databaseTablePair = response.topk.associateBy { it.database to it.table }

        val source = databaseTablePair[db to table]
        assertNotNull(source)
        assertEquals(db, source?.database)
        assertEquals(table, source?.table)
        assertEquals(false, source?.expire)

        val expire = databaseTablePair[TOPK_DATABASE to TOPK_EXPIRE_TABLE]
        assertNotNull(expire)
        assertEquals(TOPK_DATABASE, expire?.database)
        assertEquals(TOPK_EXPIRE_TABLE, expire?.table)
        assertEquals(true, expire?.expire)
    }
}
