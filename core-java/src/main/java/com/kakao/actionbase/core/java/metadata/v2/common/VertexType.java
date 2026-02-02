package com.kakao.actionbase.core.java.metadata.v2.common;

import com.kakao.actionbase.core.java.types.DataType;

public enum VertexType {
  LONG(DataType.LONG),
  STRING(DataType.STRING);

  private final DataType type;

  VertexType(DataType type) {
    this.type = type;
  }

  public DataType getType() {
    return type;
  }
}
