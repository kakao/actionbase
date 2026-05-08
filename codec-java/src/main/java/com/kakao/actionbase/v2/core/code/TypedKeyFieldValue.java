package com.kakao.actionbase.v2.core.code;

import com.kakao.actionbase.v2.core.metadata.EncodedEdgeType;

public class TypedKeyFieldValue<T> {
  EncodedEdgeType encodedEdgeType;
  T key;
  T field;
  T value;
  T dimensionValue;

  public TypedKeyFieldValue(
      EncodedEdgeType encodedEdgeType, T key, T field, T value, T dimensionValue) {
    this.encodedEdgeType = encodedEdgeType;
    this.key = key;
    this.field = field;
    this.value = value;
    this.dimensionValue = dimensionValue;
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

  public T getDimensionValue() {
    return dimensionValue;
  }

  public static <T> TypedKeyFieldValue<T> from(
      KeyFieldValue<T> keyFieldValue, EncodedEdgeType encodedEdgeType) {
    return new TypedKeyFieldValue<>(
        encodedEdgeType,
        keyFieldValue.getKey(),
        keyFieldValue.getField(),
        keyFieldValue.getValue(),
        keyFieldValue.getDimensionValue());
  }
}
