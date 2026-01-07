package com.kakao.actionbase.core.java.types;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable(singleton = true, builder = false)
@JsonSerialize(as = ImmutableDoubleType.class)
@JsonDeserialize(as = ImmutableDoubleType.class)
interface DoubleType extends DataType<Double> {

  String typeName = "double";

  @Value.Derived
  @Override
  default String type() {
    return typeName;
  }

  @Override
  default Double castNotNull(Object value) {
    try {
      if (value instanceof Number) {
        return ((Number) value).doubleValue();
      }
      return Double.parseDouble(value.toString());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          String.format("Cannot cast %s to Double: %s", value.getClass().getName(), value));
    }
  }
}
