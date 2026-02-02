package com.kakao.actionbase.v2.core.metadata;

import java.util.HashMap;
import java.util.Map;

public enum EdgeType {
  NIL,
  INDEXED,
  HASH,
  HASH_V0,
  MULTI_EDGE,
  ;

  private static final Map<String, EdgeType> NAME_TO_VALUE_MAP = new HashMap<>();

  static {
    for (EdgeType value : EdgeType.values()) {
      NAME_TO_VALUE_MAP.put(value.name(), value);
    }
  }

  public static EdgeType of(String name) {
    return NAME_TO_VALUE_MAP.get(name);
  }
}
