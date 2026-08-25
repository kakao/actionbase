package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.edge.EdgeField
import com.kakao.actionbase.core.edge.payload.AggregationsSweepResponse
import com.kakao.actionbase.core.edge.payload.AggregationsTopkResponse
import com.kakao.actionbase.core.edge.payload.DataFrameEdgePayload
import com.kakao.actionbase.core.metadata.common.AggregationConstants
import com.kakao.actionbase.core.metadata.common.AggregationConstants.Topk.DATABASE
import com.kakao.actionbase.core.metadata.common.AggregationConstants.Topk.REFRESH_TABLE
import com.kakao.actionbase.engine.queue.PollResponse
import com.kakao.actionbase.engine.queue.PolledMessage
import com.kakao.actionbase.test.MutableClock
import com.kakao.actionbase.v2.engine.sql.ScanFilter

import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

/** Named for what it carries rather than for the field it fills, so no dimension can shadow it. */
const val DIMENSION_VALUE = "dimensionValue"

/**
 * Drives a top-K through HTTP the way the outside world does: write an edge, ask for the aggregation,
 * move the clock, sweep, read the ranking back.
 *
 * Two of those go through the surface a caller would use rather than a shortcut. A ranking is read with a
 * prepared query, which is the only read this service offers, and a refresh sweeps the message the producer
 * queued rather than one built here.
 */
class TopkFixture(
    private val client: WebTestClient,
    private val clock: MutableClock,
) {
    /** Both scenario classes import this, so they share one Spring context instead of starting two. */
    @TestConfiguration
    class MovableClock {
        @Bean
        fun clock(): Clock = MutableClock(START)
    }

    fun now(at: String) {
        clock.setTo(Instant.parse(at))
    }

    /** A purchase at [at], recorded and then aggregated the way a CDC consumer would. */
    fun buy(
        entity: String,
        dimension: String,
        dimensionValues: String? = "fruit",
        at: String,
        brand: String = "",
        declaration: Declaration = DAY,
    ): Long {
        val id = NEXT_ID.getAndIncrement()

        now(at)
        purchase(entity, PurchaseValues(dimension, dimensionValues.orEmpty(), brand), id, declaration)

        return id
    }

    /** Both purchases in one request, so both recompute the same ranking at the same time. */
    fun buyTwiceInOneRequest(
        entity: String,
        dimension: String,
        at: String,
        declaration: Declaration = DAY,
    ) {
        now(at)

        val values = PurchaseValues(dimension, "fruit", "")
        val edges = (1..2).map { edge(entity, values, NEXT_ID.getAndIncrement()) }

        mutate(edges.joinToString(",") { """{"type": "INSERT", "edge": $it}""" }, declaration)
        client
            .post()
            .uri("/aggregations/v1/aggregate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "items": [
                    ${
                    edges.joinToString(",") {
                        """
                        {
                            "database": "${declaration.database}",
                            "table": "$TABLE",
                            "edge": {
                                "version": ${clock.millis()},
                                "source": "$entity",
                                "target": "$dimension",
                                "properties": {
                                    "category": "fruit",
                                    "brand": "",
                                    "purchasedAt": ${clock.millis()}},
                                    "context": {}
                            }
                        }
                        """.trimIndent()
                    }
                }]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }

    /** A cancel has to out-version the purchase it undoes, or the state machine ignores it. */
    fun cancel(
        entity: String,
        dimension: String,
        id: Long,
        dimensionValues: String = "fruit",
        declaration: Declaration = DAY,
    ) {
        mutate(
            """
            {"type": "DELETE", "edge": {"version": ${clock.millis() + NEXT_ID.getAndIncrement()}, "id": $id}}
            """.trimIndent(),
            declaration,
        )
        aggregate(entity, PurchaseValues(dimension, dimensionValues, ""), declaration)
    }

    /** Null when nobody ever wrote that row. A row holding zero is not null. */
    fun metric(
        entity: String,
        dimension: String,
        dimensionValues: String? = "fruit",
        topk: String = TOPK,
        declaration: Declaration = DAY,
    ): Long? =
        read(entity, dimensionValues, declaration, topk)
            .topks
            .singleOrNull { it.value == dimension }
            ?.metric

    fun refreshAt(
        entity: String,
        declaration: Declaration = DAY,
    ): List<String> =
        refreshMessages(declaration, entity)
            .map { Instant.ofEpochMilli(it.seq).toString() }
            .sorted()

    fun createRefreshQueue() {
        createDatabase(DATABASE)
        client
            .post()
            .uri("/queue/v1/namespaces/$DATABASE/queues")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """{"queue": "$REFRESH_TABLE", "storage": "datastore://test_namespace/$REFRESH_TABLE", "partitions": $PARTITIONS}""",
            ).exchange()
    }

    private fun purchase(
        user: String,
        values: PurchaseValues,
        id: Long,
        declaration: Declaration,
    ) {
        mutate("""{"type": "INSERT", "edge": ${edge(user, values, id)}}""", declaration)
        aggregate(user, values, declaration)
    }

    private fun edge(
        user: String,
        values: PurchaseValues,
        id: Long,
    ): String =
        """
        {"version": ${clock.millis()}, "id": $id, "source": "$user", "target": "${values.target}",
         "properties": {"category": "${values.category}", "brand": "${values.brand}", "purchasedAt": ${clock.millis()}}}
        """.trimIndent()

    /**
     * Moves a purchase into another category, which is an edge update a CDC consumer then aggregates. The
     * version has to out-run the purchase it corrects and the clock has not moved, so it takes the next tick.
     */
    fun recategorize(
        id: Long,
        entity: String,
        dimension: String,
        to: String,
        declaration: Declaration = DAY,
    ) = update(
        id = id,
        entity = entity,
        dimension = dimension,
        version = clock.millis() + 1,
        properties = mapOf("category" to to, "purchasedAt" to clock.millis()),
        declaration = declaration,
    )

    /**
     * Corrects when a purchase happened, to [at]. Only `purchasedAt` is sent: a bucket value is not part of
     * the key, so nothing needs the value from before the edit.
     */
    fun correctPurchaseTime(
        id: Long,
        entity: String,
        dimension: String,
        at: String,
        declaration: Declaration = DAY,
    ) {
        now(at)
        update(
            id = id,
            entity = entity,
            dimension = dimension,
            version = clock.millis(),
            properties = mapOf("purchasedAt" to clock.millis()),
            declaration = declaration,
        )
    }

    /** Properties are serialized rather than spliced, so a value carrying a quote stays one value. */
    private fun update(
        id: Long,
        entity: String,
        dimension: String,
        version: Long,
        properties: Map<String, Any>,
        declaration: Declaration,
    ) {
        mutate(
            """
            {"type": "UPDATE", "edge": {"version": $version, "id": $id, "source": "$entity", "target": "$dimension",
             "properties": ${MAPPER.writeValueAsString(properties)}}}
            """.trimIndent(),
            declaration,
        )
    }

    private fun mutate(
        mutation: String,
        declaration: Declaration = DAY,
    ) {
        client
            .post()
            .uri("/graph/v3/databases/${declaration.database}/tables/$TABLE/multi-edges")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"mutations": [$mutation]}""")
            .exchange()
            .expectStatus()
            .isOk
    }

    /** Aggregates one purchase's values, which is what a CDC consumer does per event. */
    fun aggregate(
        entity: String,
        dimension: String,
        dimensionValues: String = "fruit",
        declaration: Declaration = DAY,
    ) = aggregate(entity, PurchaseValues(dimension, dimensionValues, ""), declaration)

    private fun aggregate(
        user: String,
        values: PurchaseValues,
        declaration: Declaration,
    ) {
        client
            .post()
            .uri("/aggregations/v1/aggregate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "items": [
                    {"database": "${declaration.database}", "table": "$TABLE",
                     "edge": {"version": ${clock.millis()}, "source": "$user", "target": "${values.target}",
                              "properties": {"category": "${values.category}", "brand": "${values.brand}",
                                             "purchasedAt": ${clock.millis()}}, "context": {}}}
                  ]
                }
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
    }

    fun refresh(
        entity: String,
        dimension: String,
        dimensionValues: String = "fruit",
        declaration: Declaration = DAY,
    ) {
        sweepResponse(entity, dimension, dimensionValues, declaration)
    }

    /** What a refresh reports back for one ranking, which is `SKIPPED` when nothing declares it any more. */
    fun sweepStatus(
        entity: String,
        dimension: String,
        dimensionValues: String = "fruit",
        declaration: Declaration = DAY,
    ): String =
        sweepResponse(entity, dimension, dimensionValues, declaration)
            .items
            .single()
            .status

    /**
     * Sweeps the message the producer queued rather than an item put together here. Building the item would
     * mean working out the key the producer worked out, which is the half of a refresh worth testing.
     */
    private fun sweepResponse(
        entity: String,
        dimension: String,
        dimensionValues: String,
        declaration: Declaration,
    ): AggregationsSweepResponse =
        client
            .post()
            .uri("/aggregations/v1/sweep")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"items": [${MAPPER.writeValueAsString(queuedRefresh(entity, dimension, dimensionValues, declaration))}]}""")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody<AggregationsSweepResponse>()
            .returnResult()
            .responseBody!!

    /**
     * A ranking recomputed more than once has one message per recompute, and they ask for the same thing,
     * so the first is as good as any.
     */
    private fun queuedRefresh(
        entity: String,
        dimension: String,
        dimensionValues: String,
        declaration: Declaration,
    ): Map<*, *> =
        refreshMessages(declaration, entity)
            .map { it.value as Map<*, *> }
            .first { message ->
                val item = message["item"] as Map<*, *>

                item["topk"] == TOPK &&
                    item["topkDimensionValue"] == dimension &&
                    item["dimensionValues"] == declaration.split?.let { dimensionValues }.orEmpty()
            }

    /** A declaration change is a full schema PUT: a partial one would wipe the parts it leaves out. */
    fun alter(declaration: Declaration) {
        client
            .put()
            .uri("/graph/v3/databases/${declaration.database}/tables/$TABLE")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"schema": ${sourceSchema(declaration)}}""")
            .exchange()
            .expectStatus()
            .isOk
    }

    /** The rank table read as a table, which is the only way to see rows no declaration points at any more. */
    fun rankRows(
        declaration: Declaration,
        entity: String,
        category: String,
    ): List<Long> =
        client
            .get()
            .uri(
                "/graph/v3/databases/${declaration.database}/tables/${declaration.rank}/edges/scan/${AggregationConstants.Topk.RANK_INDEX}" +
                    "?start=${AggregationConstants.Topk.rankSource(declaration.database, TABLE, TOPK, entity, listOf(category))}&direction=OUT",
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody<DataFrameEdgePayload>()
            .returnResult()
            .responseBody!!
            .edges
            .map { (it.properties[AggregationConstants.Topk.METRIC] as Number).toLong() }

    fun read(
        entity: String,
        category: String?,
        declaration: Declaration = DAY,
        topk: String = TOPK,
        limit: Int? = null,
    ): AggregationsTopkResponse {
        val split = declaration.split?.takeIf { category != null }

        return client
            .post()
            .uri("/graph/v3/query/${preparedRead(declaration, topk, split)}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {"arguments": {"entity": "$entity", "limit": ${limit ?: ScanFilter.defaultLimit}${
                    split?.let { """, "$DIMENSION_VALUE": "$category"""" } ?: ""
                }}}
                """.trimIndent(),
            ).exchange()
            .expectStatus()
            .isOk
            .expectBody<QueryRows>()
            .returnResult()
            .responseBody!!
            .toTopkResponse()
    }

    /**
     * A registration names the database, the table and the top-K; everything a caller picks per read is a
     * placeholder. A read that names no dimension value is a shape of its own, since a placeholder stands
     * for a whole value and never for a key.
     */
    fun preparedRead(
        declaration: Declaration,
        topk: String,
        split: String?,
    ): String =
        prepared.getOrPut(listOf(declaration.database, topk, split.orEmpty())) {
            client
                .post()
                .uri("/graph/v3/prepared-queries")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    """
                    {
                      "comment": "read the $topk ranking",
                      "arguments": [${
                        listOfNotNull(
                            """{"name": "entity", "type": "string", "comment": "whose ranking"}""",
                            """{"name": "limit", "type": "int", "comment": "how many ranked rows to read"}""",
                            """{"name": "$DIMENSION_VALUE", "type": "string", "comment": "the value the ranking is split on"}"""
                                .takeIf { split != null },
                        ).joinToString(",")
                    }],
                      "fetch": [
                        {
                          "type": "TOPK",
                          "name": "$RANKED",
                          "database": "${declaration.database}",
                          "table": "$TABLE",
                          "topk": "$topk",
                          "entity": {"type": "VALUE", "value": ["{entity}"]},
                          "dimensionValues": ${split?.let { """{"$it": "{$DIMENSION_VALUE}"}""" } ?: "{}"},
                          "limit": "{limit}",
                          "include": true
                        }
                      ]
                    }
                    """.trimIndent(),
                ).exchange()
                .expectStatus()
                .isOk
                .expectBody<RegisteredQuery>()
                .returnResult()
                .responseBody!!
                .id
        }

    /** The step's rows are the ranking table's, so a ranking is read out of the columns that table has. */
    private fun QueryRows.toTopkResponse(): AggregationsTopkResponse {
        val topks =
            items
                .single { it.name == RANKED }
                .data
                .map { row ->
                    AggregationsTopkResponse.TopkItem(
                        value = row[EdgeField.TARGET].toString(),
                        metric = (row[AggregationConstants.Topk.METRIC] as Number).toLong(),
                        properties =
                            (row[AggregationConstants.Topk.ADDITIONAL_PROPERTIES] as? String)
                                ?.let { MAPPER.readValue<Map<String, String>>(it) }
                                ?: emptyMap(),
                    )
                }

        return AggregationsTopkResponse(topks = topks, count = topks.size)
    }

    /** Polls every partition: which one a message lands on depends on its key, and the key differs per row. */
    fun refreshMessages(
        declaration: Declaration,
        user: String,
    ): List<PolledMessage> =
        (0 until PARTITIONS).flatMap { partition ->
            client
                .get()
                .uri("/queue/v1/namespaces/$DATABASE/queues/$REFRESH_TABLE/partitions/$partition/poll?limit=100")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody<PollResponse>()
                .returnResult()
                .responseBody!!
                .messages
                .filter { message ->
                    val refreshed = (message.value as Map<*, *>)["item"] as Map<*, *>
                    refreshed["database"] == declaration.database && refreshed["source"] == user
                }
        }

    /**
     * A declaration is created the first time a test declares it, on the database it names. Creating one
     * that another class already created is not a failure: they share a context, and whether it was there
     * already does not matter.
     */
    fun declare(declaration: Declaration): Declaration {
        if (created.add(declaration.database)) {
            createDatabase(declaration.database)
            createRankTable(declaration)
            createSourceTable(declaration)
        }

        return declaration
    }

    fun declare(preset: Preset): Declaration = declare(preset.declaration)

    fun createRankTable(declaration: Declaration) {
        client
            .post()
            .uri("/graph/v3/databases/${declaration.database}/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "${declaration.rank}",
                  "schema": {
                    "type": "EDGE",
                    "source": {"type": "string", "comment": "topk|entity|dimensionValues"},
                    "target": {"type": "string", "comment": "ranked value"},
                    "properties": [
                      {"name": "metric", "type": "long", "comment": "aggregated metric", "nullable": false},
                      {"name": "additionalProperties", "type": "string", "comment": "carried properties", "nullable": true}
                    ],
                    "direction": "OUT",
                    "indexes": [${if (declaration.indexedRank) """{"index": "metric_desc", "fields": [{"field": "metric", "order": "DESC"}]}""" else ""}],
                    "groups": [],
                    "caches": []
                  },
                  "storage": "datastore://test_namespace/${declaration.database}_${declaration.rank}",
                  "mode": "SYNC",
                  "comment": "materialized rankings"
                }
                """.trimIndent(),
            ).exchange()
    }

    private fun createDatabase(database: String) {
        client
            .post()
            .uri("/graph/v3/databases")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"database": "$database", "comment": "test"}""")
            .exchange()
    }

    private fun createSourceTable(declaration: Declaration) {
        client
            .post()
            .uri("/graph/v3/databases/${declaration.database}/tables")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "table": "$TABLE",
                  "schema": ${sourceSchema(declaration)},
                  "storage": "datastore://test_namespace/${declaration.database}_$TABLE",
                  "mode": "SYNC",
                  "comment": "user-to-item purchase edges"
                }
                """.trimIndent(),
            ).exchange()
    }

    private fun sourceSchema(declaration: Declaration): String =
        """
        {
          "type": "MULTI_EDGE",
          "id": {"type": "long", "comment": "purchase id"},
          "source": {"type": "string", "comment": "user"},
          "target": {"type": "string", "comment": "item"},
          "properties": [
            {"name": "category", "type": "string", "comment": "item category", "nullable": true},
            {"name": "brand", "type": "string", "comment": "item brand", "nullable": true},
            {"name": "purchasedAt", "type": "long", "comment": "purchase time ms", "nullable": false}
          ],
          "direction": "BOTH",
          "indexes": [],
          "groups": [${declaration.groups()}],
          "caches": []
        }
        """.trimIndent()

    private val created = mutableSetOf<String>()

    /** One registration per read shape, keyed by what a registration pins rather than by what it takes. */
    private val prepared = mutableMapOf<List<String>, String>()

    private companion object {
        /** `id` identifies the edge in a MULTI_EDGE table, and every test class writes to the same tables. */
        private val NEXT_ID = AtomicLong(1)

        private val MAPPER = jacksonObjectMapper()

        private const val RANKED = "ranked"

        private const val PARTITIONS = 4

        private val START = Instant.parse(PURCHASED_AT)
    }
}

/**
 * One purchase edge's values, named after the fields they are written to. Which of them a ranking is keyed
 * by is the declaration's decision, so none of them is called `dimension` here.
 */
internal data class PurchaseValues(
    val target: String,
    val category: String,
    val brand: String,
)

/** Only the id is read back off a registration: the rest of it is what was just sent. */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class RegisteredQuery(
    val id: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class QueryRows(
    val items: List<Item>,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Item(
        val name: String,
        val data: List<Map<String, Any?>>,
    )
}
