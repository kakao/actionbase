package com.kakao.actionbase.server.configuration

import com.kakao.actionbase.v2.core.metadata.MutationMode
import com.kakao.actionbase.v2.engine.entity.DefaultStorageEntity
import com.kakao.actionbase.v2.engine.warmup.WarmUpConfig

import java.time.Duration

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "kc.graph")
data class GraphProperties(
    val phase: String?,
    val tenant: String?,
    val metastoreUrl: String?,
    val metastoreUser: String?,
    val metastorePassword: String?,
    val metastoreDriver: String?,
    val metastoreTable: String?,
    val metastoreReloadInterval: Duration?,
    val metastoreConnectionPoolSize: Int?,
    val defaultStorage: DefaultStorageEntity?,
    val wal: List<Map<String, String>>?,
    val cdc: List<Map<String, String>>?,
    val production: Boolean = false,
    val metastoreReloadInitialDelay: Duration = Duration.ZERO,
    val useToken: Boolean = true,
    val tokens: Set<String> = emptySet(),
    val encoderPoolSize: Int?,
    val warmUp: WarmUpConfig = WarmUpConfig(),
    val allowMirror: Boolean = false,
    val mutationRequestTimeout: Long?,
    val hbase: Map<String, String> = emptyMap(),
    val metadataFetchLimit: Int?,
    val systemMutationMode: MutationMode? = null,
)
