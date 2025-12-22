package com.kakao.actionbase.v2.core.types;

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
