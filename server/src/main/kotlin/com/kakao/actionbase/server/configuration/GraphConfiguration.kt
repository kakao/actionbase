package com.kakao.actionbase.server.configuration

import com.kakao.actionbase.engine.service.MutationService
import com.kakao.actionbase.engine.service.QueryService
import com.kakao.actionbase.server.client.kafka.SpringKafkaClientFactory
import com.kakao.actionbase.server.client.web.SpringWebClientFactory
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.GraphConfig
import com.kakao.actionbase.v2.engine.cdc.CdcFactory
import com.kakao.actionbase.v2.engine.cdc.DefaultCdcFactory
import com.kakao.actionbase.v2.engine.client.kafka.KafkaClientFactory
import com.kakao.actionbase.v2.engine.client.web.WebClientFactory
import com.kakao.actionbase.v2.engine.metastore.MetastoreInspector
import com.kakao.actionbase.v2.engine.util.getLogger
import com.kakao.actionbase.v2.engine.v3.V2BackedEngine
import com.kakao.actionbase.v2.engine.wal.DefaultWalFactory
import com.kakao.actionbase.v2.engine.wal.WalFactory

import java.util.Properties

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.info.InfoEndpoint
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GraphConfiguration {
    private val logger = getLogger()

    @Value("\${metastoreUser:#{null}}")
    var metastoreUser: String? = null

    @Value("\${metastorePassword:#{null}}")
    var metastorePassword: String? = null

    @Bean
    @Suppress("CyclomaticComplexMethod")
    fun provideGraphConfig(
        properties: GraphProperties,
        serverProperties: ServerProperties,
        infoEndpoint: InfoEndpoint,
    ): GraphConfig {
        val builder =
            GraphConfig.builder.apply {
                properties.phase?.let { withPhase(it) }
                properties.tenant?.let { withTenant(it) }
                properties.metastoreUrl?.let { withMetastoreUrl(it) }
                metastoreUser?.let { withMetastoreUser(it) } ?: properties.metastoreUser?.let { withMetastoreUser(it) }
                metastorePassword?.let { withMetastorePassword(it) } ?: properties.metastorePassword?.let { withMetastorePassword(it) }
                properties.metastoreDriver?.let { withMetastoreDriver(it) }
                properties.metastoreTable?.let { withMetastoreTable(it) }
                properties.metastoreReloadInterval?.let { withMetastoreReloadInterval(it) }
                properties.metastoreConnectionPoolSize?.let { withMetastoreConnectionPoolSize(it) }
                properties.encoderPoolSize?.let { withEncoderPoolSize(it) }
                properties.defaultStorage?.let { withDefaultStorageEntity(it) }
                withMetastoreReloadInitialDelay(properties.metastoreReloadInitialDelay)
                properties.wal?.let {
                    val walPropertiesList =
                        it.map { map ->
                            val walProperties = Properties()
                            walProperties.putAll(map)
                            walProperties
                        }
                    withWalProperties(walPropertiesList)
                }
                properties.cdc?.let {
                    val cdcPropertiesList =
                        it.map { map ->
                            val cdcProperties = Properties()
                            cdcProperties.putAll(map)
                            cdcProperties
                        }
                    withCdcProperties(cdcPropertiesList)
                }
                withWarmUp(properties.warmUp)
                properties.mutationRequestTimeout?.let {
                    withMutationRequestTimeout(properties.mutationRequestTimeout)
                }
                val artifactInfo =
                    ArtifactInfo
                        .of(infoEndpoint)
                withArtifactInfo(artifactInfo.toString())
                withHBase(properties.hbase)
                properties.metadataFetchLimit?.let { withMetadataFetchLimit(it) }
                properties.systemMutationMode?.let { withSystemMutationMode(it) }
                withReadOnly(serverProperties.readOnly)
            }
        return builder.build()
    }

    @Bean
    fun provideWebClientFactory(): WebClientFactory = SpringWebClientFactory

    @Bean
    fun provideKafkaClientFactory(): KafkaClientFactory = SpringKafkaClientFactory

    @Bean
    fun provideWalFactory(): WalFactory = DefaultWalFactory

    @Bean
    fun provideCdcFactory(): CdcFactory = DefaultCdcFactory

    @Bean
    fun provideGraph(
        config: GraphConfig,
        walFactory: WalFactory,
        cdcFactory: CdcFactory,
        kafkaClientFactory: KafkaClientFactory,
        webClientFactory: WebClientFactory,
    ): Graph {
        val graph =
            Graph.create(
                config,
                walFactory,
                cdcFactory,
                kafkaClientFactory,
                webClientFactory,
            )

        //  Is blocking OK?
        while (!graph.isReady()) {
            logger.info("Waiting for graph to be ready")
            Thread.sleep(1000)
        }

        return graph
    }

    @Bean("metastoreInspector")
    fun provideMetastoreInspector(graph: Graph): MetastoreInspector = MetastoreInspector.createGlobal(graph)

    @Bean("localMetastoreInspector")
    fun provideLocalMetastoreInspector(graph: Graph): MetastoreInspector = MetastoreInspector.createLocal(graph)

    @Bean
    fun provideV2BackedEngine(graph: Graph): V2BackedEngine = V2BackedEngine(graph)

    @Bean
    fun provideQueryService(engine: V2BackedEngine): QueryService = QueryService(engine)

    @Bean
    fun provideMutationService(engine: V2BackedEngine): MutationService = MutationService(engine)
}
