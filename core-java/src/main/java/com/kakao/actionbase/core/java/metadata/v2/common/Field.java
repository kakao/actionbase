package com.kakao.actionbase.core.java.metadata.v2.common;

import com.kakao.actionbase.core.java.annotation.Nullable;
import com.kakao.actionbase.core.java.types.common.ImmutableStructField;
import com.kakao.actionbase.core.java.types.common.StructField;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable
@Value.Style(allParameters = true)
@JsonSerialize(as = ImmutableField.class)
@JsonDeserialize(as = ImmutableField.class)
public interface Field {

  String name();

  DataType type();

  boolean nullable();

  @Nullable
  String desc();

  default StructField toStructField() {
    ImmutableStructField.Builder builder =
        StructField.builder().name(name()).type(type().getType(nullable())).nullable(nullable());

    if (desc() != null) {
      builder.comment(desc());
    }

    return builder.build();
  }
}
