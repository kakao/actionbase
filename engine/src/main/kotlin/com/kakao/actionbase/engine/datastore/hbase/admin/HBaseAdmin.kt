package com.kakao.actionbase.engine.datastore.hbase.admin

import com.kakao.actionbase.v2.engine.util.getLogger

import org.apache.hadoop.hbase.ClusterMetrics
import org.apache.hadoop.hbase.HConstants
import org.apache.hadoop.hbase.KeepDeletedCells
import org.apache.hadoop.hbase.NamespaceDescriptor
import org.apache.hadoop.hbase.NamespaceNotFoundException
import org.apache.hadoop.hbase.RegionMetrics
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.AsyncAdmin
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder
import org.apache.hadoop.hbase.client.RegionInfo
import org.apache.hadoop.hbase.client.TableDescriptor
import org.apache.hadoop.hbase.client.TableDescriptorBuilder
import org.apache.hadoop.hbase.io.compress.Compression
import org.apache.hadoop.hbase.io.encoding.DataBlockEncoding
import org.apache.hadoop.hbase.regionserver.BloomType
import org.apache.hadoop.hbase.util.RegionSplitter

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

class HBaseAdmin(
    private val adminMono: Mono<AsyncAdmin>,
) {
    private val logger = getLogger()

    fun getNamespaces(): Mono<List<String>> =
        adminMono.flatMap { admin ->
            admin.listNamespaceDescriptors().toMono().map { list -> list.map { it.name } }
        }

    fun createNamespace(namespace: String): Mono<Void> =
        adminMono
            .flatMap { admin ->
                checkAndExecute(
                    check =
                        admin
                            .getNamespaceDescriptor(namespace)
                            .toMono()
                            .then(Mono.just(true))
                            .onErrorResume { error ->
                                if (error is NamespaceNotFoundException) {
                                    Mono.just(false)
                                } else {
                                    Mono.error(error)
                                }
                            },
                    ifAlready = { logger.info("Namespace already exists: {}", namespace) },
                    execute = Mono.defer { admin.createNamespace(NamespaceDescriptor.create(namespace).build()).toMono() },
                )
            }.then()

    fun createTable(
        namespace: String,
        table: String,
        schema: HBaseTableSchema,
    ): Mono<Void> =
        withTableOperation(namespace, table) { admin, tableName ->
            checkAndExecute(
                check = admin.tableExists(tableName).toMono(),
                ifAlready = { logger.info("Table already exists: {}:{}", namespace, table) },
                execute = Mono.defer { admin.createTable(buildTableDescriptor(tableName, schema), getSplitKeys(schema.numRegions)).toMono() },
            )
        }.then()

    fun enableTable(
        namespace: String,
        table: String,
    ): Mono<Void> =
        withTableOperation(namespace, table) { admin, tableName ->
            checkAndExecute(
                check = admin.isTableEnabled(tableName).toMono(),
                ifAlready = { logger.info("Table is already enabled: {}:{}", namespace, table) },
                execute = Mono.defer { admin.enableTable(tableName).toMono() },
            )
        }.then()

    fun disableTable(
        namespace: String,
        table: String,
    ): Mono<Void> =
        withTableOperation(namespace, table) { admin, tableName ->
            checkAndExecute(
                check = admin.isTableEnabled(tableName).toMono().map { !it },
                ifAlready = { logger.info("Table is already disabled: {}:{}", namespace, table) },
                execute = Mono.defer { admin.disableTable(tableName).toMono() },
            )
        }.then()

    fun deleteTable(
        namespace: String,
        table: String,
    ): Mono<Void> =
        withTableOperation(namespace, table) { admin, tableName ->
            admin
                .isTableEnabled(tableName)
                .toMono()
                .flatMap { isEnabled ->
                    if (isEnabled) {
                        admin.disableTable(tableName).toMono()
                    } else {
                        Mono.empty()
                    }
                }.then(Mono.defer { admin.deleteTable(tableName).toMono() })
        }.then()

    fun enableReplication(
        namespace: String,
        table: String,
    ): Mono<Void> = setReplicationScope(namespace, table, HConstants.REPLICATION_SCOPE_GLOBAL)

    fun disableReplication(
        namespace: String,
        table: String,
    ): Mono<Void> = setReplicationScope(namespace, table, HConstants.REPLICATION_SCOPE_LOCAL)

    private fun setReplicationScope(
        namespace: String,
        table: String,
        scope: Int,
    ): Mono<Void> =
        withTableOperation(namespace, table) { admin, tableName ->
            admin
                .getDescriptor(tableName)
                .toMono()
                .flatMap { descriptor ->
                    val stale = descriptor.columnFamilies.filter { it.scope != scope }
                    if (stale.isEmpty()) {
                        logger.info("Table {}:{} replication scope already {} on all column families", namespace, table, scope)
                        Mono.empty()
                    } else {
                        val builder = TableDescriptorBuilder.newBuilder(descriptor)
                        stale.forEach { cf ->
                            builder.modifyColumnFamily(
                                ColumnFamilyDescriptorBuilder.newBuilder(cf).setScope(scope).build(),
                            )
                        }
                        admin.modifyTable(builder.build()).toMono()
                    }
                }
        }.then()

    fun getTables(namespace: String): Mono<List<HBaseTableInfo>> =
        adminMono.flatMap { admin ->
            admin
                .listTableDescriptors()
                .toMono()
                .map { it.filter { desc -> desc.tableName.namespaceAsString == namespace } }
                .flatMap { filteredDescriptors ->
                    Flux
                        .fromIterable(filteredDescriptors)
                        .flatMap { descriptor ->
                            admin
                                .isTableEnabled(descriptor.tableName)
                                .toMono()
                                .map { isEnabled -> convertToHBaseTable(descriptor, isEnabled) }
                        }.collectList()
                }
        }

    fun getTable(
        namespace: String,
        tableName: String,
    ): Mono<HBaseTableInfo> =
        withTableOperation(namespace, tableName) { admin, fullTableName ->
            Mono
                .zip(
                    admin.getDescriptor(fullTableName).toMono(),
                    admin.isTableEnabled(fullTableName).toMono(),
                ) { descriptor, isEnabled ->
                    convertToHBaseTable(descriptor, isEnabled)
                }
        }.retry(1)

    fun getTableMetricSummary(
        namespace: String,
        tableName: String,
    ): Mono<Map<String, Any>> =
        adminMono.flatMap { admin ->
            val table = TableName.valueOf(namespace, tableName)
            val regionsMono = admin.getRegions(table).toMono()
            val clusterMetricsMono = admin.clusterMetrics.toMono()

            regionsMono
                .zipWith(clusterMetricsMono)
                .map { tuple ->
                    val regions = tuple.t1
                    val metrics = tuple.t2
                    regions.mapNotNull { region ->
                        getRegionLoad(metrics, region)
                            ?.let { mapOf("regionInfo" to region, "regionMetrics" to it) }
                    }
                }.map { mapOf("data" to it) }
        }

    private fun <T> withTableOperation(
        namespace: String,
        table: String,
        operation: (AsyncAdmin, TableName) -> Mono<T>,
    ): Mono<T> =
        adminMono.flatMap { admin ->
            val tableName = TableName.valueOf(namespace, table)
            operation(admin, tableName)
        }

    private fun buildTableDescriptor(
        tableName: TableName,
        schema: HBaseTableSchema,
    ): TableDescriptor {
        val columnFamilyDesc =
            ColumnFamilyDescriptorBuilder
                .newBuilder(schema.columnFamilyName)
                .setBloomFilterType(BloomType.valueOf(schema.bloomFilter))
                .setInMemory(schema.inMemory)
                .setMinVersions(schema.minVersions)
                .setMaxVersions(schema.maxVersions)
                .setKeepDeletedCells(KeepDeletedCells.valueOf(schema.keepDeletedCells))
                .setDataBlockEncoding(DataBlockEncoding.valueOf(schema.dataBlockEncoding))
                .setCompressionType(Compression.Algorithm.valueOf(schema.compression))
                .setTimeToLive(parseTTL(schema.ttl))
                .setBlockCacheEnabled(schema.blockCache)
                .setBlocksize(schema.blockSize)
                .setScope(schema.replicationScope)
                .build()

        return TableDescriptorBuilder
            .newBuilder(tableName)
            .setColumnFamily(columnFamilyDesc)
            .setSplitEnabled(true)
            .build()
    }

    private fun getSplitKeys(numRegions: Int): Array<ByteArray> =
        if (numRegions > 1) {
            RegionSplitter.UniformSplit().split(numRegions)
        } else {
            emptyArray()
        }

    private fun parseTTL(ttl: String): Int =
        when (ttl.lowercase()) {
            "forever" -> Int.MAX_VALUE
            else -> ttl.toIntOrNull() ?: Int.MAX_VALUE
        }

    private fun checkAndExecute(
        check: Mono<Boolean>,
        ifAlready: () -> Unit = {},
        execute: Mono<Void>,
    ): Mono<Void> =
        check
            .flatMap { currentState ->
                if (currentState) {
                    ifAlready()
                    Mono.empty()
                } else {
                    execute
                }
            }

    private fun convertToHBaseTable(
        descriptor: TableDescriptor,
        isEnabled: Boolean,
    ): HBaseTableInfo {
        val columnFamily = descriptor.columnFamilies.firstOrNull()
        return HBaseTableInfo(
            name = descriptor.tableName.nameAsString,
            isEnabled = isEnabled,
            bloomFilter = columnFamily?.bloomFilterType?.name ?: HBaseTableSchema.DEFAULT.bloomFilter,
            inMemory = columnFamily?.isInMemory ?: HBaseTableSchema.DEFAULT.inMemory,
            versions = columnFamily?.maxVersions ?: HBaseTableSchema.DEFAULT.maxVersions,
            keepDeletedCells = columnFamily?.keepDeletedCells?.name ?: HBaseTableSchema.DEFAULT.keepDeletedCells,
            dataBlockEncoding = columnFamily?.dataBlockEncoding?.name ?: HBaseTableSchema.DEFAULT.dataBlockEncoding,
            compression = columnFamily?.compressionType?.name ?: HBaseTableSchema.DEFAULT.compression,
            ttl = columnFamily?.timeToLive ?: DEFAULT_TTL,
            minVersions = columnFamily?.minVersions ?: HBaseTableSchema.DEFAULT.minVersions,
            blockCache = columnFamily?.isBlockCacheEnabled ?: HBaseTableSchema.DEFAULT.blockCache,
            blockSize = columnFamily?.blocksize ?: HBaseTableSchema.DEFAULT.blockSize,
            replicationScope = columnFamily?.scope ?: HBaseTableSchema.DEFAULT.replicationScope,
        )
    }

    private fun getRegionLoad(
        clusterMetrics: ClusterMetrics,
        regionInfo: RegionInfo,
    ): RegionMetrics? {
        for (serverMetrics in clusterMetrics.liveServerMetrics.values) {
            val regionMetrics = serverMetrics.regionMetrics[regionInfo.regionName]
            if (regionMetrics != null) {
                return regionMetrics
            }
        }
        return null
    }

    companion object {
        private const val DEFAULT_TTL = Int.MAX_VALUE
    }
}
