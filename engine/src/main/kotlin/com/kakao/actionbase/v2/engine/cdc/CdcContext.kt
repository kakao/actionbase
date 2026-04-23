package com.kakao.actionbase.v2.engine.cdc

import com.kakao.actionbase.core.storage.StorageOp
import com.kakao.actionbase.v2.core.edge.TraceEdge
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.engine.audit.Audit
import com.kakao.actionbase.v2.engine.edge.HashEdge
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.label.EdgeOperationStatus
import com.kakao.actionbase.v2.engine.producer.Log

import com.fasterxml.jackson.annotation.JsonGetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

data class CdcContext(
    @JsonIgnore
    val label: EntityName,
    val edge: TraceEdge,
    val op: EdgeOperation,
    val status: EdgeOperationStatus,
    val before: HashEdge?,
    val after: HashEdge?,
    val acc: Long,
    @JsonIgnore
    val alias: EntityName? = null,
    @JsonIgnore
    val deferredRequests: List<Any> = emptyList(),
    val message: String? = null,
    val audit: Audit = Audit.default,
    val requestId: String = "",
    @JsonIgnore
    val storageOps: List<StorageOp>? = null,
    @JsonIgnore
    val storageOpsTruncated: Boolean = false,
) : Log {
    @Suppress("PropertyName")
    companion object {
        private val mapper = jacksonObjectMapper()

        private const val version = "2"

        private lateinit var phase: String

        private lateinit var tenant: String

        fun initialize(
            phase: String,
            tenant: String,
        ) {
            this.phase = phase
            this.tenant = tenant
        }
    }

    val ts = System.currentTimeMillis()

    @get:JsonGetter("label")
    val labelName: String
        get() = label.fullQualifiedName

    @get:JsonGetter("alias")
    val aliasName: String?
        get() = alias?.fullQualifiedName

    @get:JsonGetter("version")
    val version: String
        get() = Companion.version

    @get:JsonGetter("phase")
    val phase: String
        get() = Companion.phase

    @get:JsonGetter("tenant")
    val tenant: String
        get() = Companion.tenant

    private fun computeKey(): String = "${edge.src}:${edge.tgt}"

    override fun toJsonString(): Pair<String?, String> = computeKey() to mapper.writeValueAsString(this)

    override fun toJsonBytes(): Pair<ByteArray?, ByteArray> = computeKey().toByteArray() to mapper.writeValueAsBytes(this)
}
