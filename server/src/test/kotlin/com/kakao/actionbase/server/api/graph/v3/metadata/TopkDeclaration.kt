package com.kakao.actionbase.server.api.graph.v3.metadata

import com.kakao.actionbase.core.metadata.common.AggregationConstants.Topk.REFRESH_TABLE

const val TABLE = "purchases"
const val RANK = "purchases__topk"
const val RANK2 = "purchases__topk_moved"
const val TOPK = "top_purchased"
const val TOPK2 = "top_watched"
const val DAY_MILLIS = 24 * 60 * 60 * 1000L

/** When a test buys unless it says otherwise. Not on a bucket boundary, so the arithmetic has a remainder to drop. */
const val PURCHASED_AT = "2026-01-01T14:32:00Z"

/** Another bucket than [PURCHASED_AT], still well inside a 365-day window. */
const val PURCHASED_AT_LATER = "2026-01-11T14:32:00Z"

/** What a purchase at [PURCHASED_AT] queues its refresh for: its bucket's start, the window, one more bucket. */
const val REFRESH_AT = "2027-01-02T00:00:00Z"

/** One top-K declaration, and how it reads as the schema its table is created with. */
data class Declaration(
    val database: String,
    val format: String = "yyyy-MM-dd",
    val timezone: String = "UTC",
    val window: String? = "purchasedAt:bt:now-365d,now",
    val refreshAfterMillis: Long = 365 * DAY_MILLIS,
    val refreshQueue: String = REFRESH_TABLE,
    val bucketed: Boolean = true,
    val direction: String = "OUT",
    val entity: String = "source",
    val dimension: String = "target",
    val split: String? = "category",
    val secondTopk: Boolean = false,
    val secondEntity: String? = null,
    val secondGroup: Boolean = false,
    val declared: Boolean = true,
    val indexedRank: Boolean = true,
    val rank: String = RANK,
) {
    private val pinField: String get() = if (dimension == "brand" || dimension.startsWith("_")) dimension else "_$dimension"

    /** The endpoint a direction already implies (`target` for `IN`, `source` for `OUT`) is not declared again. */
    private val endpoint: String get() = if (direction == "IN") "_target" else "_source"

    fun groupFields(): String =
        listOfNotNull(
            """{"name": "$pinField"}""".takeIf { pinField != endpoint },
            split?.let { """{"name": "$it"}""" },
            """{"name": "purchasedAt", "bucket": {"type": "date", "name": "purchasedAt", "unit": "MILLISECOND",
               "timezone": "$timezone", "format": "$format"}}""".takeIf { bucketed },
        ).joinToString(",")

    fun topks(name: String = TOPK): String =
        listOfNotNull(
            topk(name, entity),
            topk(TOPK2, secondEntity ?: entity).takeIf { secondTopk || secondEntity != null },
        ).joinToString(",")

    fun topk(
        name: String,
        entity: String,
    ): String =
        """
        {"topk": "$name", "entity": "$entity", "dimension": "$dimension",
         "ranges": "${ranges()}", "refreshAfterMillis": $refreshAfterMillis,
         "refreshQueue": "$refreshQueue",
         "rank": "$database.$rank", "additionalProperties": ["category"]}
        """.trimIndent()

    /** A second group on the same table so a row can watch one purchase land in two rankings. */
    fun groups(): String =
        listOfNotNull(
            """
            {"group": "purchased_count", "type": "COUNT", "fields": [${groupFields()}],
             "directionType": "$direction", "aggregations": {"topk": [${if (declared) topks() else ""}]}}
            """.trimIndent(),
            """
            {"group": "purchased_total", "type": "COUNT",
             "fields": [{"name": "$pinField"}, {"name": "purchasedAt",
              "bucket": {"type": "date", "name": "purchasedAt", "unit": "MILLISECOND",
                         "timezone": "$timezone", "format": "$format"}}],
             "directionType": "$direction",
             "aggregations": {"topk": [{"topk": "$TOPK2", "entity": "$entity", "dimension": "$dimension",
              "ranges": "$pinField:eq:{$pinField};$window",
              "refreshAfterMillis": $refreshAfterMillis, "rank": "$database.$RANK",
              "additionalProperties": []}]}}
            """.trimIndent().takeIf { secondGroup },
        ).joinToString(",")

    fun ranges(): String =
        listOfNotNull(
            "$pinField:eq:{$pinField}".takeIf { pinField != endpoint },
            split?.let { "$it:eq:{$it}" },
            window,
        ).joinToString(";")
}

/**
 * A day bucket in UTC, a 365-day `now` window, ranked by item and split by category. A test that needs
 * another shape copies this one where it uses it.
 */
val DAY = Declaration(database = "topk_day")

/**
 * The declarations a table-driven test runs over. They are named here rather than built where they are used
 * because a `@TableSource` cell carries one, and a cell can name an enum but cannot hold a value.
 */
enum class Preset(
    val declaration: Declaration,
) {
    PER_ENTITY_BY_HOUR(
        DAY.copy(
            database = "topk_hour",
            format = "yyyy-MM-dd HH",
            window = "purchasedAt:bt:now-24h,now",
            refreshAfterMillis = DAY_MILLIS,
        ),
    ),
    IN_SEOUL(DAY.copy(database = "topk_seoul", timezone = "Asia/Seoul")),
    NO_SPLIT(DAY.copy(database = "topk_nodim", split = null)),
    BY_BRAND(DAY.copy(database = "topk_brand", dimension = "brand")),
    UNDERSCORED_DIMENSION(DAY.copy(database = "topk_underscore", dimension = "_target")),
    REFRESH_EVERY_DAY(DAY.copy(database = "topk_shortrefresh", refreshAfterMillis = DAY_MILLIS)),
    REFRESH_EVERY_TWO_YEARS(DAY.copy(database = "topk_longrefresh", refreshAfterMillis = 730 * DAY_MILLIS)),
}
