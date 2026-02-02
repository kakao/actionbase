package com.kakao.actionbase.core.java.metadata.v3.common;

import com.kakao.actionbase.core.java.types.DataType;
import com.kakao.actionbase.core.java.types.StructType;
import com.kakao.actionbase.core.java.types.common.ImmutableStructField;
import com.kakao.actionbase.core.java.types.common.StructField;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ImmutableVertexSchema.class, name = SchemaType.VERTEX_TYPE),
  @JsonSubTypes.Type(value = ImmutableEdgeSchema.class, name = SchemaType.EDGE_TYPE),
})
public interface Schema {

  @JsonIgnore
  SchemaType type();

  List<StructField> properties();

  StructType getPropertiesSchema();

  interface Builder<B> {
    B addProperties(StructField field);

    default B addProperties(String name, DataType<?> type) {
      return addProperties(ImmutableStructField.of(name, type));
    }

    default B addProperties(String name, DataType<?> type, String comment) {
      return addProperties(
          ImmutableStructField.builder().name(name).type(type).comment(comment).build());
    }
  }
}
