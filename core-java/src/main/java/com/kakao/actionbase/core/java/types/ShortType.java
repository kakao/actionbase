package com.kakao.actionbase.core.java.types;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable(singleton = true, builder = false)
@JsonSerialize(as = ImmutableShortType.class)
@JsonDeserialize(as = ImmutableShortType.class)
interface ShortType extends DataType<Short> {

  String typeName = "short";

  @Value.Derived
  @Override
  default String type() {
    return typeName;
  }

  @Override
  default Short castNotNull(Object value) {
    try {
      if (value instanceof Number) {
        return ((Number) value).shortValue();
      }
      return Short.parseShort(value.toString());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          String.format("Cannot cast %s to Long: %s", value.getClass().getName(), value));
    }
  }
}
