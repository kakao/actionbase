package com.kakao.actionbase.core.java.payload.cdc;

import com.kakao.actionbase.core.java.annotation.Nullable;
import com.kakao.actionbase.core.java.metadata.v3.StorageDescriptor;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable
@JsonSerialize(as = ImmutableStorageCDC.class)
@JsonDeserialize(as = ImmutableStorageCDC.class)
public interface StorageCDC extends MetadataCDC<StorageDescriptor> {

  @Nullable
  StorageDescriptor before();

  @Nullable
  StorageDescriptor after();

  interface Builder extends MetadataCDC.Builder<StorageDescriptor> {}
}
