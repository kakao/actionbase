package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.metadata.common.TopKTableNames
import com.kakao.actionbase.server.test.E2ETestBase

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType

/**
 * End-to-end coverage for the topk aggregation endpoints.
 *
 * A single MULTI_EDGE source table (`purchases`) declares one group per case.
 * All groups target the same score table (`purchases__topk`); each topk carries a
 * distinct name so the rowkey `{database}.{table}:{topk}:{direction}:{entity}` in the
 * score table (see TopKTableNames.scoreSourceKey) keeps the ranked entries of each case
 * separated:
 *
 *   Case                              | Group                     | Topk name
 *   --------------------------------- | ------------------------- | -------------------
 *   1. Per-entity                     | purchased_count           | top_purchased
 *   2. Per-entity + segment           | purchased_by_segment      | top_purchased_seg
 *   3. Per-entity + window            | purchased_1y              | top_purchased_1y
 *   4. Per-entity + segment + window  | purchased_by_segment_1y   | top_purchased_seg_1y
 *
 * Score table (`purchases__topk`) after aggregation. Rowkey is
 * `{database}.{table}:{topk}:{direction}:{entity}` and the score_desc index sorts entries
 * by descending score so the topk read endpoint can scan the top rows directly:
 *
 *   rowkey                                        | score_desc cq | value  -> logical (source, target, score)
 *   --------------------------------------------- | ------------- | -----
 *   commerce.purchases:top_purchased:OUT:1        | 100           | 4      -> (1, 100, 4)
 *   commerce.purchases:top_purchased:OUT:1        | 200           | 1      -> (1, 200, 1)
 *   commerce.purchases:top_purchased_seg:OUT:1    | 100           | 3
 *   commerce.purchases:top_purchased_1y:OUT:1     | 100           | 3
 *   commerce.purchases:top_purchased_seg_1y:OUT:1 | 100           | 2
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MetadataAggControllerE2ETest : E2ETestBase() {
    private val db = "commerce"
    private val sourceTable = "purchases"
    private val plainTable = "misc"
    private val scoreTable = "${sourceTable}__topk"
    private val scoreFqn = "$db.$scoreTable"

    private val user = 1L
    private val itemA = 100L
    private val itemB = 200L

    private val nowMs = System.currentTimeMillis()
    private val outOfWindowMs = nowMs - 400L * 24 * 60 * 60 * 1000
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)

    @BeforeAll
    fun setup() {
        createDatabase(db)
        createDatabase(TopKTableNames.REFRESH_TABLE_DATABASE)

        createMultiEdgeSourceTable()
        createScoreTable(database = db, table = scoreTable)
        createRefreshTable(database = TopKTableNames.REFRESH_TABLE_DATABASE, table = TopKTableNames.REFRESH_TABLE_NAME)
        createEdgeTable(
            database = db,
            table = plainTable,
            propertiesJson = """{"name": "category", "type": "string", "comment": "cat", "nullable": true}""",
            groupsJson = """{"group": "by_target", "type": "COUNT", "fields": [{"name": "_target"}]}""",
        )

        insertPurchases()
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

    @Test
    fun `POST aggregation refresh accepts explicit entry body`() {
        client
            .post()
            .uri("/graph/v3/aggregations/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "entries": [{
                    "partition": 42,
                    "key": "$db.$sourceTable:missing_topk:OUT:$user:$itemA:61000",
                    "aggregation": {
                      "type": "TOPK",
                      "database": "$db",
                      "table": "$sourceTable",
                      "group": "purchased_count",
                      "topk": "missing_topk",
                      "direction": "OUT",
                      "edge": {
                        "version": 1000,
                        "source": $user,
                        "target": $itemA,
                        "properties": {},
                        "context": {}
                      }
                    }
                  }]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.items.length()")
            .isEqualTo(0)
    }

    @Test
    fun `GET aggregation refresh entries returns parsed entries for worker-owned partitions`() {
        insertRefreshEntry(partition = 42L, refreshAt = 61_000L)

        client
            .get()
            .uri(
                "/graph/v3/aggregations/refresh/entries" +
                    "?workerCount=10&workerNumber=${TopKTableNames.refreshWorkerNumberFor(42L, 10)}" +
                    "&refreshAtLte=61000&limit=100",
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.entries.length()")
            .isEqualTo(1)
            .jsonPath("$.entries[0].partition")
            .isEqualTo(42)
            .jsonPath("$.entries[0].key")
            .isEqualTo("$db.$sourceTable:top_purchased:OUT:$user:$itemA:61000")
            .jsonPath("$.entries[0].aggregation.database")
            .isEqualTo(db)
            .jsonPath("$.entries[0].aggregation.table")
            .isEqualTo(sourceTable)
            .jsonPath("$.entries[0].aggregation.topk")
            .isEqualTo("top_purchased")
    }

    @Test
    fun `case 1 per-entity ranks all targets regardless of segment or time`() {
        aggregate(target = itemA, gender = "M", age = 20, paidAt = nowMs)
        aggregate(target = itemB, gender = "M", age = 20, paidAt = nowMs)

        readTopk("top_purchased")
            .jsonPath("$.count")
            .isEqualTo(2)
            .jsonPath("$.edges[0].target")
            .isEqualTo(itemA.toString())
            .jsonPath("$.edges[0].properties.score")
            .isEqualTo(4)
            .jsonPath("$.edges[1].target")
            .isEqualTo(itemB.toString())
            .jsonPath("$.edges[1].properties.score")
            .isEqualTo(1)
    }

    @Test
    fun `case 2 per-entity plus segment counts only matching gender and age`() {
        aggregate(target = itemA, gender = "M", age = 20, paidAt = nowMs)

        readTopk("top_purchased_seg")
            .jsonPath("$.edges[0].target")
            .isEqualTo(itemA.toString())
            .jsonPath("$.edges[0].properties.score")
            .isEqualTo(3)
    }

    @Test
    fun `case 3 per-entity plus window excludes rows outside the range`() {
        aggregate(target = itemA, gender = "M", age = 20, paidAt = nowMs)

        readTopk("top_purchased_1y")
            .jsonPath("$.edges[0].target")
            .isEqualTo(itemA.toString())
            .jsonPath("$.edges[0].properties.score")
            .isEqualTo(3)
    }

    @Test
    fun `case 4 per-entity plus segment and window applies both filters`() {
        aggregate(target = itemA, gender = "M", age = 20, paidAt = nowMs)

        readTopk("top_purchased_seg_1y")
            .jsonPath("$.edges[0].target")
            .isEqualTo(itemA.toString())
            .jsonPath("$.edges[0].properties.score")
            .isEqualTo(2)
    }

    // region topk definitions

    private fun perEntityGroup() =
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
              "table": {"score": "$scoreFqn"}
            }]
          }
        }
        """.trimIndent()

    private fun perEntitySegmentGroup() =
        """
        {
          "group": "purchased_by_segment",
          "type": "COUNT",
          "fields": [
            {"name": "gender"},
            {"name": "age"},
            {"name": "_target"}
          ],
          "directionType": "OUT",
          "aggregations": {
            "topk": [{
              "topk": "top_purchased_seg",
              "ranges": "gender:eq:{gender};age:eq:{age};_target:eq:{_target}",
              "table": {"score": "$scoreFqn"}
            }]
          }
        }
        """.trimIndent()

    private fun perEntityWindowGroup() =
        """
        {
          "group": "purchased_1y",
          "type": "COUNT",
          "fields": [
            {"name": "_target"},
            {
              "name": "paidAt",
              "bucket": {
                "type": "date", "name": "time",
                "unit": "MILLISECOND", "timezone": "+00:00", "format": "yyyy-MM-dd"
              }
            }
          ],
          "directionType": "OUT",
          "aggregations": {
            "topk": [{
              "topk": "top_purchased_1y",
              "ranges": "_target:eq:{_target};time:bt:now-365d,now",
              "table": {"score": "$scoreFqn"}
            }]
          }
        }
        """.trimIndent()

    private fun perEntitySegmentWindowGroup() =
        """
        {
          "group": "purchased_by_segment_1y",
          "type": "COUNT",
          "fields": [
            {"name": "gender"},
            {"name": "age"},
            {"name": "_target"},
            {
              "name": "paidAt",
              "bucket": {
                "type": "date", "name": "time",
                "unit": "MILLISECOND", "timezone": "+00:00", "format": "yyyy-MM-dd"
              }
            }
          ],
          "directionType": "OUT",
          "aggregations": {
            "topk": [{
              "topk": "top_purchased_seg_1y",
              "ranges": "gender:eq:{gender};age:eq:{age};_target:eq:{_target};time:bt:now-365d,now",
              "table": {"score": "$scoreFqn"}
            }]
          }
        }
        """.trimIndent()

    // endregion

    // region data setup

    /**
     * Source table (`purchases`, HBase-style rowkey). Purchases from user 1 land as
     * multi-edges keyed by `_id`. Segment (`gender`, `age`) and time (`paidAt`) are
     * stored as edge properties so each group can filter on them:
     *
     *   rowkey | _id | _target | gender | age | paidAt        -> case coverage
     *   ------ | --- | ------- | ------ | --- | ------        --------------------------
     *   1      | 1   | 100     | M      | 20  | now            -> in-window, matches seg
     *   1      | 2   | 100     | F      | 30  | now            -> in-window, off-seg
     *   1      | 3   | 100     | M      | 20  | now - 400d     -> out-of-window
     *   1      | 4   | 100     | M      | 20  | now            -> in-window, matches seg
     *   1      | 5   | 200     | M      | 20  | now            -> different target
     */
    private fun insertPurchases() {
        val edges =
            listOf(
                purchaseEdge(version = 1, id = 1, target = itemA, gender = "M", age = 20, paidAt = nowMs),
                purchaseEdge(version = 1, id = 2, target = itemA, gender = "F", age = 30, paidAt = nowMs),
                purchaseEdge(version = 1, id = 3, target = itemA, gender = "M", age = 20, paidAt = outOfWindowMs),
                purchaseEdge(version = 1, id = 4, target = itemA, gender = "M", age = 20, paidAt = nowMs),
                purchaseEdge(version = 1, id = 5, target = itemB, gender = "M", age = 20, paidAt = nowMs),
            ).joinToString(",")

        client
            .post()
            .uri("/graph/v3/databases/$db/tables/$sourceTable/multi-edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"mutations": [$edges]}""")
            .exchange()
            .expectStatus()
            .isOk
    }

    private fun purchaseEdge(
        version: Long,
        id: Long,
        target: Long,
        gender: String,
        age: Long,
        paidAt: Long,
    ): String =
        """
        {
          "type": "INSERT",
          "edge": {
            "version": $version,
            "id": $id,
            "source": $user,
            "target": $target,
            "properties": {"gender": "$gender", "age": $age, "paidAt": $paidAt}
          }
        }
        """.trimIndent()

    private fun insertRefreshEntry(
        partition: Long,
        refreshAt: Long,
    ) {
        val payload =
            """
            {
              "type": "TOPK",
              "database": "$db",
              "table": "$sourceTable",
              "group": "purchased_count",
              "topk": "top_purchased",
              "direction": "OUT",
              "edge": {
                "version": 1000,
                "source": $user,
                "target": $itemA,
                "properties": {},
                "context": {}
              }
            }
            """.trimIndent()

        client
            .post()
            .uri("/graph/v3/databases/${TopKTableNames.REFRESH_TABLE_DATABASE}/tables/${TopKTableNames.REFRESH_TABLE_NAME}/edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "mutations": [{
                    "type": "INSERT",
                    "edge": {
                      "version": 1,
                      "source": $partition,
                      "target": "$db.$sourceTable:top_purchased:OUT:$user:$itemA:$refreshAt",
                      "properties": {
                        "refreshAt": $refreshAt,
                        "payload": ${payload.toJsonString()}
                      }
                    }
                  }]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }

    private fun aggregate(
        target: Long,
        gender: String,
        age: Long,
        paidAt: Long,
    ) {
        val timeIso = dateFmt.format(Instant.ofEpochMilli(paidAt))
        client
            .post()
            .uri("/graph/v3/aggregations")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "type": "TOPK",
                  "items": [{
                    "database": "$db",
                    "table": "$sourceTable",
                    "edge": {
                      "version": 1,
                      "source": $user,
                      "target": $target,
                      "properties": {"gender": "$gender", "age": $age, "time": "$timeIso"},
                      "context": {}
                    }
                  }]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }

    // TODO: switch to `/edges/topk/{topk}` once the topk read endpoint lands (see follow-up PR).
    //       For now, scan the score table directly by the `score_desc` index using the
    //       `{database}.{table}:{topk}:{direction}:{entity}` rowkey convention
    //       (see TopKTableNames.scoreSourceKey).
    private fun readTopk(topk: String) =
        client
            .get()
            .uri("/graph/v3/databases/$db/tables/$scoreTable/edges/scan/score_desc?start=$db.$sourceTable:$topk:OUT:$user&direction=OUT&limit=10")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()

    // endregion

    // region table creation

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
        val groups =
            listOf(
                perEntityGroup(),
                perEntitySegmentGroup(),
                perEntityWindowGroup(),
                perEntitySegmentWindowGroup(),
            ).joinToString(",")
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
                    "properties": [
                      {"name": "gender", "type": "string", "comment": "gender", "nullable": true},
                      {"name": "age", "type": "long", "comment": "age", "nullable": true},
                      {"name": "paidAt", "type": "long", "comment": "paid at ms", "nullable": true}
                    ],
                    "direction": "BOTH",
                    "indexes": [],
                    "groups": [$groups],
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

    private fun createScoreTable(
        database: String,
        table: String,
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
                    "source": {"type": "string", "comment": "entity|topk"},
                    "target": {"type": "string", "comment": "ranked value"},
                    "properties": [
                      {"name": "score", "type": "long", "comment": "aggregated score", "nullable": false}
                    ],
                    "direction": "OUT",
                    "indexes": [
                      {"index": "score_desc", "fields": [{"field": "score", "order": "DESC"}]}
                    ],
                    "groups": [],
                    "caches": []
                  },
                  "storage": "datastore://test_namespace/$table",
                  "mode": "SYNC",
                  "comment": "topk score table"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }

    private fun createRefreshTable(
        database: String,
        table: String,
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
                    "source": {"type": "long", "comment": "refresh partition"},
                    "target": {"type": "string", "comment": "refresh target key"},
                    "properties": [
                      {"name": "refreshAt", "type": "long", "comment": "refresh at", "nullable": false},
                      {"name": "payload", "type": "string", "comment": "refresh payload", "nullable": false}
                    ],
                    "direction": "OUT",
                    "indexes": [
                      {"index": "refresh_at_asc", "fields": [{"field": "refreshAt", "order": "ASC"}]}
                    ],
                    "groups": [],
                    "caches": []
                  },
                  "storage": "datastore://test_namespace/$table",
                  "mode": "SYNC",
                  "comment": "topk refresh table"
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }

    // endregion
}

private fun String.toJsonString(): String =
    replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .let { "\"$it\"" }
