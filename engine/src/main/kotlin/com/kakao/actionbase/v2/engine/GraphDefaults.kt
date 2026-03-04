package com.kakao.actionbase.v2.engine

import com.kakao.actionbase.core.edge.mapper.EdgeRecordMapper
import com.kakao.actionbase.engine.EngineConstants
import com.kakao.actionbase.v2.core.code.EdgeEncoderFactory
import com.kakao.actionbase.v2.engine.compat.DefaultHBaseCluster
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.StorageEntity
import com.kakao.actionbase.v2.engine.metadata.StorageType
import com.kakao.actionbase.v2.engine.storage.jdbc.MetadataTable

import org.jetbrains.exposed.sql.Database

interface GraphDefaults {
    val localMetastore: Database
    val metastore: Database
    val metadataTable: MetadataTable
    val storages: Map<EntityName, StorageEntity>
    val edgeEncoderFactory: EdgeEncoderFactory
    val edgeRecordMapper: EdgeRecordMapper
    val lockTimeout: Long
    val datastore: DefaultHBaseCluster

    fun getStorage(uri: String): StorageEntity? =
        when {
            uri.startsWith(EngineConstants.DATASTORE_URI_PREFIX) -> {
                StorageEntity.empty.copy(active = true, type = StorageType.DATASTORE)
            }
            else -> {
                storages[EntityName.fromOrigin(uri)]
            }
        }
}

data class AbstractGraphDefaults(
    override val localMetastore: Database,
    override val metastore: Database,
    override val metadataTable: MetadataTable,
    override val edgeEncoderFactory: EdgeEncoderFactory,
    override val edgeRecordMapper: EdgeRecordMapper,
    override val lockTimeout: Long,
    override val storages: Map<EntityName, StorageEntity>,
    override val datastore: DefaultHBaseCluster,
) : GraphDefaults
