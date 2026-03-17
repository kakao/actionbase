package com.kakao.actionbase.core.edge.record

import com.kakao.actionbase.core.metadata.common.Direction

/**
 * EdgeCache Wide Row record.
 *
 * Row:       hash(source) | tableCode | EDGE_CACHE | direction | cacheCode
 * Qualifier: cacheValues + target
 * Value:     version + properties
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
            ): Key =
                Key(
                    directedSource = directedSource,
                    tableCode = tableCode,
                    recordTypeCode = EdgeRecordType.EDGE_CACHE.code,
                    direction = direction,
                    cacheCode = cacheCode,
                )
        }
    }

    data class Qualifier(
        val cacheValues: List<Any?>,
        val directedTarget: Any,
    )

    data class Value(
        val version: Long,
        val properties: Map<Int, Any?>,
    )
}
