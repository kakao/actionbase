package com.kakao.actionbase.core.java.types.common;

import com.kakao.actionbase.core.java.constant.Constant;
import com.kakao.actionbase.core.java.types.DataType;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable
@JsonSerialize(as = ImmutableField.class)
@JsonDeserialize(as = ImmutableField.class)
public interface Field {

  @Value.Parameter
  DataType<?> type();

  @Value.Default
  @Value.Parameter
  default String comment() {
    return Constant.DEFAULT_COMMENT;
  }

  Field LONG_FIELD = ImmutableField.builder().type(DataType.LONG).build();
  Field STRING_FIELD = ImmutableField.builder().type(DataType.STRING).build();
}
