package com.kakao.actionbase.core.java.constant;

import com.kakao.actionbase.core.java.metadata.v3.Storages;

import java.util.Arrays;
import java.util.List;

public class Constant {

  public static final String SYSTEM_DATABASE_NAME = "sys";

  public static final String SYSTEM_DATABASE_COMMENT = "system database";

  public static final String DEFAULT_COMMENT = "";

  public static final long DEFAULT_CREATED_AT = -1L;

  public static final String DEFAULT_CREATED_BY = "";

  public static final long DEFAULT_UPDATED_AT = -1L;

  public static final String DEFAULT_UPDATED_BY = "";

  public static final long DEFAULT_REVISION = -1L;

  public static class Datastore {
    public static final String MEMORY_NAME = "memory";
    public static final String MEMORY_COMMENT = "default memstore";

    public static final String DEFAULT_NAME = "default";
    public static final String DEFAULT_COMMENT = "default datastore";

    public static final String METADATA_NAME = "metadata";
    public static final String METADATA_COMMENT = "metadata datastore";

    public static final String NIL_NAME = "nil";
    public static final String NIL_COMMENT = "nil datastore";

    public static final List<String> ALL_SYSTEM_DATASTORE =
        Arrays.asList(DEFAULT_NAME, METADATA_NAME, NIL_NAME);
  }

  public static class Storage {
    // sys.default
    public static final String DEFAULT_NAME = "default";
    public static final String DEFAULT_COMMENT = "default storage";

    // sys.metadata
    public static final String METADATA_NAME = Storages.Schemas.METADATA_NAME;
    public static final String METADATA_COMMENT = Storages.Schemas.METADATA_COMMENT;

    public static final String NIL_NAME = "nil";
    public static final String NIL_COMMENT = "nil storage";

    public static final List<String> ALL_SYSTEM_STORAGES =
        Arrays.asList(DEFAULT_NAME, METADATA_NAME, NIL_NAME);
  }

  public static class Table {
    // sys.tenant
    public static final String TENANT_NAME = "tenant";
    public static final String TENANT_COMMENT = "sys.tenant table";
    // sys.datastore
    public static final String DATASTORE_NAME = "datastore";
    public static final String DATASTORE_COMMENT = "sys.datastore table";
    // sys.database
    public static final String DATABASE_NAME = "database";
    public static final String DATABASE_COMMENT = "sys.database table";
    // sys.table
    public static final String TABLE_NAME = "table";
    public static final String TABLE_COMMENT = "sys.table table";
    // sys.storage
    public static final String STORAGE_NAME = "storage";
    public static final String STORAGE_COMMENT = "sys.storage table";
    // sys.alias
    public static final String ALIAS_NAME = "alias";
    public static final String ALIAS_COMMENT = "sys.alias table";

    public static final List<String> ALL_SYSTEM_TABLES =
        Arrays.asList(
            TENANT_NAME, DATASTORE_NAME, DATABASE_NAME, TABLE_NAME, STORAGE_NAME, ALIAS_NAME);
  }

  public static class Codec {
    public static final int DEFAULT_POOL_SIZE = 100;

    public static final int DEFAULT_BUFFER_SIZE = 5120;

    public static final Byte BYTE_FALSE = 0;
    public static final Byte BYTE_TRUE = 1;
  }

  public static class EdgeField {
    public static final String VERSION_V2 = "ts";
    public static final String SOURCE_V2 = "src";
    public static final String TARGET_V2 = "tgt";
    public static final String PROPERTIES_V2 = "props";

    public static final String VERSION_V3 = "version";
    public static final String SOURCE_V3 = "source";
    public static final String TARGET_V3 = "target";
    public static final String PROPERTIES_V3 = "properties";
  }

  public static class Infrastructure {
    public static final String EMBEDDED_VALUE = "embedded";

    public static class Kafka {
      public static final String BOOTSTRAP_SERVERS = "bootstrap.servers";
    }

    public static class HBase {
      public static final String AUTHENTICATION = "hbase.security.authentication";
      public static final String ZOOKEEPER_QUORUM = "hbase.zookeeper.quorum";
      public static final String BOOTSTRAP_SERVERS = "hbase.client.bootstrap.servers";
    }
  }
}
