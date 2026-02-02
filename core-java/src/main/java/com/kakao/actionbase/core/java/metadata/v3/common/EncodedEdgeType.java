package com.kakao.actionbase.core.java.metadata.v3.common;

import java.util.HashMap;

public enum EncodedEdgeType {
  EDGE_LOCK((byte) -1),
  EDGE_COUNT((byte) -2),
  EDGE_STATE((byte) -3),
  EDGE_INDEX((byte) -4),
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
