package com.kakao.actionbase.v2.engine.label.metastore

import com.kakao.actionbase.v2.core.code.EdgeEncoder
import com.kakao.actionbase.v2.core.code.EncodedKey
import com.kakao.actionbase.v2.core.code.KeyFieldValue
import com.kakao.actionbase.v2.core.code.KeyValue
import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.edge.SchemaEdge
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.engine.GraphDefaults
import com.kakao.actionbase.v2.engine.edge.decodeMetastore
import com.kakao.actionbase.v2.engine.edge.toRow
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.label.AbstractLabel
import com.kakao.actionbase.v2.engine.label.LabelFactory
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.Row
import com.kakao.actionbase.v2.engine.sql.StatKey
import com.kakao.actionbase.v2.engine.storage.jdbc.BaseTableConstants
import com.kakao.actionbase.v2.engine.storage.jdbc.JdbcStorage
import com.kakao.actionbase.v2.engine.storage.jdbc.MetadataTable

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

open class JdbcHashLabel(
    entity: LabelEntity,
    coder: EdgeEncoder<String>,
    private val database: Database,
    private val metadataTable: MetadataTable,
) : AbstractLabel<String>(entity, coder) {
    private val EncodedKey<String>.metastoreKey: String
        get() = if (field == null) key else "$key:$field"

    // --- for mutateSchemaEdges

    final override fun findHashEdge(keyField: EncodedKey<String>): Mono<String> =
        Mono.fromCallable {
            transaction(database) {
                val row =
                    metadataTable
                        .select { metadataTable.k eq keyField.metastoreKey }
                        .firstOrNull()
                row?.get(metadataTable.v)
            }
        }

    override fun create(
        keyField: EncodedKey<String>,
        value: String,
    ): Mono<List<Any>> =
        Mono.fromCallable {
            transaction(database) {
                metadataTable.insert {
                    it[k] = keyField.metastoreKey
                    it[v] = value
                    it[createdBy] = javaClass.canonicalName.take(BaseTableConstants.MAX_LENGTH)
                    it[modifiedBy] = javaClass.canonicalName.take(BaseTableConstants.MAX_LENGTH)
                }
                emptyList()
            }
        }

    override fun update(
        keyField: EncodedKey<String>,
        value: String,
    ): Mono<List<Any>> =
        Mono.fromCallable {
            transaction(database) {
                metadataTable.update({ metadataTable.k eq keyField.metastoreKey }) {
                    it[v] = value
                    it[modifiedBy] = javaClass.canonicalName.take(BaseTableConstants.MAX_LENGTH)
                }
            }
            emptyList()
        }

    override fun delete(keyField: EncodedKey<String>): Mono<List<Any>> =
        Mono.fromCallable {
            transaction(database) {
                metadataTable.deleteWhere { k eq keyField.metastoreKey }
                emptyList()
            }
        }

    override fun setnx(
        keyField: EncodedKey<String>,
        value: String,
    ): Mono<Boolean> =
        Mono
            .fromCallable {
                transaction(database) {
                    try {
                        val statement =
                            metadataTable.insertIgnore {
                                it[k] = keyField.metastoreKey
                                it[v] = value
                                it[createdBy] = javaClass.canonicalName.take(BaseTableConstants.MAX_LENGTH)
                                it[modifiedBy] = javaClass.canonicalName.take(BaseTableConstants.MAX_LENGTH)
                            }
                        statement.insertedCount > 0
                    } catch (_: ExposedSQLException) {
                        false
                    }
                }
            }.subscribeOn(Schedulers.boundedElastic())
            .doOnError {
                it.printStackTrace()
            }.onErrorReturn(false)

    override fun cad(
        keyField: EncodedKey<String>,
        value: String,
    ): Mono<Long> {
        require(keyField.field == null) { "field must be null" }
        return Mono
            .fromCallable {
                transaction(database) {
                    metadataTable
                        .deleteWhere {
                            k eq keyField.metastoreKey
                            v eq value
                        }.toLong()
                }
            }
    }

    override fun findLockValue(keyField: EncodedKey<String>): Mono<String> {
        require(keyField.field == null) { "field must be null" }
        return Mono
            .fromCallable {
                transaction(database) {
                    val row =
                        metadataTable
                            .select {
                                metadataTable.k eq keyField.metastoreKey
                            }.firstOrNull()
                    row?.get(metadataTable.v)
                }
            }
    }

    // not supported
    override fun incrby(
        key: String,
        acc: Long,
    ): Mono<List<Any>> = Mono.just(emptyList())

    // --- for scan

    override fun scanStorage(
        prefix: EncodedKey<String>,
        limit: Int,
        start: EncodedKey<String>?,
        end: EncodedKey<String>?,
    ): Mono<List<KeyFieldValue<String>>> {
        log.debug("scanStorage: {}, limit: {}", prefix, limit)
        return Mono
            .fromCallable {
                transaction(database) {
                    val scanKey =
                        if (prefix.field == null) {
                            prefix.key
                        } else {
                            "${prefix.key}:${prefix.field}"
                        }
                    val results =
                        metadataTable
                            .select {
                                if (start != null) {
                                    (metadataTable.k like "$scanKey%") and (metadataTable.k greaterEq start.metastoreKey)
                                } else {
                                    metadataTable.k like "$scanKey%"
                                }
                            }.orderBy(metadataTable.k to SortOrder.ASC)
                            .limit(limit)
                            .map { row ->
                                KeyFieldValue(row[metadataTable.k], "", row[metadataTable.v])
                            }
                    results
                }
            }
    }

    override fun encodedEdgeToSchemaEdge(keyFieldValue: KeyFieldValue<String>): SchemaEdge = entity.schema.decodeMetastore(KeyValue(keyFieldValue.key, keyFieldValue.value))

    override fun deleteOnLock(keyField: KeyValue<String>): Mono<Boolean> = cad(EncodedKey(keyField.key), keyField.value).map { it > 0 }

    override fun getSelf(
        src: List<Any>,
        stats: Set<StatKey>,
    ): Mono<DataFrame> {
        val withAll = stats.contains(StatKey.WITH_ALL)
        val rows =
            Mono.fromCallable {
                val metastoreKeys =
                    src.map {
                        val edge = Edge(0L, it, it).ensureType(entity.schema)
                        val key = coder.encodeHashEdgeKey(edge, entity.id)
                        key.metastoreKey
                    }

                transaction(database) {
                    metadataTable
                        .select { metadataTable.k inList metastoreKeys }
                        .map { row ->
                            encodedEdgeToSchemaEdge(KeyFieldValue(row[metadataTable.k], row[metadataTable.v]))
                        }.filter { withAll || it.isActive }
                        .map {
                            it.toRow(withAll)
                        }
                }
            }
        return rows
            .map {
                DataFrame(
                    it,
                    if (withAll) {
                        entity.schema.allStructType
                    } else {
                        entity.schema.structType
                    },
                )
            }.defaultIfEmpty(DataFrame.empty(entity.schema.allStructType))
    }

    override fun get(
        src: Any,
        tgt: List<Any>,
        dir: Direction,
        stats: Set<StatKey>,
    ): Mono<DataFrame> {
        val withAll = stats.contains(StatKey.WITH_ALL)
        val rows =
            Mono.fromCallable {
                val metastoreKeys =
                    tgt.map {
                        val edge = Edge(0L, src, it).ensureType(entity.schema)
                        val key = coder.encodeHashEdgeKey(edge, entity.id)
                        key.metastoreKey
                    }

                transaction(database) {
                    metadataTable
                        .select { metadataTable.k inList metastoreKeys }
                        .map { row ->
                            encodedEdgeToSchemaEdge(KeyFieldValue(row[metadataTable.k], row[metadataTable.v]))
                        }.filter { withAll || it.isActive }
                        .map {
                            it.toRow(withAll)
                        }
                }
            }
        return rows
            .map {
                DataFrame(
                    it,
                    if (withAll) {
                        entity.schema.allStructType
                    } else {
                        entity.schema.structType
                    },
                )
            }.defaultIfEmpty(DataFrame.empty(entity.schema.allStructType))
    }

    // not supported
    override fun getCountRows(
        srcAndKeys: List<Pair<Any, String>>,
        dir: Direction,
    ): Mono<List<Row>> = Mono.just(srcAndKeys.map { Row(arrayOf(it.first, -1L, dir)) })

    companion object : LabelFactory<JdbcHashLabel, JdbcStorage> {
        override fun create(
            entity: LabelEntity,
            graph: GraphDefaults,
            storage: JdbcStorage,
            block: JdbcHashLabel.() -> Unit,
        ): JdbcHashLabel =
            JdbcHashLabel(
                entity = entity,
                coder = graph.edgeEncoderFactory.stringKeyFieldValueEncoder,
                database = graph.metastore,
                metadataTable = graph.metadataTable,
            )
    }
}
