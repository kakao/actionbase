package com.kakao.actionbase.v2.core.code;

import com.kakao.actionbase.v2.core.edge.Edge;
import com.kakao.actionbase.v2.core.metadata.Direction;
import com.kakao.actionbase.v2.core.metadata.DirectionType;
import com.kakao.actionbase.v2.core.metadata.Group;

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

  KeyFieldValue<T> encodeGroupEdge(
      long ts,
      Object src,
      Object tgt,
      Map<String, Object> props,
      Direction dir,
      int labelId,
      Group group);

  /**
   * Encodes every (group, direction) pair for {@code groups}, iterating each group's own {@link
   * Group#getDirectionType()} rather than the label's {@code dirType} — matching V3's {@code
   * EdgeMutationBuilder.buildGroupRecords}.
   */
  List<KeyFieldValue<T>> encodeAllGroupEdges(
      long ts, Object src, Object tgt, Map<String, Object> props, int labelId, List<Group> groups);

  default List<KeyFieldValue<T>> encodeAllGroupEdges(Edge edge, int labelId, List<Group> groups) {
    return encodeAllGroupEdges(
        edge.getTs(), edge.getSrc(), edge.getTgt(), edge.getProps(), labelId, groups);
  }

  /**
   * Encodes only the groups whose {@link Group#getDirectionType()} includes {@code dir}, given
   * {@code src}/{@code tgt} already positioned correctly for {@code dir} — the same per-direction
   * dispatch convention {@link #encodeAllCacheEdges} relies on for MULTI_EDGE's outEdge/inEdge
   * calls, since a MULTI_EDGE label has no single edge object valid for every direction at once.
   */
  List<KeyFieldValue<T>> encodeGroupEdgesForDirection(
      long ts,
      Object src,
      Object tgt,
      Map<String, Object> props,
      Direction dir,
      int labelId,
      List<Group> groups);

  default List<KeyFieldValue<T>> encodeGroupEdgesForDirection(
      Edge edge, Direction dir, int labelId, List<Group> groups) {
    return encodeGroupEdgesForDirection(
        edge.getTs(), edge.getSrc(), edge.getTgt(), edge.getProps(), dir, labelId, groups);
  }

  KeyFieldValue<T> encodeCacheEdge(
      long ts,
      Object src,
      Object tgt,
      Map<String, Object> props,
      Direction dir,
      int labelId,
      Cache cache);

  List<KeyFieldValue<T>> encodeAllCacheEdges(
      long ts,
      Object src,
      Object tgt,
      Map<String, Object> props,
      DirectionType dirType,
      int labelId,
      List<Cache> caches);

  default List<KeyFieldValue<T>> encodeAllCacheEdges(
      Edge edge, DirectionType dirType, int labelId, List<Cache> caches) {
    return encodeAllCacheEdges(
        edge.getTs(), edge.getSrc(), edge.getTgt(), edge.getProps(), dirType, labelId, caches);
  }
}
