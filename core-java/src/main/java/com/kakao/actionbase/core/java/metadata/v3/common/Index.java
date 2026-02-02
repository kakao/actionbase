package com.kakao.actionbase.core.java.metadata.v3.common;

import com.kakao.actionbase.core.java.util.HashUtils;

import java.util.List;

import org.immutables.value.Value;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable
@JsonSerialize(as = ImmutableIndex.class)
@JsonDeserialize(as = ImmutableIndex.class)
public interface Index {

  @Value.Parameter
  String index();

  @Value.Parameter
  List<IndexField> fields();

  @Value.Default
  default String comment() {
    return "";
  }

  /**
   * Primary index configuration.
   *
   * <p>Stores all index entries in the rowkey for precise 1-hop traversal using forward scan. Not
   * optimized for batch multi-key queries (e.g., mget).
   *
   * <p>- Storage layout: rowkey = key + (optional) index value - Allows prefix scan - Typically
   * used in the first hop of graph traversal
   *
   * <p>Value semantics: - `-1`: No limit (Long.MAX_VALUE); always store - `0`: Disabled; no primary
   * index will be written - `>0`: Always store; size limit is managed by downstream pipeline
   */
  @Value.Default
  default Long primary() {
    return -1L;
  }

  /**
   * Batch-optimized index configuration.
   *
   * <p>Stores index entries in qualifiers for fast multi-key (mget) queries, especially useful in
   * multi-hop graph traversal. This index is typically flipped or compacted periodically to avoid
   * unbounded growth.
   *
   * <p>- Storage layout: rowkey = key, qualifier = index value - Supports batch scan (multi-scan) -
   * Required for 2-hop or deeper traversal performance
   *
   * <p>Value semantics: - `-1`: No limit (Long.MAX_VALUE); always store - `0`: Disabled; no batch
   * index will be written - `>0`: Always store; size limit is managed by downstream pipeline
   */
  @Value.Default
  default Long batch() {
    return 0L;
  }

  @Value.Check
  default void check() {
    if (primary() == 0 && batch() == 0) {
      throw new IllegalStateException(
          "Invalid index definition: both primary and batch are disabled. This index will never be used.");
    }

    if (fields().isEmpty()) {
      throw new IllegalStateException(
          "Invalid index definition: at least one index field is required.");
    }

    if (batch() == -1L || batch() == Long.MAX_VALUE) {
      System.err.println(
          "[WARNING] Batch index is unbounded. This may cause excessive storage usage if not managed by a downstream pipeline.");
    }
  }

  @JsonIgnore
  @Value.Auxiliary
  default int code() {
    return HashUtils.stringHash(index());
  }
}
