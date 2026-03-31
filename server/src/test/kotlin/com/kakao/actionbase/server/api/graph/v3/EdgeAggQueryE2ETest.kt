package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType

/**
 * E2E test for AGG query.
 *
 * Verifies that AGG queries correctly return aggregated counts
 * for group fields with various field type and bucket combinations.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EdgeAggQueryE2ETest : E2ETestBase() {
    private val db = "agg-test-db"
    private val table = "agg-test-table"

    private val group1 = "count_by_day"
    private val group2 = "count_by_permission_day"
    private val group3 = "count_by_category_day"

    // 1704067200000 = 2024-01-01 00:00:00 UTC
    private val ts = 1704067200000L

    @BeforeAll
    fun setup() {
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$db", "comment": "test db"}""")
            .exchange()
            .expectStatus()
            .isOk

        val dateBucket = """{"type": "date", "name": "day", "unit": "MILLISECOND", "timezone": "+00:00", "format": "yyyy-MM-dd"}"""

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
                    "source": {"type": "long", "comment": "source"},
                    "target": {"type": "long", "comment": "target"},
                    "properties": [
                      {"name": "permission", "type": "string", "comment": "perm", "nullable": false},
                      {"name": "categoryId", "type": "long", "comment": "category", "nullable": false},
                      {"name": "createdAt", "type": "long", "comment": "ts", "nullable": false}
                    ],
                    "direction": "BOTH",
                    "indexes": [{"index": "created_at_desc", "fields": [{"field": "createdAt", "order": "DESC"}]}],
                    "groups": [
                      {"group": "$group1", "type": "COUNT", "fields": [{"name": "createdAt", "bucket": $dateBucket}]},
                      {"group": "$group2", "type": "COUNT", "fields": [{"name": "permission"}, {"name": "createdAt", "bucket": $dateBucket}]},
                      {"group": "$group3", "type": "COUNT", "fields": [{"name": "categoryId"}, {"name": "createdAt", "bucket": $dateBucket}]}
                    ]
                  },
                  "storage": "datastore://test_namespace/agg_test_hbase_table",
                  "mode": "SYNC",
                  "comment": "test table"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$table/edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "source": 100, "target": 1000, "properties": {"permission": "read", "categoryId": 1, "createdAt": $ts}}},
                    {"type": "INSERT", "edge": {"version": 2, "source": 100, "target": 1001, "properties": {"permission": "read", "categoryId": 1, "createdAt": $ts}}},
                    {"type": "INSERT", "edge": {"version": 3, "source": 100, "target": 1002, "properties": {"permission": "write", "categoryId": 2, "createdAt": $ts}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }

    private fun aggQuery(
        group: String,
        ranges: String,
    ) = client
        .get()
        .uri { builder ->
            builder
                .path("/graph/v3/databases/$db/tables/$table/edges/agg/$group")
                .queryParam("start", "100")
                .queryParam("direction", "OUT")
                .queryParam("ranges", ranges)
                .build()
        }.exchange()
        .expectStatus()
        .isOk
        .expectBody()

    /**
     * | rowKey (source, direction, group) | qualifier (day) | value |
     * |-----------------------------------|-----------------|-------|
     * | 100, OUT, count_by_day            | "2024-01-01"    | 3     |
     */
    @Nested
    inner class BucketFieldOnly {
        @Test
        fun `returns aggregated count`() {
            aggQuery(group1, "day:eq:2024-01-01")
                .jsonPath("$.count")
                .isEqualTo(1)
                .jsonPath("$.groups[0].value")
                .isEqualTo(3)
        }
    }

    /**
     * | rowKey (source, direction, group) | qualifier (permission, day) | value |
     * |-----------------------------------|-----------------------------|-------|
     * | 100, OUT, count_by_permission_day | "read",  "2024-01-01"       | 2     |
     * | 100, OUT, count_by_permission_day | "write", "2024-01-01"       | 1     |
     */
    @Nested
    inner class StringFieldAndBucket {
        @Test
        fun `returns aggregated count for matching value`() {
            aggQuery(group2, "permission:eq:read;day:eq:2024-01-01")
                .jsonPath("$.count")
                .isEqualTo(1)
                .jsonPath("$.groups[0].value")
                .isEqualTo(2)
        }

        @Test
        fun `returns correct count for different value`() {
            aggQuery(group2, "permission:eq:write;day:eq:2024-01-01")
                .jsonPath("$.count")
                .isEqualTo(1)
                .jsonPath("$.groups[0].value")
                .isEqualTo(1)
        }
    }

    /**
     * | rowKey (source, direction, group) | qualifier (categoryId, day) | value |
     * |-----------------------------------|-----------------------------|-------|
     * | 100, OUT, count_by_category_day   | 1L, "2024-01-01"            | 2     |
     * | 100, OUT, count_by_category_day   | 2L, "2024-01-01"            | 1     |
     */
    @Nested
    inner class LongFieldAndBucket {
        @Test
        fun `returns aggregated count for matching value`() {
            aggQuery(group3, "categoryId:eq:1;day:eq:2024-01-01")
                .jsonPath("$.count")
                .isEqualTo(1)
                .jsonPath("$.groups[0].value")
                .isEqualTo(2)
        }

        @Test
        fun `returns correct count for different value`() {
            aggQuery(group3, "categoryId:eq:2;day:eq:2024-01-01")
                .jsonPath("$.count")
                .isEqualTo(1)
                .jsonPath("$.groups[0].value")
                .isEqualTo(1)
        }
    }
}
