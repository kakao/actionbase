package com.kakao.actionbase.server.api.graph.v3.metadata

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
