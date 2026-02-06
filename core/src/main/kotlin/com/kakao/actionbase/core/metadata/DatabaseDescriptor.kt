package com.kakao.actionbase.core.metadata

import com.kakao.actionbase.core.Constants
import com.kakao.actionbase.core.metadata.payload.DatabaseCreateRequest
import com.kakao.actionbase.core.metadata.payload.DatabaseUpdateRequest

import com.fasterxml.jackson.annotation.JsonIgnore

data class DatabaseDescriptor(
    override val tenant: String,
    val database: String,
    override val active: Boolean = true,
    override val comment: String = Constants.DEFAULT_COMMENT,
    override val revision: Long = Constants.DEFAULT_REVISION,
    override val createdAt: Long = Constants.DEFAULT_CREATED_AT,
    override val createdBy: String = Constants.DEFAULT_CREATED_BY,
    override val updatedAt: Long = Constants.DEFAULT_UPDATED_AT,
    override val updatedBy: String = Constants.DEFAULT_UPDATED_BY,
) : V3Descriptor<DatabaseId> {
    @JsonIgnore
    override val id: DatabaseId = DatabaseId(tenant, database)

    fun toCreateRequest(): DatabaseCreateRequest =
        DatabaseCreateRequest(
            database = database,
            comment = comment,
        )

    fun toUpdateRequest(): DatabaseUpdateRequest =
        DatabaseUpdateRequest(
            active = active,
            comment = comment,
        )
}
