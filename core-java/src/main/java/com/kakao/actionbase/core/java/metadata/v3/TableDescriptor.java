package com.kakao.actionbase.core.java.metadata.v3;

import com.kakao.actionbase.core.java.edge.EdgePayload;
import com.kakao.actionbase.core.java.edge.EdgeState;
import com.kakao.actionbase.core.java.metadata.*;
import com.kakao.actionbase.core.java.metadata.ImmutableTableId;
import com.kakao.actionbase.core.java.metadata.TableId;
import com.kakao.actionbase.core.java.metadata.v3.common.MutationMode;
import com.kakao.actionbase.core.java.metadata.v3.common.Schema;
import com.kakao.actionbase.core.java.metadata.v3.common.SchemaType;
import com.kakao.actionbase.core.java.util.HashUtils;

import org.immutables.value.Value;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = VertexTableDescriptor.class, name = SchemaType.VERTEX_TYPE),
  @JsonSubTypes.Type(value = EdgeTableDescriptor.class, name = SchemaType.EDGE_TYPE),
})
public interface TableDescriptor<T extends Schema> extends Descriptor<TableId> {

  SchemaType type();

  String database();

  String table();

  String storage();

  T schema();

  MutationMode mode();

  @Override
  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default TableId id() {
    return ImmutableTableId.of(tenant(), database(), table());
  }

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default StorageId storageId() {
    return ImmutableStorageId.of(tenant(), database(), storage());
  }

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default int code() {
    String fullQualifiedName = database() + "." + table();
    return HashUtils.stringHash(fullQualifiedName);
  }

  @Override
  @JsonIgnore
  @Value.Auxiliary
  default EdgePayload toEdgePayload() {
    return Tables.toEdgePayload(this);
  }

  @Override
  @JsonIgnore
  @Value.Auxiliary
  default EdgeState toEdgeState() {
    return Tables.toEdgeState2(this);
  }
}
