package com.kakao.actionbase.server.api.graph.v3.query

import com.kakao.actionbase.core.Constants
import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.v2.engine.sql.StatKey

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory

data class QueryArgumentRequest(
    @field:NotBlank(message = "argument name is required")
    val name: String,
    val type: PrimitiveType,
    @field:Size(max = Constants.Name.COMMENT_MAX_LENGTH, message = Constants.Name.COMMENT_SIZE_MESSAGE)
    val comment: String = "",
) {
    fun toStructField(): StructField = StructField(name = name, type = type, comment = comment, nullable = false)
}

data class PreparedQueryCreateRequest(
    @field:Size(max = Constants.Name.COMMENT_MAX_LENGTH, message = Constants.Name.COMMENT_SIZE_MESSAGE)
    val comment: String = "",
    val arguments: List<QueryArgumentRequest> = emptyList(),
    val fetch: JsonNode = JsonNodeFactory.instance.arrayNode(),
    val transform: JsonNode = JsonNodeFactory.instance.arrayNode(),
    val stats: Set<StatKey> = emptySet(),
)

data class PreparedQueryUpdateRequest(
    val active: Boolean? = null,
    @field:Size(max = Constants.Name.COMMENT_MAX_LENGTH, message = Constants.Name.COMMENT_SIZE_MESSAGE)
    val comment: String? = null,
    val arguments: List<QueryArgumentRequest>? = null,
    val fetch: JsonNode? = null,
    val transform: JsonNode? = null,
    val stats: Set<StatKey>? = null,
)

data class PreparedQueryAliasCreateRequest(
    @field:NotBlank(message = "alias is required")
    @field:Pattern(regexp = Constants.Name.PATTERN, message = Constants.Name.MESSAGE)
    val alias: String,
    @field:NotBlank(message = "target is required")
    val target: String,
    @field:Size(max = Constants.Name.COMMENT_MAX_LENGTH, message = Constants.Name.COMMENT_SIZE_MESSAGE)
    val comment: String = "",
)

data class PreparedQueryAliasUpdateRequest(
    val active: Boolean? = null,
    @field:Size(max = Constants.Name.COMMENT_MAX_LENGTH, message = Constants.Name.COMMENT_SIZE_MESSAGE)
    val comment: String? = null,
    val target: String? = null,
)

data class PreparedQueryExecuteRequest(
    val arguments: Map<String, Any> = emptyMap(),
)

/** `arguments` carries values rather than a declaration, so a placeholder takes the type it was sent as. */
data class QueryExecuteRequest(
    val arguments: Map<String, Any> = emptyMap(),
    val fetch: JsonNode = JsonNodeFactory.instance.arrayNode(),
    val transform: JsonNode = JsonNodeFactory.instance.arrayNode(),
    val stats: Set<StatKey> = emptySet(),
)

data class PreparedQueryResponse(
    val tenant: String,
    val id: String,
    val arguments: List<StructField>,
    val fetch: JsonNode,
    val transform: JsonNode,
    val stats: Set<StatKey>,
    val active: Boolean = true,
    val comment: String = Constants.DEFAULT_COMMENT,
    val revision: Long = Constants.DEFAULT_REVISION,
    val createdAt: Long = Constants.DEFAULT_CREATED_AT,
    val createdBy: String = Constants.DEFAULT_CREATED_BY,
    val updatedAt: Long = Constants.DEFAULT_UPDATED_AT,
    val updatedBy: String = Constants.DEFAULT_UPDATED_BY,
)

data class PreparedQueryAliasResponse(
    val tenant: String,
    val alias: String,
    val target: String,
    val active: Boolean = true,
    val comment: String = Constants.DEFAULT_COMMENT,
    val revision: Long = Constants.DEFAULT_REVISION,
    val createdAt: Long = Constants.DEFAULT_CREATED_AT,
    val createdBy: String = Constants.DEFAULT_CREATED_BY,
    val updatedAt: Long = Constants.DEFAULT_UPDATED_AT,
    val updatedBy: String = Constants.DEFAULT_UPDATED_BY,
)
