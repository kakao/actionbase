package com.kakao.actionbase.v2.core.metadata;

import java.util.HashMap;

public enum EncodedEdgeType {
  LOCK_EDGE_TYPE((byte) -1),
  COUNTER_EDGE_TYPE((byte) -2),
  HASH_EDGE_TYPE((byte) -3),
  INDEXED_EDGE_TYPE((byte) -4),
  EDGE_GROUP_TYPE((byte) -5),
  EDGE_CACHE_TYPE((byte) -6),
  // Never wired to any encoder/decoder path (dead code). -5 previously belonged to this
  // entry, which collided with the online EdgeRecordType.EDGE_GROUP(-5); moved to an unused
  // code to free up -5 for EDGE_GROUP_TYPE.
  IMMUTABLE_INDEXED_EDGE_TYPE((byte) -100),
  ;

  private static final HashMap<Byte, EncodedEdgeType> CODE_TO_VALUE_MAP = new HashMap<>();

  static {
    for (EncodedEdgeType type : EncodedEdgeType.values()) {
      CODE_TO_VALUE_MAP.put(type.code, type);
    }
  }

  private final byte code;

  EncodedEdgeType(byte code) {
    this.code = code;
  }

  public static EncodedEdgeType of(byte code) {
    return CODE_TO_VALUE_MAP.get(code);
  }

  public byte getCode() {
    return code;
  }
}
