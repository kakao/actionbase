package com.kakao.actionbase.core.java.metadata.v2;

import com.kakao.actionbase.core.java.metadata.TenantId;
import com.kakao.actionbase.core.java.metadata.v2.common.*;
import com.kakao.actionbase.core.java.metadata.v2.common.LabelType;
import com.kakao.actionbase.core.java.metadata.v3.EdgeTableDescriptor;
import com.kakao.actionbase.core.java.metadata.v3.ImmutableEdgeTableDescriptor;
import com.kakao.actionbase.core.java.metadata.v3.TableDescriptor;
import com.kakao.actionbase.core.java.metadata.v3.common.DirectionType;
import com.kakao.actionbase.core.metadata.common.Group;

import java.util.List;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable
@JsonSerialize(as = ImmutableLabelDescriptor.class)
@JsonDeserialize(as = ImmutableLabelDescriptor.class)
public interface LabelDescriptor extends Descriptor<TableDescriptor<?>> {

  boolean active();

  String name();

  String desc();

  LabelType type();

  Schema schema();

  DirectionType dirType();

  String storage();

  List<Index> indices();

  List<Group> groups();

  List<TopNCompositeKey> topNCompositeKeys();

  MutationMode mode();

  boolean readOnly();

  boolean event();

  @Value.Derived
  @Value.Auxiliary
  default Identifier identifier() {
    return Identifier.of(name());
  }

  @Override
  default EdgeTableDescriptor toV3(TenantId tenant) {
    try {
      Identifier tableId = Identifier.of(name());

      return ImmutableEdgeTableDescriptor.builder()
          .active(active())
          .tenant(tenant.tenant())
          .database(tableId.service())
          .table(tableId.identifier())
          .storage(storage())
          .comment(desc())
          .schema(schema().toSchema(type(), dirType(), indices()))
          .mode(mode().getMutationMode(readOnly()))
          .build();
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "An error occurred during label conversion: " + e.getMessage(), e);
    }
  }
}
