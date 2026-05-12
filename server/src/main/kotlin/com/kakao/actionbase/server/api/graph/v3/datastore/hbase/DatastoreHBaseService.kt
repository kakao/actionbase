package com.kakao.actionbase.server.api.graph.v3.datastore.hbase

import com.kakao.actionbase.engine.datastore.hbase.admin.HBaseAdmin
import com.kakao.actionbase.engine.datastore.hbase.admin.HBaseTableInfo
import com.kakao.actionbase.engine.datastore.hbase.admin.HBaseTableSchema
import com.kakao.actionbase.server.configuration.ConditionalOnHBaseDatastore
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.StorageEntity
import com.kakao.actionbase.v2.engine.metadata.StorageType

import org.apache.hadoop.hbase.NamespaceDescriptor
import org.apache.hadoop.hbase.TableName
import org.springframework.web.bind.annotation.RestController

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@ConditionalOnHBaseDatastore
class DatastoreHBaseService(
    namespaceDescriptor: NamespaceDescriptor,
    private val hBaseAdmin: HBaseAdmin,
    private val graph: Graph,
) {
    private val tenantNamespace = namespaceDescriptor.name
    private val legacyNamespaces =
        graph.storageDdl
            .getAll(EntityName.origin)
            .map { page ->
                page.content
                    .filter { it.active && it.type == StorageType.HBASE }
                    .mapNotNull { storage -> storage.conf.get("namespace")?.asText() }
                    .filter { it != tenantNamespace }
                    .distinct()
                    .toList()
            }.toFuture()
            .get()

    private val namespaces = legacyNamespaces + tenantNamespace

    fun getNamespaces(): List<String> = namespaces

    fun getTables(): Mono<List<HBaseTableInfo>> {
        // Extract namespaces from all storages to create a distinct namespace list
        return Flux
            .fromIterable(namespaces)
            .flatMap(hBaseAdmin::getTables)
            .collectList()
            .map { tables -> tables.flatten().distinctBy { it.name } }
    }

    fun getTable(optionalFullQualifierTableName: String): Mono<HBaseTableInfo> =
        withValidatedTableName(optionalFullQualifierTableName) { tableName ->
            hBaseAdmin.getTable(tableName.namespaceAsString, tableName.qualifierAsString)
        }

    // New tables use tenant-format namespace instead of 'kc_graph'
    fun createTable(
        optionalFullQualifierTableName: String,
        request: HBaseTableCreateRequest?,
    ): Mono<Void> =
        withValidatedTableName(optionalFullQualifierTableName) { tableName ->
            val schema = request?.toHBaseTableSchema() ?: HBaseTableSchema.DEFAULT
            hBaseAdmin.createTable(tableName.namespaceAsString, tableName.qualifierAsString, schema)
        }

    fun updateTable(
        optionalFullQualifierTableName: String,
        request: HBaseTableUpdateRequest,
    ): Mono<Void> =
        withValidatedTableName(optionalFullQualifierTableName) { tableName ->
            val updateStream =
                if (request.enable == true) {
                    hBaseAdmin.enableTable(tableName.namespaceAsString, tableName.qualifierAsString)
                } else {
                    hBaseAdmin.disableTable(tableName.namespaceAsString, tableName.qualifierAsString)
                }
            throwErrorIfStorageInUse(tableName).then(updateStream)
        }

    fun enableTable(optionalFullQualifierTableName: String): Mono<Void> =
        withValidatedTableName(optionalFullQualifierTableName) { tableName ->
            throwErrorIfStorageInUse(tableName)
                .then(hBaseAdmin.enableTable(tableName.namespaceAsString, tableName.qualifierAsString))
        }

    fun disableTable(optionalFullQualifierTableName: String): Mono<Void> =
        withValidatedTableName(optionalFullQualifierTableName) { tableName ->
            throwErrorIfStorageInUse(tableName)
                .then(hBaseAdmin.disableTable(tableName.namespaceAsString, tableName.qualifierAsString))
        }

    // Replication toggle is allowed on in-use tables. The operator runbook for safe table cleanup
    // requires setting replicationScope=0 on both clusters *before* disabling/dropping — that step
    // must work while the table is still actively serving writes.
    fun enableReplication(optionalFullQualifierTableName: String): Mono<Void> =
        withValidatedTableName(optionalFullQualifierTableName) { tableName ->
            hBaseAdmin.enableReplication(tableName.namespaceAsString, tableName.qualifierAsString)
        }

    fun disableReplication(optionalFullQualifierTableName: String): Mono<Void> =
        withValidatedTableName(optionalFullQualifierTableName) { tableName ->
            hBaseAdmin.disableReplication(tableName.namespaceAsString, tableName.qualifierAsString)
        }

    fun deleteTable(optionalFullQualifierTableName: String): Mono<Void> =
        withValidatedTableName(optionalFullQualifierTableName) { tableName ->
            throwErrorIfStorageInUse(tableName)
                .then(hBaseAdmin.deleteTable(tableName.namespaceAsString, tableName.qualifierAsString))
        }

    fun getTableMetricSummary(optionalFullQualifierTableName: String): Mono<Map<String, Any>> =
        withValidatedTableName(optionalFullQualifierTableName) { tableName ->
            hBaseAdmin
                .getTableMetricSummary(tableName.namespaceAsString, tableName.qualifierAsString)
        }

    private fun throwErrorIfStorageInUse(tableName: TableName): Mono<Void> =
        getStorageInUse(tableName.namespaceAsString, tableName.qualifierAsString)
            .flatMap { storages ->
                if (storages.isNotEmpty()) {
                    Mono.error(IllegalArgumentException("Table ${tableName.namespaceAsString}:$tableName is used by storages (${storages.joinToString(",") { it.fullName }})"))
                } else {
                    Mono.empty()
                }
            }

    private fun getStorageInUse(
        namespace: String,
        tableName: String,
    ): Mono<List<StorageEntity>> =
        graph.storageDdl
            .getAll(EntityName.origin)
            .map { page ->
                page.content.filter { storage ->
                    storage.active &&
                        storage.type == StorageType.HBASE &&
                        (
                            storage.conf.get("namespace").asText() == namespace &&
                                storage.conf.get("tableName").asText() == tableName
                        )
                }
            }

    private fun <T> withValidatedTableName(
        optionalFullQualifierTableName: String,
        block: (TableName) -> Mono<T>,
    ): Mono<T> =
        runCatching {
            val tableName =
                optionalFullQualifierTableName.split(":").let {
                    if (it.size == 2) {
                        TableName.valueOf(validateNamespace(it[0]), it[1])
                    } else {
                        TableName.valueOf(tenantNamespace, optionalFullQualifierTableName)
                    }
                }
            block(tableName)
        }.getOrElse { Mono.error(it) }

    private fun validateNamespace(namespace: String): String {
        if (!namespaces.contains(namespace)) {
            throw IllegalArgumentException("invalid namespace: $namespace")
        }
        return namespace
    }
}
