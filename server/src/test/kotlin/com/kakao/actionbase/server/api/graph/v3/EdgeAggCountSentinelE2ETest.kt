package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EdgeAggCountSentinelE2ETest : E2ETestBase() {
    private val db = "test-agg-count-db"
    private val table = "follows"
    private val objectMapper = jacksonObjectMapper()

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
                    "type": "EDGE",
                    "source": {"type": "long", "comment": "src"},
                    "target": {"type": "long", "comment": "tgt"},
                    "properties": [
                      {"name": "category", "type": "string", "comment": "cat", "nullable": true}
                    ],
                    "direction": "BOTH",
                    "indexes": [],
                    "groups": [
                      {
                        "group": "by_category",
                        "type": "SUM",
                        "fields": [{"name": "category"}]
                      }
                    ],
                    "caches": []
                  },
                  "storage": "datastore://test_namespace/$table",
                  "mode": "SYNC",
                  "comment": "test"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        // Insert 3 outgoing edges from node 1 (1→2, 1→3, 1→4)
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$table/edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "source": 1, "target": 2, "properties": {"category": "A"}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": 1, "target": 3, "properties": {"category": "A"}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": 1, "target": 4, "properties": {"category": "B"}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        // Insert 2 incoming edges to node 1 (5→1, 6→1), 1 outgoing from node 5 (5→1)
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$table/edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "source": 5, "target": 1, "properties": {"category": "A"}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": 6, "target": 1, "properties": {"category": "B"}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }

    /**
     * Reads the native count for a given start/direction from the /edges/count endpoint.
     * Used as ground truth to compare against agg/__count__.
     */
    private fun nativeCount(
        start: Long,
        direction: String,
    ): Long {
        val body =
            client
                .get()
                .uri("/graph/v3/databases/$db/tables/$table/edges/count?start=$start&direction=$direction")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody(String::class.java)
                .returnResult()
                .responseBody!!
        val json: Map<String, Any> = objectMapper.readValue(body)
        return (json["count"] as Number).toLong()
    }

    @Test
    fun `agg with __count__ sentinel returns same OUT count as native count endpoint`() {
        val expected = nativeCount(1L, "OUT")

        client
            .get()
            .uri("/graph/v3/databases/$db/tables/$table/edges/agg/__count__?start=1&direction=OUT")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.count")
            .isEqualTo(1)
            .jsonPath("$.groups.length()")
            .isEqualTo(1)
            .jsonPath("$.groups[0].start")
            .isEqualTo(1)
            .jsonPath("$.groups[0].direction")
            .isEqualTo("OUT")
            .jsonPath("$.groups[0].value")
            .isEqualTo(expected)
    }

    @Test
    fun `agg with __count__ sentinel returns same IN count as native count endpoint`() {
        val expected = nativeCount(1L, "IN")

        client
            .get()
            .uri("/graph/v3/databases/$db/tables/$table/edges/agg/__count__?start=1&direction=IN")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.count")
            .isEqualTo(1)
            .jsonPath("$.groups.length()")
            .isEqualTo(1)
            .jsonPath("$.groups[0].start")
            .isEqualTo(1)
            .jsonPath("$.groups[0].direction")
            .isEqualTo("IN")
            .jsonPath("$.groups[0].value")
            .isEqualTo(expected)
    }

    @Test
    fun `agg with __count__ and ranges returns 400`() {
        client
            .get()
            .uri("/graph/v3/databases/$db/tables/$table/edges/agg/__count__?start=1&direction=OUT&ranges=foo%3Dbar")
            .exchange()
            .expectStatus()
            .isBadRequest
    }

    @Test
    fun `agg with __count__ and multiple starts returns one group per node with value matching native count`() {
        val expectedNode1 = nativeCount(1L, "OUT")
        val expectedNode5 = nativeCount(5L, "OUT")

        val body =
            client
                .get()
                .uri("/graph/v3/databases/$db/tables/$table/edges/agg/__count__?start=1&start=5&direction=OUT")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody(String::class.java)
                .returnResult()
                .responseBody!!

        val json: Map<String, Any> = objectMapper.readValue(body)
        val groups = json["groups"] as List<*>

        assertEquals(2, groups.size)
        assertEquals(2, json["count"])

        @Suppress("UNCHECKED_CAST")
        val byStart = groups.associateBy { ((it as Map<String, Any>)["start"] as Number).toLong() }

        val group1 = byStart[1L] as? Map<*, *>
        val group5 = byStart[5L] as? Map<*, *>
        assertNotNull(group1, "group for start=1 not found")
        assertNotNull(group5, "group for start=5 not found")
        assertEquals(expectedNode1, (group1!!["value"] as Number).toLong())
        assertEquals(expectedNode5, (group5!!["value"] as Number).toLong())
    }

    @Test
    fun `agg with existing regular group and no ranges returns 400`() {
        // "by_category" is a real group defined in the table schema — ranges is required for non-sentinel groups
        client
            .get()
            .uri("/graph/v3/databases/$db/tables/$table/edges/agg/by_category?start=1&direction=OUT")
            .exchange()
            .expectStatus()
            .isBadRequest
    }
}
