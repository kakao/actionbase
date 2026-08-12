package com.kakao.actionbase.engine.service

import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.sql.StatKey

import com.fasterxml.jackson.databind.JsonNode
import reactor.core.publisher.Mono

/**
 * A query is stored under an id the server assigns, and a name is kept separately and points at that id,
 * so that a name can be moved onto another query in one write and put back the same way.
 *
 * Running one goes through [QueryService] because that executor owns the transform cache.
 */
class PreparedQueryService(
    private val graph: Graph,
    private val queries: QueryService,
) {
    fun register(
        desc: String,
        arguments: List<StructField>,
        fetch: JsonNode,
        transform: JsonNode,
        stats: Set<StatKey> = emptySet(),
    ): Mono<PreparedQueryDescriptor> = TODO("Not yet implemented")

    fun amend(
        id: String,
        desc: String? = null,
        arguments: List<StructField>? = null,
        fetch: JsonNode? = null,
        transform: JsonNode? = null,
        stats: Set<StatKey>? = null,
    ): Mono<PreparedQueryDescriptor> = TODO("Not yet implemented")

    fun get(id: String): Mono<PreparedQueryDescriptor> = TODO("Not yet implemented")

    fun list(status: MetadataStatus = MetadataStatus.ACTIVE): Mono<List<PreparedQueryDescriptor>> = TODO("Not yet implemented")

    fun delete(id: String): Mono<Void> = TODO("Not yet implemented")

    fun createAlias(
        alias: String,
        desc: String,
        target: String,
    ): Mono<PreparedQueryAliasDescriptor> = TODO("Not yet implemented")

    fun updateAlias(
        alias: String,
        desc: String? = null,
        target: String? = null,
        active: Boolean? = null,
    ): Mono<PreparedQueryAliasDescriptor> = TODO("Not yet implemented")

    fun alias(alias: String): Mono<PreparedQueryAliasDescriptor> = TODO("Not yet implemented")

    fun aliases(status: MetadataStatus = MetadataStatus.ACTIVE): Mono<List<PreparedQueryAliasDescriptor>> = TODO("Not yet implemented")

    fun deleteAlias(alias: String): Mono<Void> = TODO("Not yet implemented")

    fun query(
        id: String,
        arguments: Map<String, Any>,
    ): Mono<Map<String, DataFrame>> = TODO("Not yet implemented")
}

/** Named separately so that a 404 does not follow from every missing map key in the engine. */
class NoSuchPreparedQueryException(
    id: String,
) : NoSuchElementException("No prepared query `$id`.")

enum class MetadataStatus {
    ACTIVE,
    INACTIVE,
    ALL,
    ;

    fun matches(active: Boolean): Boolean =
        when (this) {
            ACTIVE -> active
            INACTIVE -> !active
            ALL -> true
        }
}

data class PreparedQueryDescriptor(
    val id: String,
    val arguments: List<StructField>,
    val fetch: JsonNode,
    val transform: JsonNode,
    val stats: Set<StatKey>,
    val active: Boolean,
    val desc: String,
)

data class PreparedQueryAliasDescriptor(
    val alias: String,
    val target: String,
    val active: Boolean,
    val desc: String,
)
