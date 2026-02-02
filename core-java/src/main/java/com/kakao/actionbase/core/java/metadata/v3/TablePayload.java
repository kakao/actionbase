package com.kakao.actionbase.core.java.metadata.v3;

import com.kakao.actionbase.core.java.metadata.v3.common.MutationMode;
import com.kakao.actionbase.core.java.metadata.v3.common.Schema;
import com.kakao.actionbase.core.java.metadata.v3.common.SchemaType;

import org.immutables.value.Value;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = VertexTablePayload.class, name = SchemaType.VERTEX_TYPE),
  @JsonSubTypes.Type(value = EdgeTablePayload.class, name = SchemaType.EDGE_TYPE),
})
public interface TablePayload<T extends Schema> {

  @JsonIgnore
  SchemaType type();

  String table();

  String storage();

  String comment();

  T schema();

  MutationMode mode();

  @JsonIgnore
  @Value.Auxiliary
  TableDescriptor<T> toTableDescriptor(String tenant, String database);
}
