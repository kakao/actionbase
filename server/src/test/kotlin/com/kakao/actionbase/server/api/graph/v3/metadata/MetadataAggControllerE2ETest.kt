package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.edge.payload.AggregationsExpireResponse
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
                      {"name": "start", "type": "string", "comment": "start", "nullable": false},
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
    fun `POST aggregations expire sweeps rows whose expiresAt is at or below the requested value`() {
        val source = 1000L

        client
            .post()
            .uri("/graph/v3/databases/$TOPK_DATABASE/tables/$TOPK_EXPIRE_TABLE/edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "source": $source, "target": "commerce.purchases|top_purchased|user1|item1|100",
                      "properties": {"expiresAt": 100, "table": "commerce.purchases", "topk": "top_purchased", "start": "user1", "direction": "OUT", "ranges": "_target:eq:item1", "processed": false}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": $source, "target": "commerce.purchases|top_purchased|user1|item2|200",
                      "properties": {"expiresAt": 200, "table": "commerce.purchases", "topk": "top_purchased", "start": "user1", "direction": "OUT", "ranges": "_target:eq:item2", "processed": false}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": $source, "target": "commerce.purchases|top_purchased|user1|item3|500",
                      "properties": {"expiresAt": 500, "table": "commerce.purchases", "topk": "top_purchased", "start": "user1", "direction": "OUT", "ranges": "_target:eq:item3", "processed": false}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        client
            .get()
            .uri(
                "/graph/v3/databases/$TOPK_DATABASE/tables/$TOPK_EXPIRE_TABLE/edges/scan/expires_at_asc" +
                    "?start=$source&direction=OUT",
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.count")
            .isEqualTo(3)

        val response =
            client
                .post()
                .uri("/graph/v3/aggregations/expire")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    """
                    {
                      "items": [
                        {"database": "$TOPK_DATABASE", "table": "$TOPK_EXPIRE_TABLE",
                         "edge": {"version": 1, "source": $source, "target": "-", "properties": {"expiresAt": 200}, "context": {}}}
                      ]
                    }
                    """.trimIndent(),
                ).exchange()
                .expectStatus()
                .isOk
                .expectBody<AggregationsExpireResponse>()
                .returnResult()
                .responseBody!!

        assertEquals(1, response.items.size)
        assertEquals(TOPK_DATABASE, response.items[0].database)
        assertEquals(TOPK_EXPIRE_TABLE, response.items[0].table)
        assertEquals(source.toString(), response.items[0].source)
        assertEquals("SUCCESS", response.items[0].status)

        client
            .get()
            .uri(
                "/graph/v3/databases/$TOPK_DATABASE/tables/$TOPK_EXPIRE_TABLE/edges/scan/expires_at_asc" +
                    "?start=$source&direction=OUT",
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.count")
            .isEqualTo(1)
            .jsonPath("$.edges[0].properties.expiresAt")
            .isEqualTo(500)
    }

    @Test
    fun `POST aggregations expire dedupes items with the same source and takes the maximum expiresAt`() {
        val source = 2000L

        client
            .post()
            .uri("/graph/v3/databases/$TOPK_DATABASE/tables/$TOPK_EXPIRE_TABLE/edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "source": $source, "target": "commerce.purchases|top_purchased|user9|item1|100",
                      "properties": {"expiresAt": 100, "table": "commerce.purchases", "topk": "top_purchased", "start": "user9", "direction": "OUT", "ranges": "_target:eq:item1", "processed": false}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": $source, "target": "commerce.purchases|top_purchased|user9|item2|300",
                      "properties": {"expiresAt": 300, "table": "commerce.purchases", "topk": "top_purchased", "start": "user9", "direction": "OUT", "ranges": "_target:eq:item2", "processed": false}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        client
            .get()
            .uri(
                "/graph/v3/databases/$TOPK_DATABASE/tables/$TOPK_EXPIRE_TABLE/edges/scan/expires_at_asc" +
                    "?start=$source&direction=OUT",
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.count")
            .isEqualTo(2)

        val response =
            client
                .post()
                .uri("/graph/v3/aggregations/expire")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    """
                    {
                      "items": [
                        {"database": "$TOPK_DATABASE", "table": "$TOPK_EXPIRE_TABLE",
                         "edge": {"version": 1, "source": $source, "target": "-", "properties": {"expiresAt": 150}, "context": {}}},
                        {"database": "$TOPK_DATABASE", "table": "$TOPK_EXPIRE_TABLE",
                         "edge": {"version": 1, "source": $source, "target": "-", "properties": {"expiresAt": 400}, "context": {}}}
                      ]
                    }
                    """.trimIndent(),
                ).exchange()
                .expectStatus()
                .isOk
                .expectBody<AggregationsExpireResponse>()
                .returnResult()
                .responseBody!!

        assertEquals(1, response.items.size)
        assertEquals(source.toString(), response.items[0].source)
        assertEquals("SUCCESS", response.items[0].status)

        client
            .get()
            .uri(
                "/graph/v3/databases/$TOPK_DATABASE/tables/$TOPK_EXPIRE_TABLE/edges/scan/expires_at_asc" +
                    "?start=$source&direction=OUT",
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.count")
            .isEqualTo(0)
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
