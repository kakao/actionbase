package com.kakao.actionbase.core.java.types;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable(singleton = true, builder = false)
@JsonSerialize(as = ImmutableLongType.class)
@JsonDeserialize(as = ImmutableLongType.class)
interface LongType extends DataType<Long> {

  String typeName = "long";

  @Value.Derived
  @Override
  default String type() {
    return typeName;
  }

  @Override
  default Long castNotNull(Object value) {
    try {
      if (value instanceof Number) {
        return ((Number) value).longValue();
      }
      return Long.parseLong(value.toString());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          String.format("Cannot cast %s to Long: %s", value.getClass().getName(), value));
    }
  }
}
