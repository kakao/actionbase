package com.kakao.actionbase.core.java.dataframe.row;

import com.kakao.actionbase.core.java.types.DataType;
import com.kakao.actionbase.core.java.types.StructType;

import java.util.HashMap;
import java.util.Map;

/** Row interface. Each implementation defines its own serialization/deserialization method. */
public abstract class Row {

  public abstract RowType type();

  public abstract StructType schema();

  public abstract int size();

  public abstract Object get(int index);

  public abstract Object get(String fieldName);

  public final Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    for (String fieldName : schema().fieldNames()) {
      map.put(fieldName, get(fieldName));
    }
    return map;
  }

  public final String getAsString(String fieldName) {
    return DataType.STRING.cast(get(fieldName));
  }

  public final Map<String, Object> getAsMap(String fieldName) {
    Object value = get(fieldName);
    if (value instanceof Map) {
      return (Map<String, Object>) value;
    }
    throw new IllegalArgumentException("Field value is not a Map type: " + fieldName);
  }

  public final Boolean getAsBoolean(String fieldName) {
    return DataType.BOOLEAN.cast(get(fieldName));
  }

  public final Long getAsLong(String fieldName) {
    return DataType.LONG.cast(get(fieldName));
  }

  public final ArrayRow getAsRow(String fieldName) {
    try {
      return getAsRow(get(fieldName));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Field value is not a Row type: " + fieldName);
    }
  }

  public final ArrayRow[] getAsRows(String fieldName) {
    try {
      return getAsRows(get(fieldName));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Field value is not a Row[] type: " + fieldName);
    }
  }

  public static ArrayRow getAsRow(Object value) {
    if (value == null) {
      throw new IllegalArgumentException("Field value is null");
    }
    if (!(value instanceof ArrayRow)) {
      throw new IllegalArgumentException("Value is not a Row type: " + value.getClass());
    }
    return (ArrayRow) value;
  }

  public static ArrayRow[] getAsRows(Object value) {
    if (value == null) {
      throw new IllegalArgumentException("Field value is null");
    }
    if (!(value instanceof ArrayRow[])) {
      throw new IllegalArgumentException("Value is not a Row[] type: " + value.getClass());
    }
    return (ArrayRow[]) value;
  }
}
