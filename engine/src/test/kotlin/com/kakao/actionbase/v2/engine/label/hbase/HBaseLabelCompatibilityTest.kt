package com.kakao.actionbase.v2.engine.label.hbase

import com.kakao.actionbase.core.Constants
import com.kakao.actionbase.core.codec.ByteArrayBufferPool
import com.kakao.actionbase.core.edge.mapper.EdgeCacheRecordMapper
import com.kakao.actionbase.core.edge.mapper.EdgeCountRecordMapper
import com.kakao.actionbase.core.edge.mapper.EdgeGroupRecordMapper
import com.kakao.actionbase.core.edge.mapper.EdgeIndexRecordMapper
import com.kakao.actionbase.core.edge.mapper.EdgeLockRecordMapper
import com.kakao.actionbase.core.edge.mapper.EdgeRecordMapper
import com.kakao.actionbase.core.edge.mapper.EdgeStateRecordMapper
import com.kakao.actionbase.v2.engine.label.AbstractLabel
import com.kakao.actionbase.v2.engine.label.AbstractLabelCompatibilityTest
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseConnections
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTable
import com.kakao.actionbase.v2.engine.storage.hbase.HBaseTables
import com.kakao.actionbase.v2.engine.storage.hbase.impl.NewMockTable

import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.mock.MockHTable

import reactor.core.publisher.Mono

/**
 * Runs the shared label compatibility suite against the HBase-backed labels, the twins the
 * ByteArrayStore labels mirror. Storage is an in-process [MockHTable] (no real cluster), so the
 * same edge encoding is exercised end-to-end through the mock HBase client.
 */
class HBaseLabelCompatibilityTest : AbstractLabelCompatibilityTest() {
    private val edgeRecordMapper: EdgeRecordMapper =
        run {
            val pool = ByteArrayBufferPool.create(1, Constants.Codec.DEFAULT_BUFFER_SIZE)
            EdgeRecordMapper(
                state = EdgeStateRecordMapper.create(pool),
                index = EdgeIndexRecordMapper.create(pool),
                count = EdgeCountRecordMapper.create(pool),
                lock = EdgeLockRecordMapper.create(pool),
                group = EdgeGroupRecordMapper.create(pool),
                cache = EdgeCacheRecordMapper.create(pool),
            )
        }

    private fun freshTables(): Mono<HBaseTables> {
        // Each label needs an isolated store, so key the mock connection by a unique namespace.
        val namespace = "label-compat-${namespaceCounter++}"
        val conn = HBaseConnections.getMockConnection(namespace)
        val table = NewMockTable(conn.getTable(TableName.valueOf("edges")) as MockHTable)
        val hbaseTable = HBaseTable.create(table)
        return Mono.just(HBaseTables(hbaseTable, hbaseTable))
    }

    override fun hashLabel(): AbstractLabel<*> = HBaseHashLabel(hashEntity, coder, freshTables())

    override fun indexedLabel(): AbstractLabel<*> =
        HBaseIndexedLabel(
            entity = indexedEntity,
            coder = coder,
            indices = indices,
            indexNameToIndex = indices.associateBy { it.name },
            tables = freshTables(),
            edgeRecordMapper = edgeRecordMapper,
            lockTimeout = 300000L,
        )

    private companion object {
        var namespaceCounter = 0
    }
}
