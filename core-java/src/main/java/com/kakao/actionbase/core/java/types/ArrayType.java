package com.kakao.actionbase.core.java.types;

import java.util.List;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable
@Value.Style(allParameters = true)
@JsonSerialize(as = ImmutableArrayType.class)
@JsonDeserialize(as = ImmutableArrayType.class)
public interface ArrayType extends DataType<Object[]> {

  String typeName = "array";

  @Value.Derived
  @Override
  default String type() {
    return typeName;
  }

  DataType<?> elementType();

  @Override
  default Object[] castNotNull(Object value) {
    DataType<?> elementType = elementType();

    if (value instanceof List) {
      List<?> listValue = (List<?>) value;
      Object[] result = new Object[listValue.size()];
      for (int i = 0; i < listValue.size(); i++) {
        try {
          result[i] = elementType.cast(listValue.get(i));
        } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException("Invalid value at index " + i + ": " + e.getMessage());
        }
      }
      return result;
    } else if (value.getClass().isArray()) {
      Object[] arrayValue = (Object[]) value;
      Object[] result = new Object[arrayValue.length];
      for (int i = 0; i < arrayValue.length; i++) {
        try {
          result[i] = elementType.cast(arrayValue[i]);
        } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException("Invalid value at index " + i + ": " + e.getMessage());
        }
      }
      return result;
    }

    throw new IllegalArgumentException(
        "Cannot cast " + value.getClass().getName() + " to Array. Expected List or Array.");
  }
}
