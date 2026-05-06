package com.kakao.actionbase.v2.core.code;

public class KeyFieldValue<T> {

  T key;
  T field;
  T value;

  public KeyFieldValue(T key, T field, T value) {
    this.key = key;
    this.field = field;
    this.value = value;
  }

  public KeyFieldValue(T key, T value) {
    this.key = key;
    this.field = null;
    this.value = value;
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

  public static <T> KeyFieldValue<T> from(TypedKeyFieldValue<T> keyFieldValue) {
    return new KeyFieldValue<>(
        keyFieldValue.getKey(), keyFieldValue.getField(), keyFieldValue.getValue());
  }
}
