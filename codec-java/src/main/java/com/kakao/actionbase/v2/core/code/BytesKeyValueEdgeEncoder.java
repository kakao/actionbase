package com.kakao.actionbase.v2.core.code;

import com.kakao.actionbase.v2.core.code.hbase.OrderedBytes;
import com.kakao.actionbase.v2.core.code.hbase.SimplePositionedMutableByteRange;
import com.kakao.actionbase.v2.core.code.hbase.ValueUtils;
import com.kakao.actionbase.v2.core.edge.Edge;
import com.kakao.actionbase.v2.core.metadata.Direction;
import com.kakao.actionbase.v2.core.metadata.EncodedEdgeType;

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class BytesKeyValueEdgeEncoder extends AbstractEdgeEncoder<byte[]> implements IdEdgeEncoder {

  public BytesKeyValueEdgeEncoder() {
    super();
  }

  public BytesKeyValueEdgeEncoder(ConcurrentLinkedQueue<EdgeBuffer> pool) {
    super(pool);
  }

  @Override
  public byte[] getEmpty() {
    return new byte[0];
  }

  @Override
  public EncodedKey<byte[]> encodeHashEdgeKey(Edge edge, int labelId) {
    byte[] key =
        useAsByteArray(
            buffer -> {
              encodeHashKeyPrefixToBuffer(edge.getSrc(), labelId, buffer);
              encodeHashKeySuffixToBuffer(edge.getTgt(), buffer);
            });

    return new EncodedKey<>(key);
  }

  @Override
  public EncodedKey<byte[]> encodeHashEdgeKeyPrefix(Object src, int labelId) {
    byte[] key =
        useAsByteArray(
            buffer -> {
              encodeHashKeyPrefixToBuffer(src, labelId, buffer);
            });

    return new EncodedKey<>(key);
  }

  @Override
  public byte[] encodeHashEdgeValue(HashEdgeValue value) {
    return useAsByteArray(buffer -> encodeHashValueToBuffer(value, buffer));
  }

  @Override
  public HashEdgeValue decodeHashEdgeValue(byte[] value, Map<Integer, String> hashToFieldNameMap) {
    return decodeHashEdgeValueFromBytes(value, hashToFieldNameMap);
  }

  @Override
  public KeyValue<byte[]> encodeLockEdge(Edge edge, int labelId) {
    byte[] key =
        useAsByteArray(
            buffer -> encodeLockEdgeKeyToBuffer(edge.getSrc(), edge.getTgt(), labelId, buffer));
    byte[] value =
        useAsByteArray(buffer -> encodeLockEdgeValueToBuffer(System.currentTimeMillis(), buffer));
    return new KeyValue<>(key, value);
  }

  @Override
  public byte[] encodeLockEdgeValue(long ts) {
    return useAsByteArray(buffer -> encodeLockEdgeValueToBuffer(ts, buffer));
  }

  @Override
  public LockEdgeValue decodeLockEdgeValue(byte[] value) {
    return decodeLockEdgeValueFromBytes(value);
  }

  @Override
  public byte[] encodeCounterEdgeKey(Edge edge, Direction dir, int id) {
    Object srcOrTgt = dir == Direction.OUT ? edge.getSrc() : edge.getTgt();
    return useAsByteArray(buffer -> encodeCounterEdgeKeyToBuffer(srcOrTgt, id, dir, buffer));
  }

  @Override
  public KeyFieldValue<byte[]> encodeIndexedEdge(
      long ts,
      Object src,
      Object tgt,
      Map<String, Object> props,
      Direction dir,
      int labelId,
      Index index) {
    Object directedSrc;
    Object directedTgt;
    if (dir == Direction.OUT) {
      directedSrc = src;
      directedTgt = tgt;
    } else {
      directedSrc = tgt;
      directedTgt = src;
    }

    byte[] key =
        useAsByteArray(
            buffer -> {
              encodeIndexedEdgeKeyPrefixToBuffer(directedSrc, dir, labelId, index, buffer);
              encodeIndexedEdgeKeySuffixToBuffer(
                  index, ts, directedSrc, directedTgt, props, buffer);
            });
    byte[] value = useAsByteArray(buffer -> encodeIndexedEdgeValueToBuffer(ts, props, buffer));
    return new KeyFieldValue<>(key, value);
  }

  @Override
  public EncodedKey<byte[]> encodeIndexedEdgeKeyPrefix(
      Object directedSrc, Direction dir, int labelId, Index index, Consumer<EdgeBuffer> block) {
    byte[] key =
        useAsByteArray(
            buffer -> {
              encodeIndexedEdgeKeyPrefixToBuffer(directedSrc, dir, labelId, index, buffer);
              block.accept(buffer);
            });
    return new EncodedKey<>(key);
  }

  @Override
  public String encodeOffset(KeyFieldValue<byte[]> value) {
    // key includes the offset
    SimplePositionedMutableByteRange k = new SimplePositionedMutableByteRange(value.key);

    // xxhash32
    k.getInt();
    // src
    ValueUtils.deserialize(k);
    // labelId
    OrderedBytes.decodeInt32(k);
    // labelType
    byte encodedEdgeTypeCode = OrderedBytes.decodeInt8(k);

    EncodedEdgeType encodedEdgeType = EncodedEdgeType.of(encodedEdgeTypeCode);

    if (encodedEdgeType == EncodedEdgeType.HASH_EDGE_TYPE) {
      byte[] offset =
          useAsByteArray(buffer -> buffer.put(k.getBytes(), k.getPosition(), k.getRemaining()));
      return CryptoUtils.encryptAndEncodeUrlSafe(offset);
    } else if (encodedEdgeType == EncodedEdgeType.INDEXED_EDGE_TYPE) {
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

  // --- IdEdgeEncoder

  @Override
  public String encode(Object src, Object tgt) {
    byte[] encodedEdgeId =
        useAsByteArray(
            buffer -> {
              buffer.encodeAny(src);
              buffer.encodeAny(tgt);
            });
    return CryptoUtils.encryptAndEncodeUrlSafe(encodedEdgeId);
  }

  @Override
  public KeyValue<Object> decode(String edgeId) {
    byte[] encodedEdgeId = CryptoUtils.decodeAndDecryptUrlSafe(edgeId);

    SimplePositionedMutableByteRange buffer = new SimplePositionedMutableByteRange(encodedEdgeId);
    Object src = ValueUtils.deserialize(buffer);
    Object tgt = ValueUtils.deserialize(buffer);
    return new KeyValue<>(src, tgt);
  }
}
