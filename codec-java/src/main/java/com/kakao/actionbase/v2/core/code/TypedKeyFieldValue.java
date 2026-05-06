package com.kakao.actionbase.v2.core.code;

import com.kakao.actionbase.v2.core.metadata.EncodedEdgeType;

public class TypedKeyFieldValue<T> {
  EncodedEdgeType encodedEdgeType;
  T key;
  T field;
  T value;

  public TypedKeyFieldValue(EncodedEdgeType encodedEdgeType, T key, T field, T value) {
    this.encodedEdgeType = encodedEdgeType;
    this.key = key;
    this.field = field;
    this.value = value;
  }

  public TypedKeyFieldValue(EncodedEdgeType encodedEdgeType, T key, T value) {
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

  public static <T> TypedKeyFieldValue<T> from(
      KeyFieldValue<T> keyFieldValue, EncodedEdgeType encodedEdgeType) {
    return new TypedKeyFieldValue<>(
        encodedEdgeType,
        keyFieldValue.getKey(),
        keyFieldValue.getField(),
        keyFieldValue.getValue());
  }
}
