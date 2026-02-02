package com.kakao.actionbase.core.java.metadata.v2;

import com.kakao.actionbase.core.java.constant.Constant;
import com.kakao.actionbase.core.java.metadata.TenantId;
import com.kakao.actionbase.core.java.metadata.v2.common.StorageType;
import com.kakao.actionbase.core.java.metadata.v3.common.DatastoreType;

import java.util.Map;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Value.Immutable
@JsonSerialize(as = ImmutableStorageDescriptor.class)
@JsonDeserialize(as = ImmutableStorageDescriptor.class)
public interface StorageDescriptor
    extends Descriptor<com.kakao.actionbase.core.java.metadata.v3.StorageDescriptor> {

  boolean active();

  String name();

  String desc();

  StorageType type();

  Map<String, Object> conf();

  @Value.Derived
  @Value.Auxiliary
  default Identifier identifier() {
    return Identifier.of(getDefaultStorageName());
  }

  @Override
  default com.kakao.actionbase.core.java.metadata.v3.StorageDescriptor toV3(TenantId tenant) {
    try {
      Identifier storageId = Identifier.of(getDefaultStorageName());
      DatastoreType datastoreType = StorageType.toDatastoreType(type());

      return com.kakao.actionbase.core.java.metadata.v3.ImmutableStorageDescriptor.builder()
          .type(datastoreType)
          .active(active())
          .tenant(tenant.tenant())
          .database(storageId.service())
          .storage(storageId.identifier())
          .datastore(Constant.Datastore.DEFAULT_NAME)
          .configuration(conf())
          .comment(desc())
          .build();
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "An error occurred during storage conversion: " + e.getMessage(), e);
    }
  }

  // FIXME v2 metadata does not have service information. Temporary code for v3 compatibility
  @Value.Auxiliary
  default String getDefaultStorageName() {
    return "default." + name();
  }
}
