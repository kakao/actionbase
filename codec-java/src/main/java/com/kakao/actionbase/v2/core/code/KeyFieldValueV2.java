package com.kakao.actionbase.v2.core.code;

import com.kakao.actionbase.v2.core.metadata.EncodedEdgeType;

public class KeyFieldValueV2<T> {
  EncodedEdgeType encodedEdgeType;
  T key;
  T field;
  T value;

  public KeyFieldValueV2(EncodedEdgeType encodedEdgeType, T key, T field, T value) {
    this.encodedEdgeType = encodedEdgeType;
    this.key = key;
    this.field = field;
    this.value = value;
  }

  public KeyFieldValueV2(EncodedEdgeType encodedEdgeType, T key, T value) {
    this.encodedEdgeType = encodedEdgeType;
    this.key = key;
    this.field = null;
    this.value = value;
  }

  public EncodedEdgeType getEncodedEdgeType() {
    return encodedEdgeType;
  }

  public T getKey() {
    return key;
  }

  public T getField() {
    return field;
  }

  public T getValue() {
    return value;
  }

  public static <T> KeyFieldValueV2<T> fromV1(KeyFieldValue<T> keyFieldValue, EncodedEdgeType encodedEdgeType) {
    return new KeyFieldValueV2<>(encodedEdgeType, keyFieldValue.getKey(), keyFieldValue.getField(), keyFieldValue.getValue());
  }
}
