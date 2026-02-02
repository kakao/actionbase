package com.kakao.actionbase.core.java.metadata.v3;

import com.kakao.actionbase.core.java.constant.Constant;
import com.kakao.actionbase.core.java.dataframe.row.Row;
import com.kakao.actionbase.core.java.edge.Edge;
import com.kakao.actionbase.core.java.edge.EdgePayload;
import com.kakao.actionbase.core.java.edge.EdgeState;
import com.kakao.actionbase.core.java.edge.ImmutableEdgePayload;
import com.kakao.actionbase.core.java.jackson.ActionbaseObjectMapper;
import com.kakao.actionbase.core.java.metadata.ImmutableTableId;
import com.kakao.actionbase.core.java.metadata.TableId;
import com.kakao.actionbase.core.java.metadata.TenantId;
import com.kakao.actionbase.core.java.metadata.v3.common.DirectionType;
import com.kakao.actionbase.core.java.metadata.v3.common.EdgeSchema;
import com.kakao.actionbase.core.java.metadata.v3.common.EdgeSchemas;
import com.kakao.actionbase.core.java.metadata.v3.common.MutationMode;
import com.kakao.actionbase.core.java.metadata.v3.common.Schema;
import com.kakao.actionbase.core.java.metadata.v3.common.SchemaType;
import com.kakao.actionbase.core.java.metadata.v3.common.VertexSchema;
import com.kakao.actionbase.core.java.state.EventType;
import com.kakao.actionbase.core.java.types.DataType;
import com.kakao.actionbase.core.java.types.ImmutableStructType;
import com.kakao.actionbase.core.java.types.StructType;

import java.util.Map;

public final class Tables {

  public static final String ACTIVE_FIELD = "active";

  public static final String TENANT_FIELD = "tenant";

  public static final String DATABASE_FIELD = "database";

  public static final String TABLE_FIELD = "table";

  public static final String STORAGE_FIELD = "storage";

  public static final String TYPE_FIELD = "type";

  public static final String COMMENT_FIELD = "comment";

  public static final String SCHEMA_FIELD = "schema";

  public static final String MODE_FIELD = "mode";

  public static boolean active(Row row) {
    return row.getAsBoolean(ACTIVE_FIELD);
  }

  public static String tenant(Row row) {
    return row.getAsString(TENANT_FIELD);
  }

  public static String database(Row row) {
    return row.getAsString(DATABASE_FIELD);
  }

  public static String table(Row row) {
    return row.getAsString(TABLE_FIELD);
  }

  public static String storage(Row row) {
    return row.getAsString(STORAGE_FIELD);
  }

  public static String comment(Row row) {
    return row.getAsString(COMMENT_FIELD);
  }

  public static Schema schema(Row row) {
    return ActionbaseObjectMapper.fromObject(row.get(SCHEMA_FIELD), Schema.class);
  }

  public static MutationMode mode(Row row) {
    return MutationMode.valueOf(row.getAsString(MODE_FIELD));
  }

  public static String tenant(Edge edge) {
    return DataType.STRING.cast(edge.properties().get(TENANT_FIELD));
  }

  public static String database(Edge edge) {
    return DataType.STRING.cast(edge.source());
  }

  public static String table(Edge edge) {
    return DataType.STRING.cast(edge.target());
  }

  public static SchemaType type(Edge edge) {
    return SchemaType.valueOf(DataType.STRING.cast(edge.properties().get(TYPE_FIELD)));
  }

  public static String storage(Edge edge) {
    return DataType.STRING.cast(edge.properties().get(STORAGE_FIELD));
  }

  public static String comment(Edge edge) {
    return DataType.STRING.cast(edge.properties().get(COMMENT_FIELD));
  }

  public static EdgeSchema edgeSchema(Edge edge) {
    return ActionbaseObjectMapper.fromObject(edge.properties().get(SCHEMA_FIELD), EdgeSchema.class);
  }

  public static VertexSchema vertexSchema(Edge edge) {
    return ActionbaseObjectMapper.fromObject(
        edge.properties().get(SCHEMA_FIELD), VertexSchema.class);
  }

  public static MutationMode mode(Edge edge) {
    return MutationMode.valueOf(DataType.STRING.cast(edge.properties().get(MODE_FIELD)));
  }

  public static boolean active(TableDescriptor<?> instance) {
    return instance.active();
  }

  public static long version(TableDescriptor<?> instance) {
    return System.currentTimeMillis();
  }

  public static String source(TableDescriptor<?> instance) {
    return instance.database();
  }

  public static String target(TableDescriptor<?> instance) {
    return instance.table();
  }

  public static Map.Entry<String, ? super Object> tenant(TableDescriptor<?> instance) {
    return new java.util.AbstractMap.SimpleEntry<>(TENANT_FIELD, instance.tenant());
  }

  public static Map.Entry<String, ? super Object> type(TableDescriptor<?> instance) {
    return new java.util.AbstractMap.SimpleEntry<>(TYPE_FIELD, instance.type().name());
  }

  public static Map.Entry<String, ? super Object> storage(TableDescriptor<?> instance) {
    return new java.util.AbstractMap.SimpleEntry<>(STORAGE_FIELD, instance.storage());
  }

  public static Map.Entry<String, ? super Object> comment(TableDescriptor<?> instance) {
    return new java.util.AbstractMap.SimpleEntry<>(COMMENT_FIELD, instance.comment());
  }

  public static Map.Entry<String, ? super Object> schema(TableDescriptor<?> instance) {
    return new java.util.AbstractMap.SimpleEntry<>(
        SCHEMA_FIELD, ActionbaseObjectMapper.toJsonNode(instance.schema()));
  }

  public static Map.Entry<String, ? super Object> mode(TableDescriptor<?> instance) {
    return new java.util.AbstractMap.SimpleEntry<>(MODE_FIELD, instance.mode().name());
  }

  public static EdgePayload toEdgePayload(TableDescriptor<?> instance) {
    return ImmutableEdgePayload.builder()
        .version(instance.updatedAt())
        .source(source(instance))
        .target(target(instance))
        .putProperties(type(instance))
        .putProperties(tenant(instance))
        .putProperties(storage(instance))
        .putProperties(comment(instance))
        .putProperties(schema(instance))
        .putProperties(mode(instance))
        .build();
  }

  public static TableDescriptor<?> fromEdge(Edge edge) {
    SchemaType type = type(edge);
    if (type == SchemaType.EDGE) {
      return ImmutableEdgeTableDescriptor.builder()
          .tenant(tenant(edge))
          .database(database(edge))
          .table(table(edge))
          .storage(storage(edge))
          .comment(comment(edge))
          .schema(edgeSchema(edge))
          .mode(mode(edge))
          .build();
    } else if (type(edge) == SchemaType.VERTEX) {
      return ImmutableVertexTableDescriptor.builder()
          .tenant(tenant(edge))
          .database(database(edge))
          .table(table(edge))
          .storage(storage(edge))
          .comment(comment(edge))
          .schema(vertexSchema(edge))
          .mode(mode(edge))
          .build();
    } else {
      throw new IllegalArgumentException("Invalid edge type: " + type(edge));
    }
  }

  public static EdgeState toEdgeState2(TableDescriptor<?> instance) {
    return toEdgePayload(instance)
        .toEvent(EventType.INSERT)
        .toState(Schemas.EDGE_SCHEMA.getPropertiesSchema());
  }

  public static TableDescriptor<?> fromEdgeState(EdgeState state) {
    return fromEdge(state.toPayload());
  }

  public static EdgeTableDescriptor tableTable(TenantId tenantId) {
    return ImmutableEdgeTableDescriptor.builder()
        .tenant(tenantId.tenant())
        .database(Constant.SYSTEM_DATABASE_NAME)
        .table(Constant.Table.TABLE_NAME)
        .comment(Constant.Table.TABLE_COMMENT)
        .storage(Storages.Schemas.METADATA_NAME)
        .schema(Schemas.EDGE_SCHEMA)
        .mode(MutationMode.SYNC)
        .build();
  }

  public static class Schemas {

    public static final StructType SCHEMA =
        ImmutableStructType.builder()
            .addField(ACTIVE_FIELD, DataType.BOOLEAN)
            .addField(TENANT_FIELD, DataType.STRING)
            .addField(DATABASE_FIELD, DataType.STRING)
            .addField(TABLE_FIELD, DataType.STRING)
            .addField(STORAGE_FIELD, DataType.STRING)
            .addField(COMMENT_FIELD, DataType.STRING)
            .addField(SCHEMA_FIELD, EdgeSchemas.Schemas.SCHEMA)
            .addField(MODE_FIELD, DataType.STRING)
            .build();

    public static final EdgeSchema EDGE_SCHEMA =
        EdgeSchema.builder()
            .source(DataType.STRING, DATABASE_FIELD)
            .target(DataType.STRING, TABLE_FIELD)
            .addProperties(TYPE_FIELD, DataType.STRING)
            .addProperties(TENANT_FIELD, DataType.STRING)
            .addProperties(STORAGE_FIELD, DataType.STRING)
            .addProperties(COMMENT_FIELD, DataType.STRING)
            .addProperties(SCHEMA_FIELD, EdgeSchemas.Schemas.SCHEMA)
            .addProperties(MODE_FIELD, DataType.STRING)
            .direction(DirectionType.OUT)
            .addDefaultMetadataIndex()
            .build();

    public static TableId getTableId(TenantId tenantId) {
      return ImmutableTableId.of(
          tenantId.tenant(), Constant.SYSTEM_DATABASE_NAME, Constant.Table.TABLE_NAME);
    }
  }
}
