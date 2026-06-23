package com.kakao.actionbase.server.api.graph.v3

import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EdgeCacheQueryE2ETest : E2ETestBase() {
    private val objectMapper = jacksonObjectMapper()

    private val db = "test-db"
    private val edgeTable = "wishlist"
    private val multiEdgeTable = "orders"

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

        client
            .post()
            .uri("/graph/v3/databases/$db/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "$edgeTable",
                  "schema": {
                    "type": "EDGE",
                    "source": {"type": "long", "comment": "src"},
                    "target": {"type": "long", "comment": "tgt"},
                    "properties": [
                      {"name": "createdAt", "type": "long", "comment": "ts", "nullable": true},
                      {"name": "permission", "type": "string", "comment": "perm", "nullable": true}
                    ],
                    "direction": "BOTH",
                    "indexes": [],
                    "groups": [],
                    "caches": [
                      {
                        "cache": "recent_wishlist",
                        "fields": [{"field": "createdAt", "order": "DESC"}],
                        "limit": 100
                      },
                      {
                        "cache": "permission_created_at_desc",
                        "fields": [
                          {"field": "permission", "order": "ASC"},
                          {"field": "createdAt", "order": "DESC"}
                        ],
                        "limit": 100
                      },
                      {
                        "cache": "whitelisted_permission",
                        "fields": [
                          {"field": "permission", "order": "ASC", "dimension": ["me"]},
                          {"field": "createdAt", "order": "DESC"}
                        ],
                        "limit": 100
                      }
                    ]
                  },
                  "storage": "datastore://test_namespace/wishlist",
                  "mode": "SYNC",
                  "comment": "edge with cache"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        // Single-source fixtures used by recent_wishlist (single-field cache) tests.
        // permission is omitted, so the compound cache stores `(null, createdAt, target)`
        // for these rows — they don't intersect with the compound-test source below.
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$edgeTable/edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "source": 1000, "target": 2000, "properties": {"createdAt": 100}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": 1000, "target": 2001, "properties": {"createdAt": 101}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        // Compound-cache fixtures on a separate source. permission groups: "me" (1 row),
        // "others" (4 rows). Within "others", createdAt descends 500 → 400 → 300 → 100.
        // Lex byte order = (permission ASC, createdAt DESC).
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$edgeTable/edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "source": 1, "target": 2, "properties": {"permission": "me",     "createdAt": 200}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": 1, "target": 3, "properties": {"permission": "others", "createdAt": 500}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": 1, "target": 4, "properties": {"permission": "others", "createdAt": 400}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": 1, "target": 5, "properties": {"permission": "others", "createdAt": 300}}},
                    {"type": "INSERT", "edge": {"version": 1, "source": 1, "target": 6, "properties": {"permission": "others", "createdAt": 100}}}
                  ]
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
                  "table": "$multiEdgeTable",
                  "schema": {
                    "type": "MULTI_EDGE",
                    "id": {"type": "long", "comment": "order id"},
                    "source": {"type": "long", "comment": "buyer"},
                    "target": {"type": "long", "comment": "seller"},
                    "properties": [
                      {"name": "paidAt", "type": "long", "comment": "payment time", "nullable": false}
                    ],
                    "direction": "BOTH",
                    "indexes": [],
                    "groups": [],
                    "caches": [
                      {
                        "cache": "paid_at_desc",
                        "fields": [{"field": "paidAt", "order": "DESC"}],
                        "limit": 100
                      }
                    ]
                  },
                  "storage": "datastore://test_namespace/orders",
                  "mode": "SYNC",
                  "comment": "multi edge with cache"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$multiEdgeTable/multi-edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "id": 100, "source": 1000, "target": 2000, "properties": {"paidAt": 300}}},
                    {"type": "INSERT", "edge": {"version": 1, "id": 101, "source": 1000, "target": 2000, "properties": {"paidAt": 200}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }

    /**
     * EdgeCache (source=1000, direction=OUT, cache=recent_wishlist)
     * |       row key        | qualifier (DESC) |           value          |
     * |----------------------|------------------|--------------------------|
     * | hash|1000|T|-6|OUT|C | ~101 | 2001      | version=1, createdAt=101 |
     * |                      | ~100 | 2000      | version=1, createdAt=100 |
     *
     * Expected: [2001(101), 2000(100)] — DESC order
     */
    @Test
    fun `seek OUT returns edges in DESC order`() {
        client
            .get()
            .uri("/graph/v3/databases/$db/tables/$edgeTable/edges/seek/recent_wishlist?start=1000&direction=OUT")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.count")
            .isEqualTo(2)
            .jsonPath("$.edges.length()")
            .isEqualTo(2)
            .jsonPath("$.edges[0].target")
            .isEqualTo(2001)
            .jsonPath("$.edges[0].properties.createdAt")
            .isEqualTo(101)
            .jsonPath("$.edges[1].target")
            .isEqualTo(2000)
            .jsonPath("$.edges[1].properties.createdAt")
            .isEqualTo(100)
            .jsonPath("$.hasNext")
            .isEqualTo(false)
    }

    /**
     * Same wide row as above, but with limit=1 + cursor pagination.
     *
     * Expected:
     *   - Page 1 (limit=1): [2001(101)] — hasNext=true, offset=cursor
     *   - Page 2 (limit=1, offset=cursor): [2000(100)] — hasNext=false
     */
    @Test
    fun `seek OUT with offset paginates results`() {
        val page1 =
            client
                .get()
                .uri("/graph/v3/databases/$db/tables/$edgeTable/edges/seek/recent_wishlist?start=1000&direction=OUT&limit=1")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.count")
                .isEqualTo(1)
                .jsonPath("$.edges[0].target")
                .isEqualTo(2001)
                .jsonPath("$.hasNext")
                .isEqualTo(true)
                .jsonPath("$.offset")
                .isNotEmpty
                .returnResult()

        val body = objectMapper.readTree(page1.responseBody)
        val offset = body["offset"].asText()

        client
            .get()
            .uri("/graph/v3/databases/$db/tables/$edgeTable/edges/seek/recent_wishlist?start=1000&direction=OUT&limit=1&offset=$offset")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.count")
            .isEqualTo(1)
            .jsonPath("$.edges[0].target")
            .isEqualTo(2000)
            .jsonPath("$.edges[0].properties.createdAt")
            .isEqualTo(100)
            .jsonPath("$.hasNext")
            .isEqualTo(false)
            .jsonPath("$.offset")
            .doesNotExist()
    }

    /**
     * EdgeCache wide row (directedSource=2000, IN, recent_wishlist):
     * |       row key       | qualifier (DESC) |           value          |
     * |---------------------|------------------|--------------------------|
     * | hash|2000|T|-6|IN|C | ~100 | 1000      | version=1, createdAt=100 |
     *
     * Expected: [1000(100)] — edge where target=2000
     */
    @Test
    fun `seek IN returns edges for target vertex`() {
        client
            .get()
            .uri("/graph/v3/databases/$db/tables/$edgeTable/edges/seek/recent_wishlist?start=2000&direction=IN")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.count")
            .isEqualTo(1)
            .jsonPath("$.edges[0].source")
            .isEqualTo(1000)
            .jsonPath("$.edges[0].target")
            .isEqualTo(2000)
            .jsonPath("$.edges[0].properties.createdAt")
            .isEqualTo(100)
    }

    /**
     * Single-field DESC cache filtered by `ranges=createdAt:gte:101`.
     *
     * EdgeCache (source=1000, OUT, recent_wishlist) wide row:
     * |       row key        | qualifier (DESC) | target | createdAt | vs gte:101 |
     * |----------------------|------------------|--------|-----------|------------|
     * | hash|1000|T|-6|OUT|C | ~101             | 2001   | 101       | included   |
     * |                      | ~100             | 2000   | 100       | excluded   |
     *
     * Expected: only 2001 (createdAt=101) survives the `ColumnRangeFilter`.
     */
    @Test
    fun `seek with single-field range filter excludes rows below the bound`() {
        client
            .get()
            .uri(
                "/graph/v3/databases/$db/tables/$edgeTable/edges/seek/recent_wishlist" +
                    "?start=1000&direction=OUT&ranges=createdAt:gte:101",
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.count")
            .isEqualTo(1)
            .jsonPath("$.edges[0].target")
            .isEqualTo(2001)
            .jsonPath("$.edges[0].properties.createdAt")
            .isEqualTo(101)
            .jsonPath("$.hasNext")
            .isEqualTo(false)
    }

    /**
     * Compound cache (permission ASC, createdAt DESC) filtered by leading-prefix `Eq`.
     *
     * EdgeCache (source=1, OUT, permission_created_at_desc) wide row:
     * |      row key      | permission (ASC) | createdAt (DESC) | target | vs eq:others |
     * |-------------------|------------------|------------------|--------|--------------|
     * | hash|1|T|-6|OUT|C | "me"             | 200              | 2      | excluded     |
     * |                   | "others"         | 500              | 3      | included     |
     * |                   | "others"         | 400              | 4      | included     |
     * |                   | "others"         | 300              | 5      | included     |
     * |                   | "others"         | 100              | 6      | included     |
     *
     * `ranges=permission:eq:others` brackets the four "others" rows into a single
     * `ColumnRangeFilter`. Expected: targets [3, 4, 5, 6] in createdAt DESC order.
     */
    @Test
    fun `seek with compound Eq filter returns only the matching permission group`() {
        client
            .get()
            .uri(
                "/graph/v3/databases/$db/tables/$edgeTable/edges/seek/permission_created_at_desc" +
                    "?start=1&direction=OUT&ranges=permission:eq:others",
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.count")
            .isEqualTo(4)
            .jsonPath("$.edges[0].target")
            .isEqualTo(3)
            .jsonPath("$.edges[0].properties.createdAt")
            .isEqualTo(500)
            .jsonPath("$.edges[3].target")
            .isEqualTo(6)
            .jsonPath("$.edges[3].properties.createdAt")
            .isEqualTo(100)
    }

    /**
     * Same compound cache, but `Eq` on permission combined with a trailing range
     * on createdAt — the canonical `WHERE permission = X AND createdAt < T` shape.
     *
     * `ranges=permission:eq:others;createdAt:lt:300` keeps only "others" rows
     * with createdAt < 300, i.e. the single row with createdAt=100.
     */
    @Test
    fun `seek with compound Eq plus trailing range filters within the group`() {
        client
            .get()
            .uri(
                "/graph/v3/databases/$db/tables/$edgeTable/edges/seek/permission_created_at_desc" +
                    "?start=1&direction=OUT&ranges=permission:eq:others;createdAt:lt:300",
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.count")
            .isEqualTo(1)
            .jsonPath("$.edges[0].target")
            .isEqualTo(6)
            .jsonPath("$.edges[0].properties.createdAt")
            .isEqualTo(100)
    }

    /**
     * Same source=1 fixture as the compound-cache tests, but queried through the
     * `whitelisted_permission` cache whose `permission` field declares
     * `dimension: ["me"]`. The dimension is a write-time whitelist: only the single
     * `permission="me"` edge (target=2) was ever stored in this cache; the four
     * `permission="others"` edges are silently absent.
     *
     * EdgeCache (source=1, OUT, whitelisted_permission) wide row:
     * |      row key      | permission (ASC) | createdAt (DESC) | target |
     * |-------------------|------------------|------------------|--------|
     * | hash|1|T|-6|OUT|C | "me"             | 200              | 2      |
     *
     * Expected: a bare seek returns only target=2; an Eq filter on the excluded
     * "others" returns zero rows even though that source has four such edges.
     */
    @Test
    fun `seek with dimension-filtered cache excludes non-whitelisted permissions at write time`() {
        client
            .get()
            .uri(
                "/graph/v3/databases/$db/tables/$edgeTable/edges/seek/whitelisted_permission" +
                    "?start=1&direction=OUT",
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.count")
            .isEqualTo(1)
            .jsonPath("$.edges[0].target")
            .isEqualTo(2)
            .jsonPath("$.edges[0].properties.permission")
            .isEqualTo("me")

        // permission="others" was filtered out at write time, so the cache holds nothing
        // for this branch even though four such edges exist on the underlying source.
        client
            .get()
            .uri(
                "/graph/v3/databases/$db/tables/$edgeTable/edges/seek/whitelisted_permission" +
                    "?start=1&direction=OUT&ranges=permission:eq:others",
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.count")
            .isEqualTo(0)
    }

    /**
     * EdgeCache (source=1000, direction=OUT, cache=paid_at_desc)
     * |       row key         | qualifier (DESC) |         value         |
     * |-----------------------|------------------|-----------------------|
     * | hash|1000|T|-6|OUT|C  | ~300 | 100       | version=1, paidAt=300 |
     * |                       | ~200 | 101       | version=1, paidAt=200 |
     *
     * Expected: targets=[100, 101] — id is target in response
     */
    @Test
    fun `seek MultiEdge OUT returns edges with id as target`() {
        client
            .get()
            .uri("/graph/v3/databases/$db/tables/$multiEdgeTable/edges/seek/paid_at_desc?start=1000&direction=OUT")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.count")
            .isEqualTo(2)
            .jsonPath("$.edges[0].target")
            .isEqualTo(100)
            .jsonPath("$.edges[0].properties.paidAt")
            .isEqualTo(300)
            .jsonPath("$.edges[1].target")
            .isEqualTo(101)
            .jsonPath("$.edges[1].properties.paidAt")
            .isEqualTo(200)
    }
}
