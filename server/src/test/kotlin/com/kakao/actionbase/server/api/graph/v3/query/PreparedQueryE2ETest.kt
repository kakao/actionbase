package com.kakao.actionbase.server.api.graph.v3.query

import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.http.MediaType

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// Registering and running a query has taken past the 5-second default under a full build. Only this suite
// gets the longer timeout, so every other test keeps the shorter one as a check on how slow a call can get.
@AutoConfigureWebTestClient(timeout = "30s")
class PreparedQueryE2ETest : E2ETestBase() {
    private val db = "shop"
    private val table = "purchases_table"
    private val rank = "${table}__topk"
    private val rankFqn = "$db.$rank"
    private val topkName = "top_purchased"
    private val mapper = jacksonObjectMapper()

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
                  "table": "$rank",
                  "schema": {
                    "type": "EDGE",
                    "source": {"type": "string", "comment": "database|table|topk|entity|dimensionValues"},
                    "target": {"type": "string", "comment": "topkDimensionValue"},
                    "properties": [
                      {"name": "metric", "type": "long", "comment": "aggregated metric", "nullable": false},
                      {"name": "additionalProperties", "type": "string", "comment": "carried properties as JSON", "nullable": true}
                    ],
                    "direction": "OUT",
                    "indexes": [
                      {"index": "metric_desc", "fields": [{"field": "metric", "order": "DESC"}]}
                    ],
                    "groups": [],
                    "caches": []
                  },
                  "storage": "datastore://test_namespace/$rank",
                  "mode": "SYNC",
                  "comment": "topk rank rows"
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
                  "table": "$table",
                  "schema": {
                    "type": "MULTI_EDGE",
                    "id": {"type": "long", "comment": "order id"},
                    "source": {"type": "string", "comment": "user"},
                    "target": {"type": "string", "comment": "item"},
                    "properties": [
                      {"name": "category", "type": "string", "comment": "item category", "nullable": false},
                      {"name": "purchasedAt", "type": "long", "comment": "purchase time ms", "nullable": false}
                    ],
                    "direction": "OUT",
                    "indexes": [],
                    "groups": [{
                      "group": "purchased_count",
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
                          "ranges": "_target:eq:{_target};category:eq:{category};day:bt:2023-11-14,2024-11-13",
                          "dimension": "target",
                          "rank": "$rankFqn",
                          "additionalProperties": ["category"]
                        }]
                      }
                    }],
                    "caches": []
                  },
                  "storage": "datastore://test_namespace/$table",
                  "mode": "SYNC",
                  "comment": "user-to-item purchase edges"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        seedRanking()
    }

    /** Two purchases of `item1` and one of `item2`, then the aggregate call that writes the rank rows. */
    private fun seedRanking() {
        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$table/multi-edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [
                    {"type": "INSERT", "edge": {"version": 1, "id": 1, "source": "user1", "target": "item1", "properties": {"category": "grocery", "purchasedAt": 1710000000000}}},
                    {"type": "INSERT", "edge": {"version": 1, "id": 2, "source": "user1", "target": "item1", "properties": {"category": "grocery", "purchasedAt": 1710000000000}}},
                    {"type": "INSERT", "edge": {"version": 1, "id": 3, "source": "user1", "target": "item2", "properties": {"category": "grocery", "purchasedAt": 1710000000000}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk

        client
            .post()
            .uri("/aggregations/v1/aggregate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "items": [
                    {"database": "$db", "table": "$table",
                     "edge": {"version": 1, "source": "user1", "target": "item1", "properties": {"category": "grocery", "purchasedAt": 1710000000000}, "context": {}}},
                    {"database": "$db", "table": "$table",
                     "edge": {"version": 1, "source": "user1", "target": "item2", "properties": {"category": "grocery", "purchasedAt": 1710000000000}, "context": {}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }

    /** `limit` is declared an `INT` and written as a placeholder, so the cast has to happen for it to bind. */
    private fun register(): String {
        val body =
            client
                .post()
                .uri("/graph/v3/prepared-queries")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    """
                    {
                      "comment": "top purchased grocery items for one user",
                      "arguments": [
                        {"name": "entity", "type": "string", "comment": "the user asked about"},
                        {"name": "limit", "type": "int", "comment": "how many ranked rows to read"}
                      ],
                      "fetch": [
                        {
                          "type": "TOPK",
                          "name": "ranked",
                          "database": "$db",
                          "table": "$table",
                          "topk": "$topkName",
                          "entity": {"type": "VALUE", "value": ["{entity}"]},
                          "dimensionValues": {"category": "grocery"},
                          "limit": "{limit}"
                        }
                      ],
                      "transform": [
                        {
                          "type": "SQL",
                          "name": "result",
                          "sql": "SELECT target AS itemId, metric FROM ranked ORDER BY metric DESC"
                        }
                      ]
                    }
                    """.trimIndent(),
                ).exchange()
                .expectStatus()
                .isOk
                .expectBody(String::class.java)
                .returnResult()
                .responseBody!!

        val registered = mapper.readTree(body)
        assertEquals("top purchased grocery items for one user", registered["comment"].asText())
        assertEquals(listOf("entity", "limit"), registered["arguments"].map { it["name"].asText() })
        // The body comes back as it was registered, placeholder and all.
        assertEquals("{limit}", registered["fetch"][0]["limit"].asText())
        return registered["id"].asText()
    }

    private fun name(
        alias: String,
        target: String,
    ) {
        client
            .post()
            .uri("/graph/v3/prepared-queries/aliases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"alias": "$alias", "target": "$target", "comment": "홈 화면 추천"}""")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.target")
            .isEqualTo(target)
    }

    @Test
    fun `a registered query runs by the id it was given`() {
        val id = register()

        client
            .post()
            .uri("/graph/v3/query/$id")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"arguments": {"entity": "user1", "limit": 100}}""")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.items.length()")
            .isEqualTo(1)
            .jsonPath("$.items[0].name")
            .isEqualTo("result")
            .jsonPath("$.items[0].data.length()")
            .isEqualTo(2)
            .jsonPath("$.items[0].data[0].itemId")
            .isEqualTo("item1")
            .jsonPath("$.items[0].data[0].metric")
            .isEqualTo(2)
            .jsonPath("$.items[0].data[1].itemId")
            .isEqualTo("item2")
    }

    /** `limit` arrives as text, and the declared `INT` is what makes it usable where a number goes. */
    @Test
    fun `a value sent as text is read as the type the registration declared`() {
        val id = register()

        client
            .post()
            .uri("/graph/v3/query/$id")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"arguments": {"entity": "user1", "limit": "1"}}""")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.items[0].data.length()")
            .isEqualTo(1)
            .jsonPath("$.items[0].data[0].itemId")
            .isEqualTo("item1")
    }

    @Test
    fun `a named query runs and reads back under its name`() {
        val id = register()
        name("top_grocery", id)

        client
            .get()
            .uri("/graph/v3/prepared-queries/top_grocery")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.id")
            .isEqualTo(id)
            .jsonPath("$.active")
            .isEqualTo(true)

        client
            .post()
            .uri("/graph/v3/query/top_grocery")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"arguments": {"entity": "user1", "limit": 100}}""")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.items[0].data[0].itemId")
            .isEqualTo("item1")
    }

    @Test
    fun `queries and names are listed on their own paths`() {
        val id = register()
        name("listed_grocery", id)

        client
            .get()
            .uri("/graph/v3/prepared-queries")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$[?(@.id == '$id')].active")
            .isEqualTo(true)

        client
            .get()
            .uri("/graph/v3/prepared-queries/aliases")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$[?(@.alias == 'listed_grocery')].target")
            .isEqualTo(id)
    }

    /** A body sent whole, with its values alongside it: the same query, never registered. */
    @Test
    fun `a query sent whole runs with the values sent alongside it`() {
        client
            .post()
            .uri("/graph/v3/query")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "arguments": {"entity": "user1", "limit": 100},
                  "fetch": [
                    {
                      "type": "TOPK",
                      "name": "ranked",
                      "database": "$db",
                      "table": "$table",
                      "topk": "$topkName",
                      "entity": {"type": "VALUE", "value": ["{entity}"]},
                      "dimensionValues": {"category": "grocery"},
                      "limit": "{limit}",
                      "include": true
                    }
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.items[0].name")
            .isEqualTo("ranked")
            .jsonPath("$.items[0].data.length()")
            .isEqualTo(2)
    }

    @Test
    fun `calling a query nobody registered is a 404`() {
        client
            .post()
            .uri("/graph/v3/query/no_such_query")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"arguments": {"entity": "user1", "limit": 100}}""")
            .exchange()
            .expectStatus()
            .isNotFound
    }

    @Test
    fun `a name shaped like an id is refused`() {
        val id = register()

        client
            .post()
            .uri("/graph/v3/prepared-queries/aliases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"alias": "$id", "target": "$id", "comment": "id shaped"}""")
            .exchange()
            .expectStatus()
            .isBadRequest
    }
}
