package com.kakao.actionbase.server.configuration

import com.kakao.actionbase.core.metadata.DatastoreDescriptor
import com.kakao.actionbase.core.metadata.common.DatastoreType

import org.springframework.boot.context.properties.ConfigurationProperties

// NOTE: If DatastoreProperties is placed in a submodule, IntelliJ's application.yaml -> code navigation does not work.
//       To maintain the jump functionality in the IDE, a Spring-specific wrapper (ActionbaseSpringProperties) is placed in the main server module.
@ConfigurationProperties(prefix = "actionbase")
data class ServerProperties(
    val tenant: String,
    val datastore: DatastoreProperties,
    val readOnly: Boolean = false,
) {
    data class DatastoreProperties(
        val type: DatastoreType,
        val configuration: Map<String, String> = emptyMap(),
    ) {
        fun toDescriptor(): DatastoreDescriptor =
            DatastoreDescriptor(
                type = type,
                configuration = configuration,
            )

        companion object {
            const val CONFIG_KEY_ACTIONBASE_HBASE_NAMESPACE = "actionbase.hbase.namespace"
        }
    }
}
