package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MultiEdgeCountE2ETest : E2ETestBase() {
    private val db = "count-test-db"
    private val table = "count-multi-edge"

    @BeforeAll
    fun setup() {
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$db", "comment": "agg count test db"}""")
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
                    "id": {"type": "string", "comment": "id"},
                    "source": {"type": "string", "comment": "src"},
                    "target": {"type": "string", "comment": "tgt"},
                    "properties": [
                      {"name": "paidAt", "type": "long", "comment": "paid at"}
                    ],
                    "direction": "BOTH",
                    "indexes": [],
                    "groups": [
                      {
                        "group": "_count",
                        "type": "COUNT",
                        "fields": [{"name": "_target"}],
                        "directionType": "OUT",
                        "ttl": 9223372036854775807
                      },
                      {
                        "group": "day",
                        "type": "COUNT",
                        "fields": [
                          {"name": "_target"},
                          {
                            "name": "paidAt",
                            "bucket": {
                              "type": "date",
                              "name": "time",
                              "unit": "MILLISECOND",
                              "timezone": "+09:00",
                              "format": "yyyyMMdd"
                            }
                          }
                        ],
                        "directionType": "OUT",
                        "ttl": 31536000000
                      }
                    ]
                  },
                  "storage": "datastore://test_namespace/agg_count_multi_edge",
                  "mode": "SYNC",
                  "comment": "multi edge for agg count test"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        // INSERT: id=e1, source=userA, target=postB, paidAt=1718380800000 (2024-06-15 KST)
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$table/multi-edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "id": "e1", "source": "userA", "target": "postB", "properties": {"paidAt": 1718380800000}}},
                    {"type": "INSERT", "edge": {"version": 2, "id": "e2", "source": "userA", "target": "postB", "properties": {"paidAt": 1718380800000}}},
                    {"type": "INSERT", "edge": {"version": 3, "id": "e3", "source": "userA", "target": "postC", "properties": {"paidAt": 1718380800000}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }

    @Test
    fun `count returns total count by (source, target)`() {
        client
            .get()
            .uri { builder ->
                builder
                    .path("/graph/v3/databases/$db/tables/$table/multi-edges/count")
                    .queryParam("group", "_count")
                    .queryParam("start", "userA")
                    .queryParam("target", "postB")
                    .queryParam("direction", "OUT")
                    .build()
            }.exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.counts[0].start")
            .isEqualTo("userA")
            .jsonPath("$.counts[0].count")
            .isEqualTo(2)
    }

    @Test
    fun `count returns count by (source, target) within time range`() {
        client
            .get()
            .uri { builder ->
                builder
                    .path("/graph/v3/databases/$db/tables/$table/multi-edges/count")
                    .queryParam("group", "day")
                    .queryParam("start", "userA")
                    .queryParam("target", "postB")
                    .queryParam("direction", "OUT")
                    .queryParam("ranges", "time:bt:20240601,20240630")
                    .build()
            }.exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.counts[0].start")
            .isEqualTo("userA")
            .jsonPath("$.counts[0].count")
            .isEqualTo(2)
    }
}
