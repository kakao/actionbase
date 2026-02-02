package com.kakao.actionbase.v2.core.code;

import java.util.Arrays;
import java.util.Objects;

public class EncodedKey<T> {

  T key;
  T field;

  public EncodedKey(T key, T field) {
    this.key = key;
    this.field = field;
  }

  public EncodedKey(T key) {
    this.key = key;
    this.field = null;
  }

  public T getKey() {
    return key;
  }

  public T getField() {
    return field;
  }

  @Override
  public String toString() {
    return "EncodedKey{" + "key=" + formatValue(key) + ", field=" + formatValue(field) + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    EncodedKey<?> that = (EncodedKey<?>) o;
    return Objects.deepEquals(key, that.key) && Objects.deepEquals(field, that.field);
  }

  @Override
  public int hashCode() {
    return Arrays.deepHashCode(new Object[] {key, field != null ? field : 0});
  }

  private String formatValue(T value) {
    return value instanceof byte[] ? Arrays.toString((byte[]) value) : String.valueOf(value);
  }
}
