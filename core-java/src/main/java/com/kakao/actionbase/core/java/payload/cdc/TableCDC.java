package com.kakao.actionbase.core.java.payload.cdc;

import com.kakao.actionbase.core.java.annotation.Nullable;
import com.kakao.actionbase.core.java.metadata.v3.TableDescriptor;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable
@JsonSerialize(as = ImmutableTableCDC.class)
@JsonDeserialize(as = ImmutableTableCDC.class)
public interface TableCDC extends MetadataCDC<TableDescriptor<?>> {

  @Nullable
  TableDescriptor<?> before();

  @Nullable
  TableDescriptor<?> after();

  interface Builder extends MetadataCDC.Builder<TableDescriptor<?>> {}
}
