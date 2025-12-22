package com.kakao.actionbase.core.java.types;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable(singleton = true, builder = false)
@JsonSerialize(as = ImmutableBooleanType.class)
@JsonDeserialize(as = ImmutableBooleanType.class)
interface BooleanType extends DataType<Boolean> {

  static BooleanType of() {
    return ImmutableBooleanType.of();
  }

  String typeName = "boolean";

  @Value.Derived
  @Override
  default String type() {
    return typeName;
  }

  @Override
  default Boolean castNotNull(Object value) {
    try {
      if (value instanceof Boolean) {
        return (Boolean) value;
      } else if (value instanceof Number) {
        return ((Number) value).longValue() != 0L;
      }
      return Long.parseLong(value.toString()) != 0L;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          String.format("Cannot cast %s to Boolean: %s", value.getClass().getName(), value));
    }
  }
}
