package com.kakao.actionbase.core.java.metadata.v2.common;

import com.kakao.actionbase.core.java.metadata.v3.common.DatastoreType;

public enum StorageType {
  NIL,
  LOCAL,
  JDBC,
  HBASE;

  public static DatastoreType toDatastoreType(StorageType type) {
    switch (type) {
      case LOCAL:
        return DatastoreType.MEMORY;
      case HBASE:
        return DatastoreType.HBASE;
      default:
        return DatastoreType.MEMORY; // TODO: Temporary handling for types not supported in v3
    }
  }

  public static StorageType fromDatastoreType(DatastoreType type) {
    switch (type) {
      case MEMORY:
        return LOCAL;
      case HBASE:
        return HBASE;
      default:
        return LOCAL;
    }
  }
}
