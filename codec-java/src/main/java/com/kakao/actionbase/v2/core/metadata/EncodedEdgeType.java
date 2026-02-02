package com.kakao.actionbase.v2.core.metadata;

import java.util.HashMap;

public enum EncodedEdgeType {
  LOCK_EDGE_TYPE((byte) -1),
  COUNTER_EDGE_TYPE((byte) -2),
  HASH_EDGE_TYPE((byte) -3),
  INDEXED_EDGE_TYPE((byte) -4),
  IMMUTABLE_INDEXED_EDGE_TYPE((byte) -5),
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
