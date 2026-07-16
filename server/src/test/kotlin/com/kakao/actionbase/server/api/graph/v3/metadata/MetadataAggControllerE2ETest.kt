package com.kakao.actionbase.server.api.graph.v3.metadata

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
    private val scoreFqn = "$db.${table}__topk"
    private val expireFqn = "topk.expire"

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
