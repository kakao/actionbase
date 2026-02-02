package com.kakao.actionbase.core.java.payload.cdc;

import com.kakao.actionbase.core.java.annotation.Nullable;
import com.kakao.actionbase.core.java.metadata.v3.DatabaseDescriptor;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable
@JsonSerialize(as = ImmutableDatabaseCDC.class)
@JsonDeserialize(as = ImmutableDatabaseCDC.class)
public interface DatabaseCDC extends MetadataCDC<DatabaseDescriptor> {

  @Nullable
  DatabaseDescriptor before();

  @Nullable
  DatabaseDescriptor after();

  interface Builder extends MetadataCDC.Builder<DatabaseDescriptor> {}
}
