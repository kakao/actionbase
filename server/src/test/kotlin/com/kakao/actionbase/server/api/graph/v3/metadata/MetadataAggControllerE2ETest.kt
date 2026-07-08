package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.server.test.E2ETestBase

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType

/**
 * End-to-end coverage for GET /graph/v3/aggregations.
 *
 * Two tables are created: one with a topk aggregation declared, one without.
 * The response should list the first and omit the second.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MetadataAggControllerE2ETest : E2ETestBase() {
    private val db = "commerce"
    private val sourceTable = "purchases"
    private val plainTable = "misc"
    private val scoreTable = "${sourceTable}__topk"
    private val scoreFqn = "$db.$scoreTable"

    @BeforeAll
    fun setup() {
        createDatabase(db)
        createMultiEdgeSourceTable()
        createEdgeTable(
            database = db,
            table = plainTable,
            propertiesJson = """{"name": "category", "type": "string", "comment": "cat", "nullable": true}""",
            groupsJson = """{"group": "by_target", "type": "COUNT", "fields": [{"name": "_target"}]}""",
        )
    }

    @Test
    fun `GET aggregations lists tables that declare a topk`() {
        client
            .get()
            .uri("/graph/v3/aggregations")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.topk[?(@.database == '$db' && @.table == '$sourceTable')]")
            .exists()
            .jsonPath("$.topk[?(@.database == '$db' && @.table == '$plainTable')]")
            .doesNotExist()
    }

    // region fixtures

    private fun createDatabase(database: String) {
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$database", "comment": "test"}""")
            .exchange()
            .expectStatus()
            .isOk
    }

    private fun createMultiEdgeSourceTable() {
        val group =
            """
            {
              "group": "purchased_count",
              "type": "COUNT",
              "fields": [{"name": "_target"}],
              "directionType": "OUT",
              "aggregations": {
                "topk": [{
                  "topk": "top_purchased",
                  "ranges": "_target:eq:{_target}",
                  "expire": false,
                  "table": {"score": "$scoreFqn", "expire": "expire_tbl"}
                }]
              }
            }
            """.trimIndent()
        client
            .post()
            .uri("/graph/v3/databases/$db/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "$sourceTable",
                  "schema": {
                    "type": "MULTI_EDGE",
                    "id": {"type": "long", "comment": "purchase id"},
                    "source": {"type": "long", "comment": "user"},
                    "target": {"type": "long", "comment": "item"},
                    "properties": [],
                    "direction": "BOTH",
                    "indexes": [],
                    "groups": [$group],
                    "caches": []
                  },
                  "storage": "datastore://test_namespace/$sourceTable",
                  "mode": "SYNC",
                  "comment": "purchases"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }

    private fun createEdgeTable(
        database: String,
        table: String,
        propertiesJson: String,
        groupsJson: String,
    ) {
        client
            .post()
            .uri("/graph/v3/databases/$database/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "$table",
                  "schema": {
                    "type": "EDGE",
                    "source": {"type": "long", "comment": "src"},
                    "target": {"type": "long", "comment": "tgt"},
                    "properties": [$propertiesJson],
                    "direction": "BOTH",
                    "indexes": [],
                    "groups": [$groupsJson],
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
    }

    // endregion
}
