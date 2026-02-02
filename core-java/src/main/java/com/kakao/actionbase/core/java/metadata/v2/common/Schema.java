package com.kakao.actionbase.core.java.metadata.v2.common;

import com.kakao.actionbase.core.java.metadata.v3.common.*;
import com.kakao.actionbase.core.java.types.common.StructField;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable
@Value.Style(allParameters = true)
@JsonSerialize(as = ImmutableSchema.class)
@JsonDeserialize(as = ImmutableSchema.class)
public interface Schema {

  Logger logger = LoggerFactory.getLogger(Schema.class);

  VertexField src();

  VertexField tgt();

  List<Field> fields();

  /** V2 supports only Edge Labels. */
  default com.kakao.actionbase.core.java.metadata.v3.common.EdgeSchema toSchema(
      LabelType type, DirectionType dir, List<Index> indices) {
    List<StructField> newFields =
        fields().stream().map(Field::toStructField).collect(Collectors.toList());

    List<com.kakao.actionbase.core.java.metadata.v3.common.Index> newIndices =
        indices.stream().map(Index::toV3).collect(Collectors.toList());

    if (type == LabelType.NIL) {
      return Schemas.EMPTY_EDGE;
    } else if (type == LabelType.HASH) {
      return ImmutableEdgeSchema.builder()
          .source(src().toField())
          .target(tgt().toField())
          .properties(newFields)
          .direction(DirectionType.BOTH)
          .indexes(Collections.emptyList())
          .build();
    } else if (type == LabelType.INDEXED || type == LabelType.MULTI_EDGE) {
      return ImmutableEdgeSchema.builder()
          .source(src().toField())
          .target(tgt().toField())
          .properties(newFields)
          .direction(dir)
          .indexes(newIndices)
          .build();
    } else {
      throw new IllegalArgumentException("Unsupported Table type: " + type);
    }
  }
}
