package com.kakao.actionbase.core.metadata.common

object AggregationConstants {
    const val TOPK_DATABASE = "topk"
    const val TOPK_EXPIRE_TABLE = "expire"

    // score table src key: entity|topk_name — supports multiple topk in one table
    fun scoreSource(
        entity: String,
        topk: String,
    ): String = "$entity|$topk"

    fun expireSource(
        table: String,
        topk: String,
        entity: String,
        target: String,
    ): String = ("$table|$topk|$entity|$target".hashCode() / 2310).toString()

    fun expireTarget(
        table: String,
        topk: String,
        entity: String,
        target: String,
        expiresAt: Long,
    ): String = "$table|$topk|$entity|$target|$expiresAt"
}
