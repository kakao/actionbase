package com.kakao.actionbase.core.java.codec;

import com.kakao.actionbase.core.java.codec.common.ImmutableLockEdgeValue;
import com.kakao.actionbase.core.java.codec.common.LockEdgeValue;
import com.kakao.actionbase.core.java.codec.common.hbase.OrderedBytes;
import com.kakao.actionbase.core.java.codec.common.hbase.SimplePositionedMutableByteRange;
import com.kakao.actionbase.core.java.codec.common.hbase.ValueUtils;
import com.kakao.actionbase.core.java.edge.EdgeKey;
import com.kakao.actionbase.core.java.edge.EdgeState;
import com.kakao.actionbase.core.java.edge.ImmutableEdgeKey;
import com.kakao.actionbase.core.java.edge.index.EncodableEdgeIndex;
import com.kakao.actionbase.core.java.edge.index.EncodableIndexValue;
import com.kakao.actionbase.core.java.edge.index.ImmutableStoredEdgeIndex;
import com.kakao.actionbase.core.java.edge.index.StoredEdgeIndex;
import com.kakao.actionbase.core.java.metadata.v3.common.Direction;
import com.kakao.actionbase.core.java.metadata.v3.common.EncodedEdgeType;
import com.kakao.actionbase.core.java.state.ImmutableStateValue;
import com.kakao.actionbase.core.java.state.StateValue;
import com.kakao.actionbase.core.java.types.StructType;
import com.kakao.actionbase.core.java.util.CryptoUtils;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class StateCodec implements EdgeIdCodec {

  private final ConcurrentLinkedQueue<StateCodecBuffer> bufferPool;

  StateCodec(ConcurrentLinkedQueue<StateCodecBuffer> bufferPool) {
    Objects.requireNonNull(bufferPool, "bufferPool must not be null");
    this.bufferPool = bufferPool;
  }

  public KeyValue<byte[]> encodeEdgeState(EdgeState state, int labelCode) {
    byte[] key = encodeEdgeStateKey(state.key(), labelCode);
    byte[] value = encodeEdgeStateValue(state);
    return ImmutableKeyValue.of(key, value);
  }

  public byte[] encodeEdgeStateKey(EdgeKey key, int labelCode) {
    return useAsByteArray(
        buffer -> {
          encodeEdgeStateKeyPrefixToBuffer(key.source(), labelCode, buffer);
          encodeEdgeStateKeySuffixToBuffer(key.target(), buffer);
        });
  }

  public EdgeKey decodeEdgeStateKey(byte[] key) {
    SimplePositionedMutableByteRange buffer = new SimplePositionedMutableByteRange(key);
    buffer.skip(4); // skip salt
    Object source = ValueUtils.deserialize(buffer);
    OrderedBytes.decodeInt32(buffer); // skip labelId
    byte type = OrderedBytes.decodeInt8(buffer);
    if (type != EDGE_STATE_CODE) {
      throw new IllegalArgumentException("Invalid edge state key type: " + type);
    }
    Object target = ValueUtils.deserialize(buffer);
    return ImmutableEdgeKey.of(source, target);
  }

  public byte[] encodeEdgeStateValue(EdgeState state) {
    return useAsByteArray(buffer -> encodeEdgeStateToBuffer(state, buffer));
  }

  public EdgeState decodeToEdgeState(
      byte[] value, StructType schema, Object source, Object target) {
    return decodeToEdgeStateFromBytes(value).toEdgeState(schema, source, target);
  }

  public EdgeState decodeToEdgeState(byte[] key, byte[] value, StructType schema) {
    EdgeKey edgeKey = decodeEdgeStateKey(key);
    return decodeToEdgeStateFromBytes(value)
        .toEdgeState(schema, edgeKey.source(), edgeKey.target());
  }

  public KeyValue<byte[]> encodeEdgeLock(EdgeKey edgeKey, int labelId, long lockAt) {
    byte[] key =
        useAsByteArray(
            buffer ->
                encodeEdgeLockKeyToBuffer(edgeKey.source(), edgeKey.target(), labelId, buffer));
    byte[] value = encodeEdgeLockValue(lockAt);
    return ImmutableKeyValue.of(key, value);
  }

  public byte[] encodeEdgeLockValue(long ts) {
    return useAsByteArray(buffer -> encodeEdgeLockValueToBuffer(ts, buffer));
  }

  public LockEdgeValue decodeEdgeLockValue(byte[] value) {
    return decodeLockEdgeValueFromBytes(value);
  }

  public byte[] encodeEdgeCountKey(Object start, Direction direction, int id) {
    return useAsByteArray(buffer -> encodeCounterEdgeKeyToBuffer(start, id, direction, buffer));
  }

  public KeyValue<byte[]> encodeEdgeIndex(EncodableEdgeIndex edgeIndex, int code) {
    byte[] key =
        useAsByteArray(
            buffer -> {
              encodeEdgeIndexKeyPrefix(
                  edgeIndex.directedSource(),
                  edgeIndex.direction(),
                  code,
                  edgeIndex.indexCode(),
                  buffer);
              encodeEdgeIndexKeySuffix(edgeIndex.indexValues(), edgeIndex.directedTarget(), buffer);
            });
    byte[] value =
        useAsByteArray(
            buffer -> encodeEdgeIndexValue(edgeIndex.version(), edgeIndex.properties(), buffer));
    return ImmutableKeyValue.of(key, value);
  }

  public byte[] encodeEdgeIndexKeyPrefix(
      Object start, Direction dir, int labelId, int indexCode, Consumer<StateCodecBuffer> block) {
    return useAsByteArray(
        buffer -> {
          encodeEdgeIndexKeyPrefixToBuffer(start, dir, labelId, indexCode, buffer);
          block.accept(buffer);
        });
  }

  public StoredEdgeIndex decodeToStoredEdgeIndex(KeyValue<byte[]> kv) {
    ImmutableStoredEdgeIndex.Builder builder = ImmutableStoredEdgeIndex.builder();

    SimplePositionedMutableByteRange buffer = new SimplePositionedMutableByteRange(kv.key());
    buffer.skip(4); // skip salt

    builder.directedSource(ValueUtils.deserialize(buffer));
    builder.tableCode(OrderedBytes.decodeInt32(buffer));
    OrderedBytes.decodeInt8(buffer); // skip type
    builder.direction(Direction.fromCode(OrderedBytes.decodeInt8(buffer)));
    builder.indexCode(OrderedBytes.decodeInt32(buffer));

    List<Object> indexValues = new ArrayList<>();
    while (buffer.getRemaining() > 0) {
      indexValues.add(ValueUtils.deserialize(buffer));
    }
    Object directedTarget = indexValues.remove(indexValues.size() - 1);
    builder.directedTarget(directedTarget);

    builder.indexValues(indexValues);

    SimplePositionedMutableByteRange valueBuffer = new SimplePositionedMutableByteRange(kv.value());
    builder.version(OrderedBytes.decodeInt64(valueBuffer));
    while (valueBuffer.getRemaining() > 0) {
      int hash = OrderedBytes.decodeInt32(valueBuffer);
      Object value = ValueUtils.deserialize(valueBuffer);
      builder.putProperties(hash, value);
    }

    return builder.build();
  }

  // --- IdEdgeEncoder

  public String encode(Object src, Object tgt) {
    byte[] encodedEdgeId =
        useAsByteArray(
            buffer -> {
              buffer.encodeAny(src);
              buffer.encodeAny(tgt);
            });
    return CryptoUtils.encryptAndEncodeUrlSafe(encodedEdgeId);
  }

  public KeyValue<Object> decode(String edgeId) {
    byte[] encodedEdgeId = CryptoUtils.decodeAndDecryptUrlSafe(edgeId);

    SimplePositionedMutableByteRange buffer = new SimplePositionedMutableByteRange(encodedEdgeId);
    Object src = ValueUtils.deserialize(buffer);
    Object tgt = ValueUtils.deserialize(buffer);
    return ImmutableKeyValue.of(src, tgt);
  }

  public static final byte EDGE_STATE_CODE = EncodedEdgeType.EDGE_STATE.getCode();
  public static final byte EDGE_LOCK_CODE = EncodedEdgeType.EDGE_LOCK.getCode();
  public static final byte EDGE_COUNT_CODE = EncodedEdgeType.EDGE_COUNT.getCode();

  public static final byte EDGE_INDEX_CODE = EncodedEdgeType.EDGE_INDEX.getCode();

  public static final String INSERT_TS_KEY = "__InsertTs__";
  public static final String DELETE_TS_KEY = "__DeleteTs__";
  public static final int INSERT_TS_KEY_INT32 = ValueUtils.stringHash(INSERT_TS_KEY);
  public static final int DELETE_TS_KEY_INT32 = ValueUtils.stringHash(DELETE_TS_KEY);

  public static void encodeEdgeStateKeyPrefixToBuffer(
      Object src, int labelId, StateCodecBuffer buffer) {
    buffer.encodeWithHash(src, labelId, EDGE_STATE_CODE);
  }

  // --- public static

  public static void encodeEdgeStateKeySuffixToBuffer(Object tgt, StateCodecBuffer buffer) {
    buffer.encodeAny(tgt);
  }

  public static void encodeEdgeStateToBuffer(EdgeState state, StateCodecBuffer buffer) {
    buffer.encodeInt8(state.active() ? (byte) 1 : (byte) 0);
    buffer.encodeInt64(state.version());
    for (Map.Entry<String, StateValue> e : state.properties().entrySet()) {
      StateValue stateValue = e.getValue();
      buffer.encodeInt32(ValueUtils.stringHash(e.getKey()));
      buffer.encodeAny(stateValue.value());
      buffer.encodeInt64(stateValue.version());
    }
    Long createdAt = state.createdAt();
    Long deletedAt = state.deletedAt();
    if (createdAt != null) {
      buffer.encodeInt32(INSERT_TS_KEY_INT32);
      buffer.encodeAny(createdAt);
      buffer.encodeInt64(createdAt); // compatible with the version value format
    }
    if (deletedAt != null) {
      buffer.encodeInt32(DELETE_TS_KEY_INT32);
      buffer.encodeAny(deletedAt);
      buffer.encodeInt64(deletedAt); // compatible with the version value format
    }
  }

  public static void encodeCounterEdgeKeyToBuffer(
      Object srcOrTgt, int id, Direction dir, StateCodecBuffer buffer) {
    buffer.encodeWithHash(srcOrTgt, id, EDGE_COUNT_CODE);
    buffer.encodeInt8(dir.getCode());
  }

  public static void encodeIndexedEdgeKeyPrefixToBuffer(
      Object start, Direction direction, int labelId, int indexCode, StateCodecBuffer buffer) {

    buffer.encodeWithHash(start, labelId, EDGE_INDEX_CODE);
    buffer.encodeInt8(direction.getCode());
    buffer.encodeInt32(indexCode);
  }

  public static void encodeEdgeIndexKeyPrefixToBuffer(
      Object start, Direction direction, int labelId, int indexCode, StateCodecBuffer buffer) {

    encodeEdgeIndexKeyPrefix(start, direction, labelId, indexCode, buffer);
  }

  public static void encodeEdgeIndexKeyPrefix(
      Object start, Direction direction, int labelId, int indexCode, StateCodecBuffer buffer) {
    encodeIndexedEdgeKeyPrefixToBuffer(start, direction, labelId, indexCode, buffer);
  }

  public static void encodeEdgeIndexKeySuffix(
      List<EncodableIndexValue> indexValues, Object end, StateCodecBuffer buffer) {
    for (EncodableIndexValue indexValue : indexValues) {
      buffer.encodeAny(indexValue.value(), indexValue.order());
    }
    buffer.encodeAny(end);
  }

  public static void encodeEdgeIndexValue(
      long ts, Map<String, Object> properties, StateCodecBuffer buffer) {
    buffer.encodeInt64(ts);
    for (Map.Entry<String, Object> e : properties.entrySet()) {
      buffer.encodeInt32(ValueUtils.stringHash(e.getKey()));
      buffer.encodeAny(e.getValue());
    }
  }

  // --- internal

  byte[] useAsByteArray(Consumer<StateCodecBuffer> block) {
    StateCodecBuffer edgeBuffer = borrow();
    edgeBuffer.reset();
    block.accept(edgeBuffer);
    byte[] byteArray = edgeBuffer.buffer.toByteArray();
    release(edgeBuffer);
    return byteArray;
  }

  // --- private

  private StateCodecBuffer borrow() {
    return bufferPool.poll();
  }

  private void release(StateCodecBuffer reusable) {
    bufferPool.add(reusable);
  }

  // package-private

  static EncodedEdgeStateValue decodeToEdgeStateFromBytes(byte[] value) {
    SimplePositionedMutableByteRange buffer = new SimplePositionedMutableByteRange(value);

    byte activeCode = OrderedBytes.decodeInt8(buffer);
    boolean active = activeCode == 1;
    long ts = OrderedBytes.decodeInt64(buffer);
    Long createdAt = null;
    Long deletedAt = null;
    Map<Integer, StateValue> map = new HashMap<>();
    while (buffer.getRemaining() > 0) {
      int propertyKeyHash = OrderedBytes.decodeInt32(buffer);
      Object propertyValue = ValueUtils.deserialize(buffer);
      long propertyTs = OrderedBytes.decodeInt64(buffer);

      if (propertyKeyHash == INSERT_TS_KEY_INT32) {
        createdAt = (Long) propertyValue;
      } else if (propertyKeyHash == DELETE_TS_KEY_INT32) {
        deletedAt = (Long) propertyValue;
      } else {
        map.put(
            propertyKeyHash,
            ImmutableStateValue.builder().version(propertyTs).value(propertyValue).build());
      }
    }

    return ImmutableEncodedEdgeStateValue.builder()
        .active(active)
        .version(ts)
        .properties(map)
        .createdAt(createdAt)
        .deletedAt(deletedAt)
        .build();
  }

  static void encodeEdgeLockKeyToBuffer(Object src, Object tgt, int id, StateCodecBuffer buffer) {
    buffer.encodeWithHash(src, id, EDGE_LOCK_CODE);
    buffer.encodeAny(tgt);
  }

  static void encodeEdgeLockValueToBuffer(long version, StateCodecBuffer buffer) {
    buffer.encodeInt64(version);
  }

  static LockEdgeValue decodeLockEdgeValueFromBytes(byte[] value) {
    SimplePositionedMutableByteRange buffer = new SimplePositionedMutableByteRange(value);
    long version = OrderedBytes.decodeInt64(buffer);
    return ImmutableLockEdgeValue.of(version);
  }

  public static String decodeAny(byte[] key, int keyOffset, int keyLength, byte[] value) {
    try {
      SimplePositionedMutableByteRange keyBuffer =
          new SimplePositionedMutableByteRange(key, keyOffset, keyLength);
      keyBuffer.skip(4); // skip salt
      Object source = ValueUtils.deserialize(keyBuffer);
      // skip labelId
      OrderedBytes.decodeInt32(keyBuffer);
      byte type = OrderedBytes.decodeInt8(keyBuffer);
      if (type == EDGE_STATE_CODE) {
        Object target = ValueUtils.deserialize(keyBuffer);
        return "state: " + source + "->" + target;
      } else if (type == EDGE_LOCK_CODE) {
        Object target = ValueUtils.deserialize(keyBuffer);
        return " lock: " + source + "->" + target;
      } else if (type == EDGE_COUNT_CODE) {
        Direction direction = Direction.fromCode(OrderedBytes.decodeInt8(keyBuffer));
        return "count: " + source + ", direction: " + direction;
      } else if (type == EDGE_INDEX_CODE) {
        Direction direction = Direction.fromCode(OrderedBytes.decodeInt8(keyBuffer));
        return "index: "
            + source
            + ", direction: "
            + direction
            + ", indexId: "
            + OrderedBytes.decodeInt32(keyBuffer);
      } else {
        return "ERROR - unknown edge type: " + type;
      }
    } catch (Exception e) {
      return "ERROR - decoding edge: " + e.getMessage().substring(0, 20);
    }
  }

  public Object decodeAny(KeyValue<byte[]> kv, StructType schema) {
    SimplePositionedMutableByteRange buffer = new SimplePositionedMutableByteRange(kv.key());
    buffer.skip(4); // skip salt
    Object source = ValueUtils.deserialize(buffer);
    // skip labelId
    OrderedBytes.decodeInt32(buffer);
    byte type = OrderedBytes.decodeInt8(buffer);
    if (type == EDGE_STATE_CODE) {
      Object target = ValueUtils.deserialize(buffer);
      return decodeToEdgeStateFromBytes(kv.value()).toEdgeState(schema, source, target);
    } else if (type == EDGE_LOCK_CODE) {
      return decodeLockEdgeValueFromBytes(kv.value());
    } else if (type == EDGE_COUNT_CODE) {
      return 0L;
    } else if (type == EDGE_INDEX_CODE) {
      return decodeToStoredEdgeIndex(kv);
    } else {
      throw new IllegalArgumentException("Unknown edge type: " + type);
    }
  }

  public String encodeOffset(KeyValue<byte[]> value) {
    // key includes the offset
    SimplePositionedMutableByteRange k = new SimplePositionedMutableByteRange(value.key());

    // xxhash32
    k.getInt();
    // src
    ValueUtils.deserialize(k);
    // labelId
    OrderedBytes.decodeInt32(k);
    // labelType
    byte encodedEdgeTypeCode = OrderedBytes.decodeInt8(k);

    EncodedEdgeType encodedEdgeType = EncodedEdgeType.of(encodedEdgeTypeCode);

    if (encodedEdgeType == EncodedEdgeType.EDGE_STATE) {
      byte[] offset =
          useAsByteArray(buffer -> buffer.put(k.getBytes(), k.getPosition(), k.getRemaining()));
      return CryptoUtils.encryptAndEncodeUrlSafe(offset);
    } else if (encodedEdgeType == EncodedEdgeType.EDGE_INDEX) {
      // direction
      OrderedBytes.decodeInt8(k);
      // indexId
      OrderedBytes.decodeInt32(k);
      byte[] offset =
          useAsByteArray(buffer -> buffer.put(k.getBytes(), k.getPosition(), k.getRemaining()));
      return CryptoUtils.encryptAndEncodeUrlSafe(offset);
    } else {
      throw new IllegalArgumentException("Invalid encodedEdgeType: " + encodedEdgeType);
    }
  }

  public byte[] decodeOffset(String offset) {
    return CryptoUtils.decodeAndDecryptUrlSafe(offset);
  }
}
