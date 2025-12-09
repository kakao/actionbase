package com.kakao.actionbase.core.java.types;

import com.kakao.actionbase.core.java.annotation.NotNull;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable(singleton = true, builder = false)
@JsonSerialize(as = ImmutableStringType.class)
@JsonDeserialize(as = ImmutableStringType.class)
interface StringType extends DataType<String> {

  String typeName = "string";

  @Value.Derived
  @Override
  default String type() {
    return typeName;
  }

  @Override
  default String castNotNull(@NotNull Object value) {
    if (value instanceof String) {
      return (String) value;
    }

    return value.toString();
  }
}
