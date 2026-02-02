package com.kakao.actionbase.core.java.metadata.v3;

import com.kakao.actionbase.core.java.constant.Constant;
import com.kakao.actionbase.core.java.dataframe.row.ArrayRow;
import com.kakao.actionbase.core.java.edge.Edge;
import com.kakao.actionbase.core.java.edge.EdgePayload;
import com.kakao.actionbase.core.java.edge.EdgeState;
import com.kakao.actionbase.core.java.edge.ImmutableEdgePayload;
import com.kakao.actionbase.core.java.jackson.ActionbaseObjectMapper;
import com.kakao.actionbase.core.java.metadata.ImmutableTableId;
import com.kakao.actionbase.core.java.metadata.TableId;
import com.kakao.actionbase.core.java.metadata.TenantId;
import com.kakao.actionbase.core.java.metadata.v3.common.DatastoreType;
import com.kakao.actionbase.core.java.metadata.v3.common.DirectionType;
import com.kakao.actionbase.core.java.metadata.v3.common.EdgeSchema;
import com.kakao.actionbase.core.java.metadata.v3.common.MutationMode;
import com.kakao.actionbase.core.java.state.EventType;
import com.kakao.actionbase.core.java.types.DataType;
import com.kakao.actionbase.core.java.types.ImmutableStructType;
import com.kakao.actionbase.core.java.types.StructType;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

public final class Storages {

  public static final String ACTIVE_FIELD = "active";

  public static final String TENANT_FIELD = "tenant";

  public static final String TYPE_FIELD = "type";

  public static final String CONFIGURATION_FIELD = "configuration";

  public static final String DATABASE_FIELD = "database";

  public static final String STORAGE_FIELD = "storage";

  public static final String DATASTORE_FIELD = "datastore";

  public static final String COMMENT_FIELD = "comment";

  public static boolean active(ArrayRow row) {
    return row.getAsBoolean(ACTIVE_FIELD);
  }

  public static String tenant(ArrayRow row) {
    return row.getAsString(TENANT_FIELD);
  }

  public static String database(ArrayRow row) {
    return row.getAsString(DATABASE_FIELD);
  }

  public static String storage(ArrayRow row) {
    return row.getAsString(STORAGE_FIELD);
  }

  public static String comment(ArrayRow row) {
    return row.getAsString(COMMENT_FIELD);
  }

  public static DatastoreType type(Edge edge) {
    return DatastoreType.valueOf(DataType.STRING.cast(edge.properties().get(TYPE_FIELD)));
  }

  public static String tenant(Edge edge) {
    return DataType.STRING.cast(edge.properties().get(TENANT_FIELD));
  }

  public static String database(Edge edge) {
    return DataType.STRING.cast(edge.source());
  }

  public static String storage(Edge edge) {
    return DataType.STRING.cast(edge.target());
  }

  public static String comment(Edge edge) {
    return DataType.STRING.cast(edge.properties().get(COMMENT_FIELD));
  }

  public static String datastore(Edge edge) {
    return DataType.STRING.cast(edge.properties().get(DATASTORE_FIELD));
  }

  public static Map<String, Object> configuration(Edge edge) {
    return (Map<String, Object>)
        ActionbaseObjectMapper.INSTANCE.convertValue(
            edge.properties().get(CONFIGURATION_FIELD), Map.class);
  }

  public static boolean active(StorageDescriptor instance) {
    return instance.active();
  }

  public static long version(StorageDescriptor instance) {
    return System.currentTimeMillis();
  }

  public static String source(StorageDescriptor instance) {
    return instance.database();
  }

  public static String target(StorageDescriptor instance) {
    return instance.storage();
  }

  public static Map.Entry<String, ? super Object> type(StorageDescriptor instance) {
    return new java.util.AbstractMap.SimpleEntry<>(TYPE_FIELD, instance.type().name());
  }

  public static Map.Entry<String, ? super Object> tenant(StorageDescriptor instance) {
    return new java.util.AbstractMap.SimpleEntry<>(TENANT_FIELD, instance.tenant());
  }

  public static Map.Entry<String, ? super Object> comment(StorageDescriptor instance) {
    return new java.util.AbstractMap.SimpleEntry<>(COMMENT_FIELD, instance.comment());
  }

  public static Map.Entry<String, ? super Object> datastore(StorageDescriptor instance) {
    return new java.util.AbstractMap.SimpleEntry<>(DATASTORE_FIELD, instance.datastore());
  }

  public static Map.Entry<String, ? super Object> configuration(StorageDescriptor instance) {
    JsonNode node = ActionbaseObjectMapper.INSTANCE.valueToTree(instance.configuration());
    return new java.util.AbstractMap.SimpleEntry<>(CONFIGURATION_FIELD, node);
  }

  public static EdgePayload toEdgePayload(StorageDescriptor instance) {
    return ImmutableEdgePayload.builder()
        .version(instance.updatedAt())
        .source(source(instance))
        .target(target(instance))
        .putProperties(type(instance))
        .putProperties(tenant(instance))
        .putProperties(comment(instance))
        .putProperties(datastore(instance))
        .putProperties(configuration(instance))
        .build();
  }

  public static StorageDescriptor fromEdge(Edge edge) {
    return ImmutableStorageDescriptor.builder()
        .type(type(edge))
        .tenant(tenant(edge))
        .database(database(edge))
        .storage(storage(edge))
        .comment(comment(edge))
        .datastore(datastore(edge))
        .configuration(configuration(edge))
        .build();
  }

  public static EdgeState toEdgeState(StorageDescriptor instance) {
    return toEdgePayload(instance)
        .toEvent(EventType.INSERT)
        .toState(Storages.Schemas.EDGE_SCHEMA.getPropertiesSchema());
  }

  public static StorageDescriptor fromEdgeState(EdgeState state) {
    return fromEdge(state.toPayload());
  }

  public static String toJson(StorageDescriptor instance) {
    return ActionbaseObjectMapper.toJson(instance);
  }

  public static StorageDescriptor fromJson(String json) {
    return ActionbaseObjectMapper.fromJson(json, StorageDescriptor.class);
  }

  public static EdgeTableDescriptor storageTable(TenantId tenantId) {
    return ImmutableEdgeTableDescriptor.builder()
        .comment(Constant.Table.STORAGE_COMMENT)
        .tenant(tenantId.tenant())
        .database(Constant.SYSTEM_DATABASE_NAME)
        .table(Constant.Table.STORAGE_NAME)
        .storage(Storages.Schemas.METADATA_NAME)
        .schema(Schemas.EDGE_SCHEMA)
        .mode(MutationMode.SYNC)
        .build();
  }

  public static class Schemas {

    public static final String DEFAULT_STORAGE_NAME = "default";

    public static final String DEFAULT_STORAGE_COMMENT = "default storage";

    public static final String METADATA_NAME = "metadata";

    public static final String METADATA_COMMENT = "metadata storage";

    public static final String NIL_STORAGE_NAME = "nil";

    public static final String NIL_STORAGE_COMMENT = "nil storage";

    public static final StructType SCHEMA =
        ImmutableStructType.builder()
            .addField(ACTIVE_FIELD, DataType.BOOLEAN)
            .addField(TENANT_FIELD, DataType.STRING)
            .addField(DATABASE_FIELD, DataType.STRING)
            .addField(STORAGE_FIELD, DataType.STRING)
            .addField(COMMENT_FIELD, DataType.STRING)
            .build();

    public static final EdgeSchema EDGE_SCHEMA =
        EdgeSchema.builder()
            .source(DataType.STRING, DATABASE_FIELD)
            .target(DataType.STRING, STORAGE_FIELD)
            .addProperties(TYPE_FIELD, DataType.STRING)
            .addProperties(CONFIGURATION_FIELD, DataType.OBJECT)
            .addProperties(DATASTORE_FIELD, DataType.STRING)
            .addProperties(TENANT_FIELD, DataType.STRING)
            .addProperties(COMMENT_FIELD, DataType.STRING)
            .direction(DirectionType.OUT)
            .addDefaultMetadataIndex()
            .build();

    public static TableId getTableId(TenantId tenantId) {
      return ImmutableTableId.of(
          tenantId.tenant(), Constant.SYSTEM_DATABASE_NAME, Constant.Table.STORAGE_NAME);
    }
  }
}
