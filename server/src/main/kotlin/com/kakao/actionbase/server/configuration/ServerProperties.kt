package com.kakao.actionbase.server.configuration

import com.kakao.actionbase.core.Constants
import com.kakao.actionbase.core.metadata.DatastoreDescriptor
import com.kakao.actionbase.core.metadata.common.DatastoreType
import com.kakao.actionbase.core.metadata.features.TableFeature

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties

// NOTE: If DatastoreProperties is placed in a submodule, IntelliJ's application.yaml -> code navigation does not work.
//       To maintain the jump functionality in the IDE, a Spring-specific wrapper (ActionbaseSpringProperties) is placed in the main server module.
@ConfigurationProperties(prefix = "actionbase")
data class ServerProperties(
    val tenant: String,
    val datastore: DatastoreProperties,
    val readOnly: Boolean = false,
    val databaseLevelFeatures: List<DatabaseFeatures> = emptyList(),
) {
    private val featuresByDatabase: Map<String, Set<TableFeature>>

    init {
        val invalidNames = databaseLevelFeatures.map { it.database }.filterNot { it.matches(DATABASE_NAME_REGEX) }
        require(invalidNames.isEmpty()) {
            "Invalid database names in actionbase.database-level-features: $invalidNames (must match ${Constants.Name.PATTERN})"
        }
        val duplicates =
            databaseLevelFeatures
                .groupingBy { it.database }
                .eachCount()
                .filterValues { it > 1 }
                .keys
        require(duplicates.isEmpty()) {
            "Duplicate database entries in actionbase.database-level-features: $duplicates"
        }
        featuresByDatabase = databaseLevelFeatures.associate { it.database to it.features }
        if (featuresByDatabase.isNotEmpty()) {
            log.info("database-level-features: {}", featuresByDatabase)
        }
    }

    fun featuresOf(database: String): Set<TableFeature> = featuresByDatabase[database] ?: emptySet()

    data class DatabaseFeatures(
        val database: String,
        val features: Set<TableFeature> = emptySet(),
    )

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

    companion object {
        private val log = LoggerFactory.getLogger(ServerProperties::class.java)
        private val DATABASE_NAME_REGEX = Regex(Constants.Name.PATTERN)
    }
}
