package com.kakao.actionbase.v2.core.code;

import com.kakao.actionbase.v2.core.code.hbase.OrderedBytes;
import com.kakao.actionbase.v2.core.code.hbase.SimplePositionedMutableByteRange;
import com.kakao.actionbase.v2.core.code.hbase.ValueUtils;
import com.kakao.actionbase.v2.core.metadata.Active;
import com.kakao.actionbase.v2.core.metadata.Direction;
import com.kakao.actionbase.v2.core.metadata.DirectionType;
import com.kakao.actionbase.v2.core.metadata.EncodedEdgeType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class AbstractEdgeEncoder<T> implements EdgeEncoder<T> {

  public static final byte HASH_EDGE_TYPE = EncodedEdgeType.HASH_EDGE_TYPE.getCode();
  public static final byte LOCK_EDGE_TYPE = EncodedEdgeType.LOCK_EDGE_TYPE.getCode();
  public static final byte COUNTER_EDGE_TYPE = EncodedEdgeType.COUNTER_EDGE_TYPE.getCode();

  public static final byte INDEXED_EDGE_TYPE = EncodedEdgeType.INDEXED_EDGE_TYPE.getCode();
  public static final byte IMMUTABLE_INDEXED_EDGE_TYPE =
      EncodedEdgeType.IMMUTABLE_INDEXED_EDGE_TYPE.getCode();

  public static final String INSERT_TS_KEY = "__InsertTs__";
  public static final String DELETE_TS_KEY = "__DeleteTs__";
  public static final int INSERT_TS_KEY_INT32 = ValueUtils.stringHash(INSERT_TS_KEY);
  public static final int DELETE_TS_KEY_INT32 = ValueUtils.stringHash(DELETE_TS_KEY);

  public static final String SOURCE_KEY = "_source";
  public static final String TARGET_KEY = "_target";

  public static final int SOURCE_KEY_INT32 = ValueUtils.stringHash(SOURCE_KEY);
  public static final int TARGET_KEY_INT32 = ValueUtils.stringHash(TARGET_KEY);

  private final ConcurrentLinkedQueue<EdgeBuffer> pool;

  public AbstractEdgeEncoder() {
    this.pool = null;
  }

  public AbstractEdgeEncoder(ConcurrentLinkedQueue<EdgeBuffer> pool) {
    this.pool = pool;
  }

  public int getPoolSize() {
    return pool == null ? 0 : pool.size();
  }

  public int getBufferSize() {
    if (pool == null || pool.isEmpty()) {
      return EdgeBuffer.DEFAULT_CAPACITY;
    } else {
      return pool.peek().buffer.getLimit();
    }
  }

  public static void encodeHashKeyPrefixToBuffer(Object src, int labelId, EdgeBuffer buffer) {
    buffer.encodeWithHash(src, labelId, HASH_EDGE_TYPE);
  }

  // --- public static

  public static void encodeHashKeySuffixToBuffer(Object tgt, EdgeBuffer buffer) {
    buffer.encodeAny(tgt);
  }

  public static void encodeHashValueToBuffer(HashEdgeValue versionValueMap, EdgeBuffer buffer) {
    buffer.encodeInt8(versionValueMap.active.getCode());
    buffer.encodeInt64(versionValueMap.getTs());
    for (Map.Entry<String, VersionValue> e : versionValueMap.getMap().entrySet()) {
      VersionValue versionValue = e.getValue();
      buffer.encodeInt32(ValueUtils.stringHash(e.getKey()));
      buffer.encodeAny(versionValue.getValue());
      buffer.encodeInt64(versionValue.getVersion());
    }
    Long insertTs = versionValueMap.getInsertTs();
    Long deleteTs = versionValueMap.getDeleteTs();
    if (insertTs != null) {
      buffer.encodeInt32(INSERT_TS_KEY_INT32);
      buffer.encodeAny(insertTs);
      buffer.encodeInt64(insertTs); // compatible with the version value format
    }
    if (deleteTs != null) {
      buffer.encodeInt32(DELETE_TS_KEY_INT32);
      buffer.encodeAny(deleteTs);
      buffer.encodeInt64(deleteTs); // compatible with the version value format
    }
  }

  public static HashEdgeValue decodeHashEdgeValueFromBytes(
      byte[] value, Map<Integer, String> hashToFieldNameMap) {
    SimplePositionedMutableByteRange buffer = new SimplePositionedMutableByteRange(value);

    byte activeCode = OrderedBytes.decodeInt8(buffer);
    Active active = Active.of(activeCode);
    long ts = OrderedBytes.decodeInt64(buffer);
    Long insertTs = null;
    Long deleteTs = null;
    Map<String, VersionValue> map = new HashMap<>();
    while (buffer.getRemaining() > 0) {
      int propertyKeyHash = OrderedBytes.decodeInt32(buffer);
      String propertyKey = hashToFieldNameMap.get(propertyKeyHash);
      if (propertyKey == null) {
        propertyKey = String.valueOf(propertyKeyHash);
      }
      Object propertyValue = ValueUtils.deserialize(buffer);
      long propertyTs = OrderedBytes.decodeInt64(buffer);

      if (propertyKeyHash == INSERT_TS_KEY_INT32) {
        insertTs = (Long) propertyValue;
      } else if (propertyKeyHash == DELETE_TS_KEY_INT32) {
        deleteTs = (Long) propertyValue;
      } else {
        map.put(propertyKey, new VersionValue(propertyTs, propertyValue));
      }
    }
    return new HashEdgeValue(active, ts, map, insertTs, deleteTs);
  }

  public static void encodeLockEdgeKeyToBuffer(Object src, Object tgt, int id, EdgeBuffer buffer) {
    buffer.encodeWithHash(src, id, LOCK_EDGE_TYPE);
    buffer.encodeAny(tgt);
  }

  public static void encodeLockEdgeValueToBuffer(long ts, EdgeBuffer buffer) {
    buffer.encodeInt64(ts);
  }

  public static LockEdgeValue decodeLockEdgeValueFromBytes(byte[] value) {
    SimplePositionedMutableByteRange buffer = new SimplePositionedMutableByteRange(value);
    long ts = OrderedBytes.decodeInt64(buffer);
    return new LockEdgeValue(ts);
  }

  public static void encodeCounterEdgeKeyToBuffer(
      Object srcOrTgt, int id, Direction dir, EdgeBuffer buffer) {
    buffer.encodeWithHash(srcOrTgt, id, COUNTER_EDGE_TYPE);
    buffer.encodeInt8(dir.getCode());
  }

  public static void encodeIndexedEdgeKeyPrefixToBuffer(
      Object src, Direction direction, int labelId, Index index, EdgeBuffer buffer) {

    buffer.encodeWithHash(src, labelId, INDEXED_EDGE_TYPE);
    buffer.encodeInt8(direction.getCode());
    buffer.encodeInt32(index.getId());
  }

  public static void encodeIndexedEdgeKeySuffixToBuffer(
      Index index,
      long ts,
      Object src,
      Object tgt,
      Map<String, Object> properties,
      EdgeBuffer buffer) {
    buffer.encodeFields(index, ts, src, tgt, properties);
    buffer.encodeAny(tgt);
  }

  public static void encodeIndexedEdgeValueToBuffer(
      long ts, Map<String, Object> properties, EdgeBuffer buffer) {
    buffer.encodeInt64(ts);
    for (Map.Entry<String, Object> e : properties.entrySet()) {
      buffer.encodeInt32(ValueUtils.stringHash(e.getKey()));
      buffer.encodeAny(e.getValue());
    }
  }

  @Override
  public List<KeyFieldValue<T>> encodeAllIndexedEdges(
      long ts,
      Object src,
      Object tgt,
      Map<String, Object> props,
      DirectionType dirType,
      int labelId,
      List<Index> indices) {
    return indices.stream()
        .flatMap(
            index ->
                dirType.getDirs().stream()
                    .map(dir -> encodeIndexedEdge(ts, src, tgt, props, dir, labelId, index)))
        .collect(Collectors.toList());
  }

  // --- internal

  String useAsBase64String(Consumer<EdgeBuffer> block) {
    byte[] byteArray = useAsByteArray(block);
    return SerializationUtils.base64.encode(byteArray);
  }

  public String useAsHexString(Consumer<EdgeBuffer> block) {
    byte[] byteArray = useAsByteArray(block);
    return SerializationUtils.hex.encode(byteArray);
  }

  byte[] useAsByteArray(Consumer<EdgeBuffer> block) {
    EdgeBuffer edgeBuffer = borrow();
    edgeBuffer.reset();
    block.accept(edgeBuffer);
    byte[] byteArray = edgeBuffer.buffer.toByteArray();
    release(edgeBuffer);
    return byteArray;
  }

  // --- private

  private EdgeBuffer borrow() {
    if (pool != null) {
      return pool.poll();
    } else {
      return new EdgeBuffer();
    }
  }

  private void release(EdgeBuffer reusable) {
    if (pool != null) {
      pool.add(reusable);
    }
  }
}
