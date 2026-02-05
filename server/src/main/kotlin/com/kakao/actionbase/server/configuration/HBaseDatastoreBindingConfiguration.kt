package com.kakao.actionbase.server.configuration

import com.kakao.actionbase.engine.datastore.hbase.admin.HBaseAdmin
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.storage.DefaultStorageBackendFactory
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseStorageBackend

import org.apache.hadoop.hbase.NamespaceDescriptor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnHBaseDatastore
class HBaseDatastoreBindingConfiguration(
    // DefaultStorageBackendFactory is initialized in graph, so graph configuration must be completed before hbase admin injection is possible.
    private val graph: Graph,
) {
    @Bean
    fun hBaseAdmin(): HBaseAdmin {
        val backend = DefaultStorageBackendFactory.INSTANCE as? HBaseStorageBackend
            ?: throw IllegalStateException("HBaseAdmin requires HBaseStorageBackend but got ${DefaultStorageBackendFactory.INSTANCE::class.simpleName}")
        return HBaseAdmin(
            backend.connectionMono
                .map { it.admin }
                .cache(),
        )
    }

    @Bean
    fun namespaceDescriptor(serverProperties: ServerProperties): NamespaceDescriptor =
        serverProperties.datastore.configuration[ServerProperties.DatastoreProperties.CONFIG_KEY_ACTIONBASE_HBASE_NAMESPACE]
            ?.let { namespace ->
                NamespaceDescriptor.create(namespace).build()
            } ?: throw java.lang.IllegalArgumentException("Missing required configuration: ‘actionbase.hbase.namespace’ must be specified in the datastore configuration.")
}
