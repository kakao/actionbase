package com.kakao.actionbase.core.java.payload.cdc;

import com.kakao.actionbase.core.java.annotation.Nullable;
import com.kakao.actionbase.core.java.metadata.v3.AliasDescriptor;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable
@JsonSerialize(as = ImmutableAliasCDC.class)
@JsonDeserialize(as = ImmutableAliasCDC.class)
public interface AliasCDC extends MetadataCDC<AliasDescriptor> {

  @Nullable
  AliasDescriptor before();

  @Nullable
  AliasDescriptor after();

  interface Builder extends MetadataCDC.Builder<AliasDescriptor> {}
}
