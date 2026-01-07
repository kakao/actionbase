package com.kakao.actionbase.core.java.types;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable(singleton = true, builder = false)
@JsonSerialize(as = ImmutableIntType.class)
@JsonDeserialize(as = ImmutableIntType.class)
interface IntType extends DataType<Integer> {

  String typeName = "int";

  @Value.Derived
  @Override
  default String type() {
    return typeName;
  }

  @Override
  default Integer castNotNull(Object value) {
    try {
      if (value instanceof Number) {
        return ((Number) value).intValue();
      }
      return Integer.parseInt(value.toString());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          String.format("Cannot cast %s to Long: %s", value.getClass().getName(), value));
    }
  }
}
