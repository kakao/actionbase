package com.kakao.actionbase.core.edge.record

import com.kakao.actionbase.core.edge.Edge
import com.kakao.actionbase.core.java.codec.common.hbase.Order
import com.kakao.actionbase.core.metadata.common.Direction
import com.kakao.actionbase.core.state.AbstractSchema

/**
 * Wide Row record for multi-hop query support.
 *
 * Unlike EdgeIndexRecord (Narrow Row: one row per edge),
 * EdgeCacheRecord stores all edges for a (source, direction, cache) combination
 * in a single HBase row, with each edge encoded as a separate qualifier.
 *
 * Row key: hash | directed source | tableCode | EDGE_CACHE | direction | cacheCode
 * Qualifier: cacheValues... | directed target
 * Value: version | properties...
 */
data class EdgeCacheRecord(
    override val key: Key,
    val qualifier: Qualifier,
    val value: Value,
) : EdgeRecord() {
    data class Key(
        val directedSource: Any,
        val tableCode: Int,
        override val recordTypeCode: Byte,
        val direction: Direction,
        val cacheCode: Int,
    ) : EdgeRecord.Key() {
        init {
            require(recordTypeCode == EdgeRecordType.EDGE_CACHE.code) {
                "Invalid record type code: $recordTypeCode, expected: ${EdgeRecordType.EDGE_CACHE.code}"
            }
        }

        companion object {
            fun of(
                directedSource: Any,
                tableCode: Int,
                direction: Direction,
                cacheCode: Int,
            ) = Key(
                directedSource = directedSource,
                tableCode = tableCode,
                recordTypeCode = EdgeRecordType.EDGE_CACHE.code,
                direction = direction,
                cacheCode = cacheCode,
            )
        }
    }

    data class Qualifier(
        val cacheValues: List<CacheValue>,
        val directedTarget: Any,
    ) {
        data class CacheValue(
            val value: Any?,
            val order: Order,
        )
    }

    data class Value(
        val version: Long,
        val properties: Map<Int, Any?>,
    )

    fun toEdge(info: AbstractSchema): Edge {
        val (source, target) =
            when (key.direction) {
                Direction.OUT -> key.directedSource to qualifier.directedTarget
                Direction.IN -> qualifier.directedTarget to key.directedSource
            }

        return Edge(
            version = value.version,
            source = source,
            target = target,
            properties =
                value.properties
                    .mapNotNull { (code, value) ->
                        info.nameOfOrNull(code)?.let { it to value }
                    }.toMap(),
        )
    }
}
