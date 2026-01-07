package com.kakao.actionbase.core.java.types.common;

import com.kakao.actionbase.core.java.types.DataType;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable
@Value.Style(allMandatoryParameters = true)
@JsonSerialize(as = ImmutableStructField.class)
@JsonDeserialize(as = ImmutableStructField.class)
public interface StructField {

  String name();

  DataType<?> type();

  @Value.Default
  default String comment() {
    return "";
  }

  @Value.Default
  default boolean nullable() {
    return false;
  }

  static ImmutableStructField.Builder builder() {
    return ImmutableStructField.builder();
  }

  static StructField of(String name, DataType<?> type, String comment) {
    return ImmutableStructField.builder().name(name).type(type).comment(comment).build();
  }
}
