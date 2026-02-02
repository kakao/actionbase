package com.kakao.actionbase.core.java.metadata.v3;

import com.kakao.actionbase.core.java.constant.Constant;
import com.kakao.actionbase.core.java.edge.Edge;
import com.kakao.actionbase.core.java.edge.EdgePayload;
import com.kakao.actionbase.core.java.edge.EdgeState;
import com.kakao.actionbase.core.java.edge.ImmutableEdgePayload;
import com.kakao.actionbase.core.java.jackson.ActionbaseObjectMapper;
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

public final class Datastores {

  public static final String DEFAULT_DATASTORE_NAME = "default";

  public static final String NIL_DATASTORE_NAME = "nil";

  public static final String NIL_DATASTORE_COMMENT = "nil datastore";

  public static final String ACTIVE_FIELD = "active";

  public static final String TENANT_FIELD = "tenant";

  public static final String TYPE_FIELD = "type";

  public static final String DATASTORE_FIELD = "datastore";

  public static final String COMMENT_FIELD = "comment";

  public static final String CONFIGURATION_FIELD = "configuration";

  static class E {

    public static String tenant(Edge edge) {
      return DataType.STRING.cast(edge.source());
    }

    public static DatastoreType type(Edge edge) {
      return DatastoreType.valueOf(DataType.STRING.cast(edge.properties().get(TYPE_FIELD)));
    }

    public static String datastore(Edge edge) {
      return DataType.STRING.cast(edge.target());
    }

    public static String comment(Edge edge) {
      return DataType.STRING.cast(edge.properties().get(COMMENT_FIELD));
    }

    public static Map<String, String> configuration(Edge edge) {
      return (Map<String, String>)
          ActionbaseObjectMapper.INSTANCE.convertValue(
              edge.properties().get(CONFIGURATION_FIELD), Map.class);
    }
  }

  static class M {

    public static boolean active(DatastoreDescriptor instance) {
      return instance.active();
    }

    public static long version(DatastoreDescriptor instance) {
      return System.currentTimeMillis();
    }

    public static String source(DatastoreDescriptor instance) {
      return instance.tenant();
    }

    public static String target(DatastoreDescriptor instance) {
      return instance.datastore();
    }

    public static Map.Entry<String, ? super Object> type(DatastoreDescriptor instance) {
      return new java.util.AbstractMap.SimpleEntry<>(TYPE_FIELD, instance.type().name());
    }

    public static Map.Entry<String, ? super Object> comment(DatastoreDescriptor instance) {
      return new java.util.AbstractMap.SimpleEntry<>(COMMENT_FIELD, instance.comment());
    }

    public static Map.Entry<String, ? super Object> configuration(DatastoreDescriptor instance) {
      JsonNode node = ActionbaseObjectMapper.INSTANCE.valueToTree(instance.configuration());
      return new java.util.AbstractMap.SimpleEntry<>(CONFIGURATION_FIELD, node);
    }
  }

  public static EdgePayload toEdgePayload(DatastoreDescriptor instance) {
    return ImmutableEdgePayload.builder()
        .version(instance.updatedAt())
        .source(M.source(instance))
        .target(M.target(instance))
        .putProperties(M.type(instance))
        .putProperties(M.comment(instance))
        .putProperties(M.configuration(instance))
        .build();
  }

  public static DatastoreDescriptor fromEdge(Edge edge) {
    return ImmutableDatastoreDescriptor.builder()
        .type(E.type(edge))
        .tenant(E.tenant(edge))
        .datastore(E.datastore(edge))
        .comment(E.comment(edge))
        .configuration(E.configuration(edge))
        .build();
  }

  public static EdgeState toEdgeState(DatastoreDescriptor instance) {
    return toEdgePayload(instance)
        .toEvent(EventType.INSERT)
        .toState(Datastores.Schemas.EDGE_SCHEMA.getPropertiesSchema());
  }

  public static DatastoreDescriptor fromEdgeState(EdgeState state) {
    return fromEdge(state.toPayload());
  }

  public static String toJson(DatastoreDescriptor instance) {
    return ActionbaseObjectMapper.toJson(instance);
  }

  public static DatastoreDescriptor fromJson(String json) {
    return ActionbaseObjectMapper.fromJson(json, DatastoreDescriptor.class);
  }

  public static EdgeTableDescriptor datastoreTable(TenantId tenantId) {
    return ImmutableEdgeTableDescriptor.builder()
        .comment(Constant.Table.DATASTORE_COMMENT)
        .tenant(tenantId.tenant())
        .database(Constant.SYSTEM_DATABASE_NAME)
        .table(Constant.Table.DATASTORE_NAME)
        .storage(Storages.Schemas.METADATA_NAME)
        .schema(Schemas.EDGE_SCHEMA)
        .mode(MutationMode.DENY)
        .build();
  }

  public static class Schemas {

    public static final StructType SCHEMA =
        ImmutableStructType.builder()
            .addField(ACTIVE_FIELD, DataType.BOOLEAN)
            .addField(TENANT_FIELD, DataType.STRING)
            .addField(DATASTORE_FIELD, DataType.STRING)
            .addField(COMMENT_FIELD, DataType.STRING)
            .build();

    public static final EdgeSchema EDGE_SCHEMA =
        EdgeSchema.builder()
            .source(DataType.STRING, TENANT_FIELD)
            .target(DataType.STRING, DATASTORE_FIELD)
            .addProperties(TYPE_FIELD, DataType.STRING)
            .addProperties(COMMENT_FIELD, DataType.STRING)
            .addProperties(CONFIGURATION_FIELD, DataType.OBJECT)
            .direction(DirectionType.OUT)
            .addDefaultMetadataIndex()
            .build();
  }
}
