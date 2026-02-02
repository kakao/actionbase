package com.kakao.actionbase.core.java.dataframe.row;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum RowType {
  ARRAY,
  MAP,
  EDGE,
  LIST;

  private final String type;

  RowType() {
    type = name().toLowerCase();
  }

  @JsonValue
  public String type() {
    return type;
  }

  @JsonCreator
  public static RowType fromValue(String value) {
    return valueOf(value.toUpperCase());
  }

  public static final String ARRAY_TYPE = "array";
  public static final String MAP_TYPE = "map";
  public static final String LIST_TYPE = "list";
  public static final String EDGE_TYPE = "edge";
}
