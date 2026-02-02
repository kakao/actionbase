package com.kakao.actionbase.v2.core.code;

import com.kakao.actionbase.v2.core.edge.Edge;
import com.kakao.actionbase.v2.core.metadata.Direction;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class StringKeyFieldValueEdgeEncoder extends AbstractEdgeEncoder<String> {

  public StringKeyFieldValueEdgeEncoder() {
    super();
  }

  public StringKeyFieldValueEdgeEncoder(ConcurrentLinkedQueue<EdgeBuffer> pool) {
    super(pool);
  }

  @Override
  public String getEmpty() {
    return "";
  }

  @Override
  public EncodedKey<String> encodeHashEdgeKey(Edge edge, int labelId) {
    String key =
        useAsBase64String(buffer -> encodeHashKeyPrefixToBuffer(edge.getSrc(), labelId, buffer));

    String field = useAsHexString(buffer -> buffer.encodeAny(edge.getTgt()));

    return new EncodedKey<>(key, field);
  }

  @Override
  public EncodedKey<String> encodeHashEdgeKeyPrefix(Object src, int labelId) {
    String key = useAsBase64String(buffer -> encodeHashKeyPrefixToBuffer(src, labelId, buffer));

    return new EncodedKey<>(key);
  }

  @Override
  public String encodeHashEdgeValue(HashEdgeValue value) {
    return useAsBase64String(buffer -> encodeHashValueToBuffer(value, buffer));
  }

  @Override
  public HashEdgeValue decodeHashEdgeValue(String value, Map<Integer, String> hashToFieldNameMap) {
    byte[] bytes = SerializationUtils.base64.decode(value);
    return decodeHashEdgeValueFromBytes(bytes, hashToFieldNameMap);
  }

  @Override
  public KeyValue<String> encodeLockEdge(Edge edge, int labelId) {
    String key =
        useAsBase64String(
            buffer -> encodeLockEdgeKeyToBuffer(edge.getSrc(), edge.getTgt(), labelId, buffer));
    String value =
        useAsBase64String(
            buffer -> encodeLockEdgeValueToBuffer(System.currentTimeMillis(), buffer));
    return new KeyValue<>(key, value);
  }

  @Override
  public String encodeLockEdgeValue(long ts) {
    return useAsBase64String(buffer -> encodeLockEdgeValueToBuffer(ts, buffer));
  }

  @Override
  public LockEdgeValue decodeLockEdgeValue(String value) {
    byte[] bytes = Base64.getUrlDecoder().decode(value);
    return decodeLockEdgeValueFromBytes(bytes);
  }

  @Override
  public String encodeCounterEdgeKey(Edge edge, Direction dir, int id) {
    Object srcOrTgt = dir == Direction.OUT ? edge.getSrc() : edge.getTgt();
    return useAsBase64String(buffer -> encodeCounterEdgeKeyToBuffer(srcOrTgt, id, dir, buffer));
  }

  @Override
  public KeyFieldValue<String> encodeIndexedEdge(
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
    String key =
        useAsBase64String(
            buffer -> encodeIndexedEdgeKeyPrefixToBuffer(directedSrc, dir, labelId, index, buffer));
    String field =
        useAsHexString(
            buffer ->
                encodeIndexedEdgeKeySuffixToBuffer(
                    index, ts, directedSrc, directedTgt, props, buffer));
    String value = useAsBase64String(buffer -> encodeIndexedEdgeValueToBuffer(ts, props, buffer));

    return new KeyFieldValue<>(key, field, value);
  }

  @Override
  public EncodedKey<String> encodeIndexedEdgeKeyPrefix(
      Object directedSrc, Direction dir, int labelId, Index index, Consumer<EdgeBuffer> block) {
    String key =
        useAsBase64String(
            buffer -> {
              encodeIndexedEdgeKeyPrefixToBuffer(directedSrc, dir, labelId, index, buffer);
              block.accept(buffer);
            });
    return new EncodedKey<>(key);
  }

  @Override
  public String encodeOffset(KeyFieldValue<String> value) {
    byte[] field = SerializationUtils.hex.decode(value.field);
    return CryptoUtils.encryptAndEncodeUrlSafe(field);
  }
}
