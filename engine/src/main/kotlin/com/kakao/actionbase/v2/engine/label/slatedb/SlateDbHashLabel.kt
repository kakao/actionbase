package com.kakao.actionbase.v2.engine.label.slatedb

import com.kakao.actionbase.v2.core.code.EdgeEncoder
import com.kakao.actionbase.v2.core.code.EncodedKey
import com.kakao.actionbase.v2.core.code.IdEdgeEncoder
import com.kakao.actionbase.v2.core.code.KeyFieldValue
import com.kakao.actionbase.v2.core.code.KeyValue
import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.edge.SchemaEdge
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.engine.GraphDefaults
import com.kakao.actionbase.v2.engine.edge.decodeByteArray
import com.kakao.actionbase.v2.engine.edge.toRow
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.label.AbstractLabel
import com.kakao.actionbase.v2.engine.label.LabelFactory
import com.kakao.actionbase.v2.engine.sql.DataFrame
import com.kakao.actionbase.v2.engine.sql.Row
import com.kakao.actionbase.v2.engine.sql.StatKey
import com.kakao.actionbase.v2.engine.storage.slatedb.SlateDbStorage
import com.kakao.actionbase.v2.engine.storage.slatedb.SlateDbTable

import java.nio.ByteBuffer
import java.util.Arrays

import reactor.core.publisher.Mono

class SlateDbHashLabel(
    entity: LabelEntity,
    coder: EdgeEncoder<ByteArray>,
    private val table: Mono<SlateDbTable>,
) : AbstractLabel<ByteArray>(entity, coder) {
    override fun findHashEdge(keyField: EncodedKey<ByteArray>): Mono<ByteArray> {
        require(keyField.field == null) { "field must be null" }
        return table.flatMap { it.get(keyField.key) }
    }

    override fun create(
        keyField: EncodedKey<ByteArray>,
        value: ByteArray,
    ): Mono<List<Any>> {
        require(keyField.field == null) { "field must be null" }
        return table
            .flatMap { it.put(keyField.key, value) }
            .thenReturn(emptyList())
    }

    override fun update(
        keyField: EncodedKey<ByteArray>,
        value: ByteArray,
    ): Mono<List<Any>> = create(keyField, value)

    override fun delete(keyField: EncodedKey<ByteArray>): Mono<List<Any>> {
        require(keyField.field == null) { "field must be null" }
        return table
            .flatMap { it.delete(keyField.key) }
            .thenReturn(emptyList())
    }

    override fun setnx(
        keyField: EncodedKey<ByteArray>,
        value: ByteArray,
    ): Mono<Boolean> {
        require(keyField.field == null) { "field must be null" }
        return table.flatMap { tbl ->
            tbl
                .get(keyField.key)
                .hasElement()
                .flatMap { exists ->
                    if (exists) {
                        Mono.just(false)
                    } else {
                        tbl.put(keyField.key, value).thenReturn(true)
                    }
                }
        }
    }

    override fun cad(
        keyField: EncodedKey<ByteArray>,
        value: ByteArray,
    ): Mono<Long> {
        require(keyField.field == null) { "field must be null" }
        return table.flatMap { tbl ->
            tbl
                .get(keyField.key)
                .flatMap { existingValue ->
                    if (Arrays.equals(existingValue, value)) {
                        tbl.delete(keyField.key).thenReturn(1L)
                    } else {
                        Mono.just(0L)
                    }
                }.defaultIfEmpty(0L)
        }
    }

    override fun findLockValue(keyField: EncodedKey<ByteArray>): Mono<ByteArray> {
        require(keyField.field == null) { "field must be null" }
        return table.flatMap { it.get(keyField.key) }
    }

    override fun incrby(
        key: ByteArray,
        acc: Long,
    ): Mono<List<Any>> =
        table.flatMap { tbl ->
            tbl
                .get(key)
                .map { bytes -> ByteBuffer.wrap(bytes).getLong() }
                .defaultIfEmpty(0L)
                .flatMap { current ->
                    val newValue = ByteBuffer.allocate(8).putLong(current + acc).array()
                    tbl.put(key, newValue).thenReturn(emptyList<Any>())
                }
        }

    override fun scanStorage(
        prefix: EncodedKey<ByteArray>,
        limit: Int,
        start: EncodedKey<ByteArray>?,
        end: EncodedKey<ByteArray>?,
    ): Mono<List<KeyFieldValue<ByteArray>>> {
        // SlateDB scan not yet implemented in FFI
        // For now, return empty - full scan support requires slatedb_scan_prefix_with_options
        log.warn("SlateDB scan not yet fully implemented, returning empty result")
        return Mono.just(emptyList())
    }

    override fun encodedEdgeToSchemaEdge(keyFieldValue: KeyFieldValue<ByteArray>): SchemaEdge = entity.schema.decodeByteArray(keyFieldValue)

    override fun deleteOnLock(keyField: KeyValue<ByteArray>): Mono<Boolean> = cad(EncodedKey(keyField.key), keyField.value).map { it > 0 }

    override fun getSelf(
        src: List<Any>,
        stats: Set<StatKey>,
        idEdgeEncoder: IdEdgeEncoder,
    ): Mono<DataFrame> {
        val withAll = stats.contains(StatKey.WITH_ALL)
        val withEdgeId = withAll || stats.contains(StatKey.EDGE_ID)

        val keysMono =
            Mono.just(
                src.map {
                    val edge = Edge(0L, it, it).ensureType(entity.schema)
                    coder.encodeHashEdgeKey(edge, entity.id)
                },
            )

        return keysMono
            .flatMap { keys ->
                table.flatMap { tbl ->
                    Mono.zip(
                        keys.map { key -> tbl.get(key.key).map { key to it } },
                    ) { results ->
                        results
                            .filterIsInstance<Pair<EncodedKey<ByteArray>, ByteArray>>()
                            .mapNotNull { (key, value) ->
                                try {
                                    encodedEdgeToSchemaEdge(KeyFieldValue(key.key, value))
                                } catch (e: Exception) {
                                    null
                                }
                            }.filter { withAll || it.isActive }
                            .map {
                                if (withEdgeId) {
                                    it.toRow(withAll, idEdgeEncoder)
                                } else {
                                    it.toRow(withAll, null)
                                }
                            }
                    }
                }
            }.map { rows ->
                DataFrame(
                    rows,
                    if (withAll) {
                        entity.schema.allStructType
                    } else if (withEdgeId) {
                        entity.schema.edgeIdStructType
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
        idEdgeEncoder: IdEdgeEncoder,
    ): Mono<DataFrame> {
        val withAll = stats.contains(StatKey.WITH_ALL)
        val withEdgeId = withAll || stats.contains(StatKey.EDGE_ID)

        val keys =
            tgt.map {
                val edge = Edge(0L, src, it).ensureType(entity.schema)
                coder.encodeHashEdgeKey(edge, entity.id)
            }

        return table
            .flatMap { tbl ->
                if (keys.isEmpty()) {
                    Mono.just(emptyList())
                } else {
                    Mono.zip(
                        keys.map { key -> tbl.get(key.key).map { key to it }.defaultIfEmpty(key to ByteArray(0)) },
                    ) { results ->
                        results
                            .filterIsInstance<Pair<EncodedKey<ByteArray>, ByteArray>>()
                            .filter { it.second.isNotEmpty() }
                            .mapNotNull { (key, value) ->
                                try {
                                    encodedEdgeToSchemaEdge(KeyFieldValue(key.key, value))
                                } catch (e: Exception) {
                                    null
                                }
                            }.filter { withAll || it.isActive }
                            .map {
                                if (withEdgeId) {
                                    it.toRow(withAll, idEdgeEncoder, isMultiEdge)
                                } else {
                                    it.toRow(withAll, null, isMultiEdge)
                                }
                            }
                    }
                }
            }.map { rows ->
                DataFrame(
                    rows,
                    if (withAll) {
                        entity.schema.allStructType
                    } else if (withEdgeId) {
                        entity.schema.edgeIdStructType
                    } else {
                        entity.schema.structType
                    },
                )
            }.defaultIfEmpty(DataFrame.empty(entity.schema.allStructType))
    }

    override fun getCountRows(
        srcAndKeys: List<Pair<Any, ByteArray>>,
        dir: Direction,
    ): Mono<List<Row>> =
        table.flatMap { tbl ->
            if (srcAndKeys.isEmpty()) {
                Mono.just(emptyList())
            } else {
                Mono.zip(
                    srcAndKeys.map { (src, key) ->
                        tbl
                            .get(key)
                            .map { bytes -> ByteBuffer.wrap(bytes).getLong() }
                            .defaultIfEmpty(0L)
                            .map { count -> Row(arrayOf(src, count, dir)) }
                    },
                ) { results -> results.filterIsInstance<Row>() }
            }
        }

    companion object : LabelFactory<SlateDbHashLabel, SlateDbStorage> {
        override fun create(
            entity: LabelEntity,
            graph: GraphDefaults,
            storage: SlateDbStorage,
            block: SlateDbHashLabel.() -> Unit,
        ): SlateDbHashLabel {
            val table = storage.options.getTable()
            return SlateDbHashLabel(
                entity = entity,
                coder = graph.edgeEncoderFactory.bytesKeyValueEncoder,
                table = table,
            ).apply(block)
        }
    }
}
