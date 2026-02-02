package com.kakao.actionbase.core.java.metadata.v2;

import com.kakao.actionbase.core.java.metadata.TenantId;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable
@Value.Style(allParameters = true)
@JsonSerialize(as = ImmutableAliasDescriptor.class)
@JsonDeserialize(as = ImmutableAliasDescriptor.class)
public interface AliasDescriptor
    extends Descriptor<com.kakao.actionbase.core.java.metadata.v3.AliasDescriptor> {

  boolean active();

  String name();

  String desc();

  String target();

  LabelDescriptor label();

  @Value.Derived
  @Value.Auxiliary
  default Identifier identifier() {
    return Identifier.of(name());
  }

  @Override
  default com.kakao.actionbase.core.java.metadata.v3.AliasDescriptor toV3(TenantId tenant) {
    try {
      Identifier aliasId = Identifier.of(name());
      Identifier labelId = Identifier.of(target());

      if (!aliasId.service().equals(labelId.service())) {
        throw new IllegalArgumentException(
            "Service names do not match: " + aliasId.service() + " != " + labelId.service());
      }

      return com.kakao.actionbase.core.java.metadata.v3.ImmutableAliasDescriptor.builder()
          .active(active())
          .tenant(tenant.tenant())
          .database(aliasId.service())
          .alias(aliasId.identifier())
          .table(labelId.identifier())
          .comment(desc())
          .build();
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "An error occurred during label conversion: " + e.getMessage(), e);
    }
  }
}
