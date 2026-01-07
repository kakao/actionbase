package com.kakao.actionbase.core.java.hbase;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable
@JsonSerialize(as = ImmutableHBaseTablePayload.class)
@JsonDeserialize(as = ImmutableHBaseTablePayload.class)
public interface HBaseTablePayload {

  String name();

  @Value.Default
  default String columnFamilyName() {
    return "f";
  }

  @Value.Default
  default String bloomFilter() {
    return "ROW";
  }

  @Value.Default
  default boolean inMemory() {
    return false;
  }

  @Value.Default
  default String keepDeletedCells() {
    return "FALSE";
  }

  @Value.Default
  default String dataBlockEncoding() {
    return "FAST_DIFF";
  }

  @Value.Default
  default String compression() {
    return "LZ4";
  }

  @Value.Default
  default String ttl() {
    return "forever";
  }

  @Value.Default
  default int maxVersions() {
    return 1;
  }

  @Value.Default
  default int minVersions() {
    return 0;
  }

  @Value.Default
  default boolean blockCache() {
    return true;
  }

  @Value.Default
  default int blockSize() {
    return 65536;
  }

  @Value.Default
  default int numRegions() {
    return 32;
  }

  @Value.Default
  default int replicationScope() {
    return 0;
  }
}
