package com.kakao.actionbase.v2.engine

import com.kakao.actionbase.v2.core.metadata.MutationMode
import com.kakao.actionbase.v2.engine.entity.DefaultStorageEntity
import com.kakao.actionbase.v2.engine.service.ddl.DdlService
import com.kakao.actionbase.v2.engine.warmup.WarmUpConfig

import java.net.InetAddress
import java.time.Duration
import java.util.Properties
import java.util.UUID

data class GraphConfig(
    val phase: String,
    val tenant: String,
    val metastoreUrl: String,
    val metastoreUser: String,
    val metastorePassword: String,
    val metastoreDriver: String?,
    val metastoreTable: String?,
    val metastoreReloadInitialDelay: Duration?,
    val metastoreReloadInterval: Duration?,
    val metastoreConnectionPoolSize: Int,
    val defaultStorageEntity: DefaultStorageEntity?,
    val walProperties: List<Properties>,
    val cdcProperties: List<Properties>,
    val topNProperties: Properties,
    val encoderPoolSize: Int,
    val lockTimeout: Long,
    val hostName: String,
    val warmUp: WarmUpConfig,
    val artifactInfo: String?,
    // Aligned with nginx.conf proxy_read_timeout 300
    val mutationRequestTimeout: Long = 300_000,
    val hbase: Map<String, String> = emptyMap(),
    val metadataFetchLimit: Int = DdlService.DEFAULT_METADATA_LIMIT,
    val systemMutationMode: MutationMode? = null,
) {
    companion object {
        val builder: Builder
            get() = Builder()
    }

    class Builder {
        private var phase: String = "local"
        private var tenant: String = "local"
        private var metastoreUrl: String = "jdbc:h2:mem:test-${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=MYSQL"
        private var metastoreUser: String = ""
        private var metastorePassword: String = ""
        private var metastoreDriver: String? = null
        private var metastoreTable: String? = null
        private var defaultStorageEntity: DefaultStorageEntity? = null
        private var walProperties: List<Properties> = listOf(Properties())
        private var cdcProperties: List<Properties> = listOf(Properties())
        private var topNProperties: Properties = Properties()
        private var metastoreReloadInitialDelay: Duration? = null
        private var metastoreReloadInterval: Duration? = null
        private var metastoreConnectionPoolSize: Int = 10
        private var encoderPoolSize: Int = 0
        private var lockTimeout: Long = 500L
        private var hostName: String = InetAddress.getLocalHost().hostName ?: "localhost"
        private var warmUp: WarmUpConfig = WarmUpConfig()
        private var artifactInfo: String? = null
        private var hbase: Map<String, String> = emptyMap()
        private var metadataFetchLimit: Int = DdlService.DEFAULT_METADATA_LIMIT
        private var systemMutationMode: MutationMode? = null

        // Aligned with nginx.conf proxy_read_timeout 300
        private var mutationRequestTimeout: Long = 300_000

        fun withPhase(phase: String) = apply { this.phase = phase }

        fun withTenant(tenant: String) = apply { this.tenant = tenant }

        fun withMetastoreUrl(url: String) = apply { this.metastoreUrl = url }

        fun withMetastoreUser(user: String) = apply { this.metastoreUser = user }

        fun withMetastorePassword(password: String) = apply { this.metastorePassword = password }

        fun withDefaultStorageEntity(entity: DefaultStorageEntity) =
            apply {
                this.defaultStorageEntity = entity
            }

        fun withWalProperties(properties: List<Properties>) = apply { this.walProperties = properties }

        fun withCdcProperties(properties: List<Properties>) = apply { this.cdcProperties = properties }

        fun withTopNProperties(properties: Properties) = apply { this.topNProperties = properties }

        fun withMetastoreDriver(driver: String?) = apply { this.metastoreDriver = driver }

        fun withMetastoreTable(table: String?) = apply { this.metastoreTable = table }

        fun withMetastoreReloadInitialDelay(delay: Duration?) = apply { this.metastoreReloadInitialDelay = delay }

        fun withMetastoreReloadInterval(interval: Duration?) = apply { this.metastoreReloadInterval = interval }

        fun withMetastoreConnectionPoolSize(size: Int) = apply { this.metastoreConnectionPoolSize = size }

        fun withEncoderPoolSize(size: Int) = apply { this.encoderPoolSize = size }

        fun withLockTimeout(timeout: Long) = apply { this.lockTimeout = timeout }

        fun withHostName(hostName: String) = apply { this.hostName = hostName }

        fun withWarmUp(warmUp: WarmUpConfig) = apply { this.warmUp = warmUp }

        fun withArtifactInfo(info: String) = apply { this.artifactInfo = info }

        fun withMutationRequestTimeout(mutationRequestTimeout: Long) =
            apply {
                this.mutationRequestTimeout = mutationRequestTimeout
            }

        fun withHBase(hbase: Map<String, String>) = apply { this.hbase = hbase }

        fun withMetadataFetchLimit(limit: Int) =
            apply {
                require(limit > 0) { "ddlFetchLimit must be positive, got $limit" }
                this.metadataFetchLimit = limit
            }

        fun withSystemMutationMode(systemMutationMode: MutationMode?) = apply { this.systemMutationMode = systemMutationMode }

        fun build(): GraphConfig =
            GraphConfig(
                phase = phase,
                tenant = tenant,
                metastoreUrl = metastoreUrl,
                metastoreUser = metastoreUser,
                metastorePassword = metastorePassword,
                metastoreDriver = metastoreDriver,
                metastoreTable = metastoreTable,
                metastoreReloadInitialDelay = metastoreReloadInitialDelay,
                metastoreReloadInterval = metastoreReloadInterval,
                metastoreConnectionPoolSize = metastoreConnectionPoolSize,
                walProperties = walProperties,
                cdcProperties = cdcProperties,
                topNProperties = topNProperties,
                encoderPoolSize = encoderPoolSize,
                lockTimeout = lockTimeout,
                hostName = hostName,
                warmUp = warmUp,
                defaultStorageEntity = defaultStorageEntity,
                artifactInfo = artifactInfo,
                mutationRequestTimeout = mutationRequestTimeout,
                hbase = hbase,
                metadataFetchLimit = metadataFetchLimit,
                systemMutationMode = systemMutationMode,
            )
    }
}
