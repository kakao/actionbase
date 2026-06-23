package com.kakao.actionbase.v2.core.code;

public class KeyFieldValue<T> {

  T key;
  T field;
  T value;
  T dimensionValue;

  public KeyFieldValue(T key, T field, T value) {
    this(key, field, value, null);
  }

  public KeyFieldValue(T key, T value) {
    this(key, null, value, null);
  }

  public KeyFieldValue(T key, T field, T value, T dimensionValue) {
    this.key = key;
    this.field = field;
    this.value = value;
    this.dimensionValue = dimensionValue;
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

  public KeyFieldValue<T> withDimensionValue(T dimensionValue) {
    return new KeyFieldValue<>(key, field, value, dimensionValue);
  }

  public static <T> KeyFieldValue<T> from(TypedKeyFieldValue<T> keyFieldValue) {
    return new KeyFieldValue<>(
        keyFieldValue.getKey(),
        keyFieldValue.getField(),
        keyFieldValue.getValue(),
        keyFieldValue.getDimensionValue());
  }
}
