package com.kakao.actionbase.v2.engine.wal

import com.kakao.actionbase.v2.core.edge.TraceEdge
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.core.metadata.MutationMode
import com.kakao.actionbase.v2.engine.audit.Audit
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.metadata.MutationModeContext
import com.kakao.actionbase.v2.engine.producer.Log

import com.fasterxml.jackson.annotation.JsonGetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

data class WalLog(
    @JsonIgnore
    val alias: EntityName?,
    @JsonIgnore
    val label: EntityName,
    val edge: TraceEdge,
    val op: EdgeOperation,
    val mode: MutationModeContext = MutationModeContext.of(table = MutationMode.SYNC, request = null),
    val audit: Audit = Audit.default,
    val requestId: String = "",
) : Log {
    val ts: Long = System.currentTimeMillis()

    @get:JsonGetter("alias")
    val aliasName: String?
        get() = alias?.fullQualifiedName

    @get:JsonGetter("label")
    val labelName: String
        get() = label.fullQualifiedName

    private fun computeKey(): String = "${edge.src}:${edge.tgt}"

    override fun toJsonString(): Pair<String?, String> = computeKey() to mapper.writeValueAsString(this)

    override fun toJsonBytes(): Pair<ByteArray?, ByteArray> = computeKey().toByteArray() to mapper.writeValueAsBytes(this)

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

    @get:JsonGetter("version")
    val version: String
        get() = Companion.version

    @get:JsonGetter("phase")
    val phase: String
        get() = Companion.phase

    @get:JsonGetter("tenant")
    val tenant: String
        get() = Companion.tenant
}
