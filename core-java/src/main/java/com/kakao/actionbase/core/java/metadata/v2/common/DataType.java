package com.kakao.actionbase.core.java.metadata.v2.common;

public enum DataType {
  INT(com.kakao.actionbase.core.java.types.DataType.INT),
  SHORT(com.kakao.actionbase.core.java.types.DataType.SHORT),
  LONG(com.kakao.actionbase.core.java.types.DataType.LONG),
  BOOLEAN(com.kakao.actionbase.core.java.types.DataType.BOOLEAN),
  DOUBLE(com.kakao.actionbase.core.java.types.DataType.DOUBLE),
  STRING(com.kakao.actionbase.core.java.types.DataType.STRING),
  JSON(com.kakao.actionbase.core.java.types.DataType.OBJECT);

  private final com.kakao.actionbase.core.java.types.DataType<?> type;

  DataType(com.kakao.actionbase.core.java.types.DataType<?> type) {
    this.type = type;
  }

  public com.kakao.actionbase.core.java.types.DataType<?> getType(boolean nullable) {
    return type;
  }
}
