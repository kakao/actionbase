package com.kakao.actionbase.engine.storage

import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTables

import reactor.core.publisher.Mono

/**
 * Provides HBaseTables for v2 Label implementations that need direct HBase table access
 * (e.g., Filters, CellUtil) beyond what StorageTable supports.
 */
interface HBaseTablesProvider {
    fun getHBaseTables(
        namespace: String,
        name: String,
    ): Mono<HBaseTables>
}
