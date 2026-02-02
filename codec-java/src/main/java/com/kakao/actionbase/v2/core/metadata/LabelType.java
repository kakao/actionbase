package com.kakao.actionbase.v2.core.metadata;

import java.util.HashMap;
import java.util.Map;

public enum LabelType {
  NIL(EdgeType.NIL, false),
  HASH(EdgeType.HASH, true),
  INDEXED(EdgeType.INDEXED, true),
  IMMUTABLE_INDEXED(EdgeType.INDEXED, true),
  MULTI_EDGE(EdgeType.MULTI_EDGE, true),
  ;

  private static final Map<String, LabelType> NAME_TO_VALUE_MAP = new HashMap<>();

  static {
    for (LabelType labelType : values()) {
      NAME_TO_VALUE_MAP.put(labelType.name(), labelType);
    }
  }

  private final EdgeType edgeType;

  private final boolean isBulkEncodingSupported;

  LabelType(EdgeType edgeType, boolean isBulkEncodingSupported) {
    this.edgeType = edgeType;
    this.isBulkEncodingSupported = isBulkEncodingSupported;
  }

  public static LabelType of(String name) {
    return NAME_TO_VALUE_MAP.get(name);
  }

  public EdgeType getEdgeType() {
    return this.edgeType;
  }

  public boolean isBulkEncodingSupported() {
    return isBulkEncodingSupported;
  }
}
