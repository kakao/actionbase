package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.edge.payload.DataFrameEdgePayload
import com.kakao.actionbase.core.metadata.common.AggregationConstants.TOPK_DATABASE
import com.kakao.actionbase.core.metadata.common.AggregationConstants.TOPK_EXPIRE_TABLE
import com.kakao.actionbase.engine.AggregationEngine
import com.kakao.actionbase.engine.service.AggregationService
import com.kakao.actionbase.engine.service.MutationService
import com.kakao.actionbase.engine.service.QueryService
import com.kakao.actionbase.server.test.E2ETestBase

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@Import(MetadataAggQueryControllerE2ETest.MutableClockConfig::class)
class MetadataAggQueryControllerE2ETest : E2ETestBase() {
    private val db = "commerce"
    private val table = "orders"
    private val scoreTable = "orders__topk"
    private val scoreFqn = "$db.$scoreTable"
    private val expireFqn = "$TOPK_DATABASE.$TOPK_EXPIRE_TABLE"
    private val topkName = "top_purchased_1y"

    @Autowired
    private lateinit var clock: MutableClock

    @BeforeAll
    fun setup() {
        // topk system database + expire table (may already exist from other tests, ignore conflicts)
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$TOPK_DATABASE", "comment": "test"}""")
            .exchange()

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
                    "source": {"type": "long", "comment": "partition"},
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

        // commerce database (may already exist)
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$db", "comment": "test"}""")
            .exchange()

        // score companion table with score_desc index
        client
            .post()
            .uri("/graph/v3/databases/$db/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "$scoreTable",
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
                  "storage": "datastore://test_namespace/$scoreTable",
                  "mode": "SYNC",
                  "comment": "top-k score"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        // original table with topk group (expireAfterMillis > 0 to exercise expire write)
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
                    "properties": [],
                    "direction": "OUT",
                    "indexes": [],
                    "groups": [{
                      "group": "purchased_count",
                      "type": "COUNT",
                      "fields": [{"name": "_target"}],
                      "directionType": "OUT",
                      "aggregations": {
                        "topk": [{
                          "topk": "$topkName",
                          "ranges": "_target:eq:{_target}",
                          "expireAfterMillis": 31536000000,
                          "table": {"score": "$scoreFqn", "expire": "$expireFqn"}
                        }]
                      }
                    }],
                    "caches": []
                  },
                  "storage": "datastore://test_namespace/$table",
                  "mode": "SYNC",
                  "comment": "orders"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }

    @Test
    fun `topk returns entries in descending score order after aggregate`() {
        clock.setInstant(Instant.parse("2026-06-10T00:00:00Z"))

        // 1. mutate original: user1 -> banana (x3), apple (x1), melon (x2)
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$table/multi-edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "id": 1, "source": "user1", "target": "banana", "properties": {}}},
                    {"type": "INSERT", "edge": {"version": 2, "id": 2, "source": "user1", "target": "banana", "properties": {}}},
                    {"type": "INSERT", "edge": {"version": 3, "id": 3, "source": "user1", "target": "banana", "properties": {}}},
                    {"type": "INSERT", "edge": {"version": 4, "id": 4, "source": "user1", "target": "apple", "properties": {}}},
                    {"type": "INSERT", "edge": {"version": 5, "id": 5, "source": "user1", "target": "melon", "properties": {}}},
                    {"type": "INSERT", "edge": {"version": 6, "id": 6, "source": "user1", "target": "melon", "properties": {}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        // 2. aggregate for each (source, target) pair — the topk uses ranges "_target:eq:{_target}"
        for (target in listOf("banana", "apple", "melon")) {
            client
                .post()
                .uri("/graph/v3/aggregations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    """
                    {
                      "type": "TOPK",
                      "items": [
                        {"database": "$db", "table": "$table",
                         "edge": {"version": 1, "source": "user1", "target": "$target", "properties": {}, "context": {}}}
                      ]
                    }
                    """.trimIndent(),
                ).exchange()
                .expectStatus()
                .isOk
        }

        // 3. topk query for user1 — expect [banana=3, melon=2, apple=1]
        val response =
            client
                .get()
                .uri(
                    "/graph/v3/databases/$db/tables/$table/aggregations/topk/$topkName" +
                        "?entity=user1&limit=10",
                ).exchange()
                .expectStatus()
                .isOk
                .expectBody<DataFrameEdgePayload>()
                .returnResult()
                .responseBody!!

        assertEquals(3, response.count)
        val targets = response.edges.map { it.target.toString() to (it.properties["score"] as Number).toLong() }
        assertEquals(listOf("banana" to 3L, "melon" to 2L, "apple" to 1L), targets)
    }

    @Test
    fun `topk returns 400 when topk name is unknown`() {
        client
            .get()
            .uri(
                "/graph/v3/databases/$db/tables/$table/aggregations/topk/no_such_topk" +
                    "?entity=user1&limit=10",
            ).exchange()
            .expectStatus()
            .isBadRequest
    }

    @Test
    fun `topk survives an expire sweep triggered before the 1y window ends`() {
        // aggregate lays down score + expire rows with expiresAt = t0 + 1 year.
        clock.setInstant(Instant.parse("2026-07-01T00:00:00Z"))
        val t0 = clock.instant()

        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$table/multi-edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 10, "id": 10, "source": "userW", "target": "banana", "properties": {}}},
                    {"type": "INSERT", "edge": {"version": 11, "id": 11, "source": "userW", "target": "banana", "properties": {}}}
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
                    {"database": "$db", "table": "$table",
                     "edge": {"version": 1, "source": "userW", "target": "banana", "properties": {}, "context": {}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        // Advance clock 6 months so we are still inside the 1-year window (expiresAt = t0 + 1y).
        val advancedInstant = t0.plusSeconds(60L * 60 * 24 * 180)
        clock.setInstant(advancedInstant)

        // Sanity: score row is present before sweep.
        val before =
            client
                .get()
                .uri(
                    "/graph/v3/databases/$db/tables/$table/aggregations/topk/$topkName" +
                        "?entity=userW&limit=10",
                ).exchange()
                .expectStatus()
                .isOk
                .expectBody<DataFrameEdgePayload>()
                .returnResult()
                .responseBody!!
        assertEquals(1, before.count)
        assertEquals(
            "banana",
            before.edges
                .single()
                .target
                .toString(),
        )
        assertEquals(2L, (before.edges.single().properties["score"] as Number).toLong())
    }

    @Test
    fun `expire CDC re-aggregate drops targets that fall out of the rolling window`() {
        val windowedDb = "commerce-window"
        val windowedTable = "orders_window"
        val windowedScore = "orders_window__topk"
        val windowedScoreFqn = "$windowedDb.$windowedScore"
        val windowedTopk = "top_purchased_1y"
        val entity = "userR"

        // Setup: score companion + original table whose topk uses a rolling `now`-based window.
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$windowedDb", "comment": "test"}""")
            .exchange()

        client
            .post()
            .uri("/graph/v3/databases/$windowedDb/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "$windowedScore",
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
                  "storage": "datastore://test_namespace/$windowedScore",
                  "mode": "SYNC",
                  "comment": "topk score"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/graph/v3/databases/$windowedDb/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "$windowedTable",
                  "schema": {
                    "type": "MULTI_EDGE",
                    "id": {"type": "long", "comment": "order id"},
                    "source": {"type": "string", "comment": "user"},
                    "target": {"type": "string", "comment": "item"},
                    "properties": [
                      {"name": "purchasedAt", "type": "long", "comment": "purchase time (yyyy-MM-dd from `now`)", "nullable": false}
                    ],
                    "direction": "OUT",
                    "indexes": [],
                    "groups": [{
                      "group": "purchased_rolling_1y",
                      "type": "COUNT",
                      "fields": [
                        {"name": "_target"},
                        {"name": "purchasedAt", "bucket": {"type": "date", "name": "day", "unit": "MILLISECOND", "timezone": "UTC", "format": "yyyy-MM-dd"}}
                      ],
                      "directionType": "OUT",
                      "aggregations": {
                        "topk": [{
                          "topk": "$windowedTopk",
                          "ranges": "_target:eq:{_target};day:bt:now-365d,now",
                          "expireAfterMillis": 31536000000,
                          "table": {"score": "$windowedScoreFqn", "expire": "$expireFqn"}
                        }]
                      }
                    }],
                    "caches": []
                  },
                  "storage": "datastore://test_namespace/$windowedTable",
                  "mode": "SYNC",
                  "comment": "orders with a rolling 1y window"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        // t0 = 2026-06-01. Seed: item1 x3 back on 2026-05-01 (inside window),
        // item2 x1 today. After clock advances one year, item1's day falls out of `now-365d,now`.
        val t0 = Instant.parse("2026-06-01T00:00:00Z")
        clock.setInstant(t0)
        val item1Day = Instant.parse("2026-05-01T00:00:00Z").toEpochMilli()
        val item2Day = t0.toEpochMilli()

        client
            .post()
            .uri("/graph/v3/databases/$windowedDb/tables/$windowedTable/multi-edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "id": 200, "source": "$entity", "target": "item1", "properties": {"purchasedAt": $item1Day}}},
                    {"type": "INSERT", "edge": {"version": 2, "id": 201, "source": "$entity", "target": "item1", "properties": {"purchasedAt": $item1Day}}},
                    {"type": "INSERT", "edge": {"version": 3, "id": 202, "source": "$entity", "target": "item1", "properties": {"purchasedAt": $item1Day}}},
                    {"type": "INSERT", "edge": {"version": 4, "id": 203, "source": "$entity", "target": "item2", "properties": {"purchasedAt": $item2Day}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        for ((target, day) in listOf("item1" to item1Day, "item2" to item2Day)) {
            client
                .post()
                .uri("/graph/v3/aggregations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    """
                    {
                      "type": "TOPK",
                      "items": [
                        {"database": "$windowedDb", "table": "$windowedTable",
                         "edge": {"version": 1, "source": "$entity", "target": "$target", "properties": {"purchasedAt": $day}, "context": {}}}
                      ]
                    }
                    """.trimIndent(),
                ).exchange()
                .expectStatus()
                .isOk
        }

        val initial =
            client
                .get()
                .uri(
                    "/graph/v3/databases/$windowedDb/tables/$windowedTable/aggregations/topk/$windowedTopk" +
                        "?entity=$entity&limit=10",
                ).exchange()
                .expectStatus()
                .isOk
                .expectBody<DataFrameEdgePayload>()
                .returnResult()
                .responseBody!!
        val initialScores =
            initial.edges.associate { it.target.toString() to (it.properties["score"] as Number).toLong() }
        assertEquals(3L, initialScores["item1"])
        assertEquals(1L, initialScores["item2"])

        // Advance the clock by ~13 months so item1's day (2026-05-01) sits outside `now-365d,now`.
        clock.setInstant(t0.plusSeconds(60L * 60 * 24 * 400))

        // Touch-line CDC surrogate: aggregate against `topk.expire` with `processed=true`,
        // carrying the ranges captured at write time. The service resolves the original table
        // from `properties.table` and re-runs aggregate — `now`-based ranges now exclude item1.
        client
            .post()
            .uri("/graph/v3/aggregations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "type": "TOPK",
                  "items": [
                    {"database": "$TOPK_DATABASE", "table": "$TOPK_EXPIRE_TABLE",
                     "edge": {"version": 1, "source": "unused", "target": "unused",
                              "properties": {
                                "table": "$windowedDb.$windowedTable",
                                "topk": "$windowedTopk",
                                "directedSource": "$entity",
                                "directedTarget": "item1",
                                "direction": "OUT",
                                "ranges": "_target:eq:item1;day:bt:now-365d,now",
                                "processed": true
                              }, "context": {}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        val after =
            client
                .get()
                .uri(
                    "/graph/v3/databases/$windowedDb/tables/$windowedTable/aggregations/topk/$windowedTopk" +
                        "?entity=$entity&limit=10",
                ).exchange()
                .expectStatus()
                .isOk
                .expectBody<DataFrameEdgePayload>()
                .returnResult()
                .responseBody!!
        val positive = after.edges.filter { (it.properties["score"] as Number).toLong() > 0L }
        assertEquals(
            listOf("item2" to 1L),
            positive.map { it.target.toString() to (it.properties["score"] as Number).toLong() },
        )
    }

    @Test
    fun `expire CDC re-aggregate drops segment-only targets that fall out of the rolling window`() {
        val segmentDb = "commerce-segment-window"
        val segmentTable = "orders_segment_window"
        val segmentScore = "orders_segment_window__topk"
        val segmentScoreFqn = "$segmentDb.$segmentScore"
        val segmentTopk = "top_purchased_by_category_1y"
        val entity = "userS"

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
                  "table": "$segmentScore",
                  "schema": {
                    "type": "EDGE",
                    "source": {"type": "string", "comment": "entity|topk"},
                    "target": {"type": "string", "comment": "ranked segment"},
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
                  "storage": "datastore://test_namespace/$segmentScore",
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
                      "group": "purchased_by_category_1y",
                      "type": "COUNT",
                      "fields": [
                        {"name": "category"},
                        {"name": "purchasedAt", "bucket": {"type": "date", "name": "day", "unit": "MILLISECOND", "timezone": "UTC", "format": "yyyy-MM-dd"}}
                      ],
                      "directionType": "OUT",
                      "aggregations": {
                        "topk": [{
                          "topk": "$segmentTopk",
                          "ranges": "category:eq:{category};day:bt:now-365d,now",
                          "expireAfterMillis": 31536000000,
                          "table": {"score": "$segmentScoreFqn", "expire": "$expireFqn"}
                        }]
                      }
                    }],
                    "caches": []
                  },
                  "storage": "datastore://test_namespace/$segmentTable",
                  "mode": "SYNC",
                  "comment": "segment-only orders with a rolling 1y window"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        // t0 = 2026-06-01. Seed: fruit x3 back on 2026-05-01 (inside window),
        // meat x1 today. After clock advances one year, fruit's day falls out of `now-365d,now`.
        val t0 = Instant.parse("2026-06-01T00:00:00Z")
        clock.setInstant(t0)
        val fruitDay = Instant.parse("2026-05-01T00:00:00Z").toEpochMilli()
        val meatDay = t0.toEpochMilli()

        client
            .post()
            .uri("/graph/v3/databases/$segmentDb/tables/$segmentTable/multi-edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "id": 300, "source": "$entity", "target": "banana", "properties": {"category": "fruit", "purchasedAt": $fruitDay}}},
                    {"type": "INSERT", "edge": {"version": 2, "id": 301, "source": "$entity", "target": "apple",  "properties": {"category": "fruit", "purchasedAt": $fruitDay}}},
                    {"type": "INSERT", "edge": {"version": 3, "id": 302, "source": "$entity", "target": "melon",  "properties": {"category": "fruit", "purchasedAt": $fruitDay}}},
                    {"type": "INSERT", "edge": {"version": 4, "id": 303, "source": "$entity", "target": "beef",   "properties": {"category": "meat",  "purchasedAt": $meatDay}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        for ((category, day) in listOf("fruit" to fruitDay, "meat" to meatDay)) {
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
                         "edge": {"version": 1, "source": "$entity", "target": "-",
                                  "properties": {"category": "$category", "purchasedAt": $day}, "context": {}}}
                      ]
                    }
                    """.trimIndent(),
                ).exchange()
                .expectStatus()
                .isOk
        }

        val initial =
            client
                .get()
                .uri(
                    "/graph/v3/databases/$segmentDb/tables/$segmentTable/aggregations/topk/$segmentTopk" +
                        "?entity=$entity&limit=10",
                ).exchange()
                .expectStatus()
                .isOk
                .expectBody<DataFrameEdgePayload>()
                .returnResult()
                .responseBody!!
        val initialScores =
            initial.edges.associate { it.target.toString() to (it.properties["score"] as Number).toLong() }
        assertEquals(3L, initialScores["fruit"])
        assertEquals(1L, initialScores["meat"])

        // Advance the clock ~13 months so fruit's day (2026-05-01) sits outside `now-365d,now`.
        clock.setInstant(t0.plusSeconds(60L * 60 * 24 * 400))

        // Touch-line CDC surrogate for the `fruit` segment.
        client
            .post()
            .uri("/graph/v3/aggregations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "type": "TOPK",
                  "items": [
                    {"database": "$TOPK_DATABASE", "table": "$TOPK_EXPIRE_TABLE",
                     "edge": {"version": 1, "source": "unused", "target": "unused",
                              "properties": {
                                "table": "$segmentDb.$segmentTable",
                                "topk": "$segmentTopk",
                                "directedSource": "$entity",
                                "directedTarget": "fruit",
                                "direction": "OUT",
                                "ranges": "category:eq:fruit;day:bt:now-365d,now",
                                "processed": true
                              }, "context": {}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        val after =
            client
                .get()
                .uri(
                    "/graph/v3/databases/$segmentDb/tables/$segmentTable/aggregations/topk/$segmentTopk" +
                        "?entity=$entity&limit=10",
                ).exchange()
                .expectStatus()
                .isOk
                .expectBody<DataFrameEdgePayload>()
                .returnResult()
                .responseBody!!
        val positive = after.edges.filter { (it.properties["score"] as Number).toLong() > 0L }
        assertEquals(
            listOf("meat" to 1L),
            positive.map { it.target.toString() to (it.properties["score"] as Number).toLong() },
        )
    }

    @TestConfiguration
    class MutableClockConfig {
        @Bean
        fun mutableClock(): MutableClock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))

        @Bean
        @Primary
        fun clock(mutableClock: MutableClock): Clock = mutableClock

        @Bean
        @Primary
        fun aggregationService(
            queryService: QueryService,
            mutationService: MutationService,
            engine: AggregationEngine,
            mutableClock: MutableClock,
        ): AggregationService = AggregationService(queryService, mutationService, engine, mutableClock)
    }

    class MutableClock(
        private var now: Instant,
    ) : Clock() {
        fun setInstant(instant: Instant) {
            now = instant
        }

        override fun instant(): Instant = now

        override fun withZone(zone: java.time.ZoneId): Clock = this

        override fun getZone(): java.time.ZoneId = ZoneOffset.UTC

        override fun millis(): Long = now.toEpochMilli()
    }
}
