package com.kakao.actionbase.v2.core.code;

import com.kakao.actionbase.v2.core.edge.Edge;
import com.kakao.actionbase.v2.core.metadata.Direction;
import com.kakao.actionbase.v2.core.metadata.DirectionType;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface EdgeEncoder<T> {

  T getEmpty();

  // HashEdge
  EncodedKey<T> encodeHashEdgeKey(Edge edge, int labelId);

  EncodedKey<T> encodeHashEdgeKeyPrefix(Object src, int labelId);

  T encodeHashEdgeValue(HashEdgeValue value);

  HashEdgeValue decodeHashEdgeValue(T value, Map<Integer, String> hashToFieldNameMap);

  // LockEdge
  KeyValue<T> encodeLockEdge(Edge edge, int labelId);

  T encodeLockEdgeValue(long ts);

  LockEdgeValue decodeLockEdgeValue(T value);

  // CounterEdge
  T encodeCounterEdgeKey(Edge edge, Direction dir, int id);

  // IndexedEdge
  KeyFieldValue<T> encodeIndexedEdge(
      long ts,
      Object src,
      Object tgt,
      Map<String, Object> props,
      Direction dir,
      int labelId,
      Index index);

  EncodedKey<T> encodeIndexedEdgeKeyPrefix(
      Object directedSrc, Direction dir, int labelId, Index index, Consumer<EdgeBuffer> block);

  List<KeyFieldValue<T>> encodeAllIndexedEdges(
      long ts,
      Object src,
      Object tgt,
      Map<String, Object> props,
      DirectionType dirType,
      int labelId,
      List<Index> indices);

  /**
   * Encode the offset to a string. It contains the offset oft the index.
   *
   * @param value the encoded key field value
   * @return the encoded offset
   */
  String encodeOffset(KeyFieldValue<T> value);

  /**
   * Decode the offset from a string. It contains the offset oft the index.
   *
   * @param offset the encoded offset
   * @return the offset
   */
  default byte[] decodeOffset(String offset) {
    return CryptoUtils.decodeAndDecryptUrlSafe(offset);
  }

  default List<KeyFieldValue<T>> encodeAllIndexedEdges(
      Edge edge, DirectionType dirType, int labelId, List<Index> indices) {
    return encodeAllIndexedEdges(
        edge.getTs(), edge.getSrc(), edge.getTgt(), edge.getProps(), dirType, labelId, indices);
  }
}
