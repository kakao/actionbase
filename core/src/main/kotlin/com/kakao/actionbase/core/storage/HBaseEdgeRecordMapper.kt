package com.kakao.actionbase.core.storage

import com.kakao.actionbase.core.codec.MapperExtensions.decodeEdgeKeyPrefix
import com.kakao.actionbase.core.codec.buffer
import com.kakao.actionbase.core.edge.mapper.EdgeCacheRecordMapper
import com.kakao.actionbase.core.edge.mapper.EdgeCountRecordMapper
import com.kakao.actionbase.core.edge.mapper.EdgeGroupRecordMapper
import com.kakao.actionbase.core.edge.mapper.EdgeIndexRecordMapper
import com.kakao.actionbase.core.edge.mapper.EdgeLockRecordMapper
import com.kakao.actionbase.core.edge.mapper.EdgeRecordMapper
import com.kakao.actionbase.core.edge.mapper.EdgeStateRecordMapper
import com.kakao.actionbase.core.edge.record.EdgeInvalidRecord
import com.kakao.actionbase.core.edge.record.EdgeRecord
import com.kakao.actionbase.core.edge.record.EdgeRecordType

object HBaseEdgeRecordMapper {
    private val edgeRecordMapper: EdgeRecordMapper =
        EdgeRecordMapper(
            state = EdgeStateRecordMapper.create(),
            index = EdgeIndexRecordMapper.create(),
            count = EdgeCountRecordMapper.create(),
            lock = EdgeLockRecordMapper.create(),
            group = EdgeGroupRecordMapper.create(),
            cache = EdgeCacheRecordMapper.create(),
        )
    private val stateDecoder = edgeRecordMapper.state.decoder
    private val indexDecoder = edgeRecordMapper.index.decoder
    private val countDecoder = edgeRecordMapper.count.decoder
    private val lockDecoder = edgeRecordMapper.lock.decoder
    private val groupDecoder = edgeRecordMapper.group.decoder

    fun decodeIntoEdgeType(key: ByteArray): EdgeRecordType = EdgeRecordType.of(key.buffer().decodeEdgeKeyPrefix().recordTypeCode)

    fun decodeToEdgeRecord(
        key: ByteArray,
        value: ByteArray,
    ): EdgeRecord {
        val keyPrefix: EdgeRecord.Key.CommonPrefix = key.buffer().decodeEdgeKeyPrefix()
        return when (keyPrefix.recordTypeCode) {
            EdgeRecordType.EDGE_STATE.code -> stateDecoder.decode(key, value)
            EdgeRecordType.EDGE_INDEX.code -> indexDecoder.decode(key, value)
            EdgeRecordType.EDGE_COUNT.code -> countDecoder.decode(key, value)
            EdgeRecordType.EDGE_LOCK.code -> lockDecoder.decode(key, value)
            // EdgeRecordType.EDGE_GROUP.code -> groupDecoder.decode(key, value) // need qualifier
            else -> EdgeInvalidRecord(keyPrefix.recordTypeCode)
        }
    }

    fun decodeToEdgeRecordKey(key: ByteArray): EdgeRecord.Key {
        val keyPrefix: EdgeRecord.Key.CommonPrefix = key.buffer().decodeEdgeKeyPrefix()
        return when (keyPrefix.recordTypeCode) {
            EdgeRecordType.EDGE_STATE.code -> stateDecoder.decodeKey(key)
            EdgeRecordType.EDGE_INDEX.code -> indexDecoder.decodeKey(key)
            EdgeRecordType.EDGE_COUNT.code -> countDecoder.decodeKey(key)
            EdgeRecordType.EDGE_LOCK.code -> lockDecoder.decodeKey(key)
            // EdgeRecordType.EDGE_GROUP.code -> groupDecoder.decodeKey(key)
            else -> EdgeInvalidRecord.Key(keyPrefix.recordTypeCode)
        }
    }

    fun decode(
        status: String,
        timestamp: Long,
        key: ByteArray,
        value: ByteArray,
    ): HBaseEdgeRecord = decodeToEdgeRecord(key, value).toHBaseEdgeRecord(status, timestamp)

    fun decodeKey(
        status: String,
        timestamp: Long,
        key: ByteArray,
    ): HBaseEdgeRecord.Key = decodeToEdgeRecordKey(key).toHBaseEdgeRecordKey(status, timestamp)
}
