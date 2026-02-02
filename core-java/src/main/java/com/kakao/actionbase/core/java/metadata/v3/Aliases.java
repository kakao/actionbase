package com.kakao.actionbase.core.java.metadata.v3;

import com.kakao.actionbase.core.java.constant.Constant;
import com.kakao.actionbase.core.java.dataframe.row.ImmutableArrayRow;
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
import com.kakao.actionbase.core.java.metadata.v3.common.MutationMode;
import com.kakao.actionbase.core.java.types.DataType;
import com.kakao.actionbase.core.java.types.ImmutableStructType;
import com.kakao.actionbase.core.java.types.StructType;

import java.util.HashMap;
import java.util.Map;

public final class Aliases {

  public static final String ACTIVE_FIELD = "active";

  public static final String TENANT_FIELD = "tenant";

  public static final String DATABASE_FIELD = "database";

  public static final String ALIAS_FIELD = "alias";

  public static final String TABLE_FIELD = "table";

  public static final String COMMENT_FIELD = "comment";

  public static boolean active(Row row) {
    return row.getAsBoolean(ACTIVE_FIELD);
  }

  public static String tenant(Row row) {
    return row.getAsString(TENANT_FIELD);
  }

  public static String database(Row row) {
    return row.getAsString(DATABASE_FIELD);
  }

  public static String alias(Row row) {
    return row.getAsString(ALIAS_FIELD);
  }

  public static String table(Row row) {
    return row.getAsString(TABLE_FIELD);
  }

  public static String comment(Row row) {
    return row.getAsString(COMMENT_FIELD);
  }

  public static Row toRow(AliasDescriptor instance) {
    return ImmutableArrayRow.builder()
        .data(
            instance.active(),
            instance.tenant(),
            instance.database(),
            instance.alias(),
            instance.table(),
            instance.comment())
        .schema(Schemas.SCHEMA)
        .build();
  }

  public static AliasDescriptor fromRow(Row row) {
    return ImmutableAliasDescriptor.builder()
        .active(active(row))
        .tenant(tenant(row))
        .database(database(row))
        .alias(alias(row))
        .table(table(row))
        .comment(comment(row))
        .build();
  }

  public static String tenant(Edge edge) {
    return DataType.STRING.cast(edge.properties().get(TENANT_FIELD));
  }

  public static String database(Edge edge) {
    return DataType.STRING.cast(edge.source());
  }

  public static String alias(Edge edge) {
    return DataType.STRING.cast(edge.target());
  }

  public static String table(Edge edge) {
    return DataType.STRING.cast(edge.properties().get(TABLE_FIELD));
  }

  public static String comment(Edge edge) {
    return DataType.STRING.cast(edge.properties().get(COMMENT_FIELD));
  }

  public static boolean active(AliasDescriptor instance) {
    return instance.active();
  }

  public static long version(AliasDescriptor instance) {
    return System.currentTimeMillis();
  }

  public static String source(AliasDescriptor instance) {
    return instance.database();
  }

  public static String target(AliasDescriptor instance) {
    return instance.alias();
  }

  public static Map.Entry<String, ? super Object> tenant(AliasDescriptor instance) {
    return new java.util.AbstractMap.SimpleEntry<>(TENANT_FIELD, instance.tenant());
  }

  public static Map.Entry<String, ? super Object> table(AliasDescriptor instance) {
    return new java.util.AbstractMap.SimpleEntry<>(TABLE_FIELD, instance.table());
  }

  public static Map.Entry<String, ? super Object> comment(AliasDescriptor instance) {
    return new java.util.AbstractMap.SimpleEntry<>(COMMENT_FIELD, instance.comment());
  }

  public static EdgePayload toEdge(AliasDescriptor instance) {
    return ImmutableEdgePayload.builder()
        .version(instance.updatedAt())
        .source(source(instance))
        .target(target(instance))
        .putProperties(tenant(instance))
        .putProperties(table(instance))
        .putProperties(comment(instance))
        .build();
  }

  public static AliasDescriptor fromEdge(Edge edge) {
    return ImmutableAliasDescriptor.builder()
        .tenant(tenant(edge))
        .database(database(edge))
        .alias(alias(edge))
        .table(table(edge))
        .comment(comment(edge))
        .build();
  }

  public static AliasDescriptor fromEdgeState(EdgeState state) {
    return fromEdge(state.toPayload());
  }

  public static String toJson(AliasDescriptor instance) {
    return ActionbaseObjectMapper.toJson(instance);
  }

  public static AliasDescriptor fromJson(String json) {
    return ActionbaseObjectMapper.fromJson(json, AliasDescriptor.class);
  }

  static class M {
    static Object tenant(AliasDescriptor instance) {
      return instance.tenant();
    }

    static Object database(AliasDescriptor instance) {
      return instance.database();
    }

    static Object alias(AliasDescriptor instance) {
      return instance.alias();
    }

    static Object table(AliasDescriptor instance) {
      return instance.table();
    }

    static Object comment(AliasDescriptor instance) {
      return instance.comment();
    }
  }

  public static Map<String, Object> toMap(AliasDescriptor instance) {
    Map<String, Object> map = new HashMap<>();
    map.put("tenant", M.tenant(instance));
    map.put("database", M.database(instance));
    map.put("alias", M.alias(instance));
    map.put("table", M.table(instance));
    map.put("comment", M.comment(instance));
    return map;
  }

  public static EdgeTableDescriptor aliasTable(TenantId tenantId) {
    return ImmutableEdgeTableDescriptor.builder()
        .comment(Constant.Table.ALIAS_COMMENT)
        .tenant(tenantId.tenant())
        .database(Constant.SYSTEM_DATABASE_NAME)
        .table(Constant.Table.ALIAS_NAME)
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
            .addField(ALIAS_FIELD, DataType.STRING)
            .addField(TABLE_FIELD, DataType.STRING)
            .addField(COMMENT_FIELD, DataType.STRING)
            .build();

    public static final EdgeSchema EDGE_SCHEMA =
        EdgeSchema.builder()
            .source(DataType.STRING, TENANT_FIELD)
            .target(DataType.STRING, DATABASE_FIELD)
            .addProperties(TENANT_FIELD, DataType.STRING)
            .addProperties(TABLE_FIELD, DataType.STRING)
            .addProperties(COMMENT_FIELD, DataType.STRING)
            .direction(DirectionType.OUT)
            .addDefaultMetadataIndex()
            .build();

    public static TableId getTableId(TenantId tenantId) {
      return ImmutableTableId.of(
          tenantId.tenant(), Constant.SYSTEM_DATABASE_NAME, Constant.Table.ALIAS_NAME);
    }
  }
}
