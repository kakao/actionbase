package com.kakao.actionbase.v2.engine

import com.kakao.actionbase.core.edge.mapper.EdgeRecordMapper
import com.kakao.actionbase.engine.EngineConstants
import com.kakao.actionbase.engine.datastore.impl.ByteArrayStore
import com.kakao.actionbase.v2.core.code.EdgeEncoderFactory
import com.kakao.actionbase.v2.engine.compat.DefaultHBaseCluster
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.StorageEntity
import com.kakao.actionbase.v2.engine.metadata.StorageType
import com.kakao.actionbase.v2.engine.storage.jdbc.MetadataTable

import org.jetbrains.exposed.sql.Database

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

interface GraphDefaults {
    val localStore: ByteArrayStore
    val metastore: Database
    val metadataTable: MetadataTable
    val storages: Map<EntityName, StorageEntity>
    val edgeEncoderFactory: EdgeEncoderFactory
    val edgeRecordMapper: EdgeRecordMapper
    val lockTimeout: Long
    val datastore: DefaultHBaseCluster

    // A label's storage is a datastore:// URI resolved directly by namespace:
    // __sys__/metastore is the RDB-backed system metastore, everything else is
    // the HBase datastore. Bare names are legacy references to metastore-stored
    // Storage entities, kept until migration retires them.
    fun getStorage(uri: String): StorageEntity? =
        when {
            uri == EngineConstants.METASTORE_URI ->
                StorageEntity.empty.copy(
                    active = true,
                    type = StorageType.LOCAL,
                    conf = jacksonObjectMapper().createObjectNode().apply { put("useGlobal", true) },
                )
            uri.startsWith(EngineConstants.DATASTORE_URI_PREFIX) ->
                StorageEntity.empty.copy(active = true, type = StorageType.DATASTORE)
            else ->
                storages[EntityName.fromOrigin(uri)]
        }
}

data class AbstractGraphDefaults(
    override val localStore: ByteArrayStore,
    override val metastore: Database,
    override val metadataTable: MetadataTable,
    override val edgeEncoderFactory: EdgeEncoderFactory,
    override val edgeRecordMapper: EdgeRecordMapper,
    override val lockTimeout: Long,
    override val storages: Map<EntityName, StorageEntity>,
    override val datastore: DefaultHBaseCluster,
) : GraphDefaults
