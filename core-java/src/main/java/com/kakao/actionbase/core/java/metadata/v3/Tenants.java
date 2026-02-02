package com.kakao.actionbase.core.java.metadata.v3;

import com.kakao.actionbase.core.java.constant.Constant;
import com.kakao.actionbase.core.java.dataframe.row.ArrayRow;
import com.kakao.actionbase.core.java.dataframe.row.ImmutableArrayRow;
import com.kakao.actionbase.core.java.edge.EdgePayload;
import com.kakao.actionbase.core.java.edge.EdgeState;
import com.kakao.actionbase.core.java.edge.ImmutableEdgePayload;
import com.kakao.actionbase.core.java.jackson.ActionbaseObjectMapper;
import com.kakao.actionbase.core.java.metadata.TenantId;
import com.kakao.actionbase.core.java.metadata.v3.common.DirectionType;
import com.kakao.actionbase.core.java.metadata.v3.common.EdgeSchema;
import com.kakao.actionbase.core.java.metadata.v3.common.MutationMode;
import com.kakao.actionbase.core.java.state.EventType;
import com.kakao.actionbase.core.java.types.DataType;
import com.kakao.actionbase.core.java.types.ImmutableStructType;
import com.kakao.actionbase.core.java.types.StructType;

import java.util.HashMap;
import java.util.Map;

public final class Tenants {

  public static final String ACTIVE_FIELD = "active";

  public static final String ORIGIN_FIELD = "origin";

  public static final String TENANT_FIELD = "tenant";

  public static final String API_VERSION_FIELD = "apiVersion";

  public static final String COMMENT_FIELD = "comment";

  static class R {

    public static boolean active(ArrayRow row) {
      return row.getAsBoolean(ACTIVE_FIELD);
    }

    public static String origin(ArrayRow row) {
      return row.getAsString(ORIGIN_FIELD);
    }

    public static String name(ArrayRow row) {
      return row.getAsString(TENANT_FIELD);
    }

    public static String comment(ArrayRow row) {
      return row.getAsString(COMMENT_FIELD);
    }
  }

  static class E {

    public static String origin(EdgePayload edge) {
      return DataType.STRING.cast(edge.source());
    }

    public static String name(EdgePayload edge) {
      return DataType.STRING.cast(edge.target());
    }

    public static String comment(EdgePayload edge) {
      return DataType.STRING.cast(edge.properties().get(COMMENT_FIELD));
    }
  }

  static class M {

    public static String source(TenantDescriptor instance) {
      return instance.origin();
    }

    public static String target(TenantDescriptor instance) {
      return instance.tenant();
    }

    public static Map.Entry<String, ? super Object> active(TenantDescriptor instance) {
      return new java.util.AbstractMap.SimpleEntry<>(ACTIVE_FIELD, instance.active());
    }

    public static Map.Entry<String, ? super Object> comment(TenantDescriptor instance) {
      return new java.util.AbstractMap.SimpleEntry<>(COMMENT_FIELD, instance.comment());
    }
  }

  public static ArrayRow toRow(TenantDescriptor instance) {
    return ImmutableArrayRow.builder()
        .data(instance.active(), instance.origin(), instance.tenant(), instance.comment())
        .schema(Schemas.SCHEMA)
        .build();
  }

  public static TenantDescriptor fromRow(ArrayRow row) {
    return ImmutableTenantDescriptor.builder()
        .active(R.active(row))
        .tenant(R.name(row))
        .comment(R.comment(row))
        .build();
  }

  public static EdgePayload toEdge(TenantDescriptor instance, long version) {
    return ImmutableEdgePayload.builder()
        .version(version)
        .source(M.source(instance))
        .target(M.target(instance))
        .putProperties(M.active(instance))
        .putProperties(M.comment(instance))
        .build();
  }

  public static TenantDescriptor fromEdge(EdgePayload edge) {
    return ImmutableTenantDescriptor.builder()
        .tenant(E.name(edge))
        .comment(E.comment(edge))
        .build();
  }

  public static EdgePayload toEdgePayload(TenantDescriptor instance) {
    return ImmutableEdgePayload.builder()
        .version(instance.updatedAt())
        .source(instance.origin())
        .target(instance.tenant())
        .putProperties(API_VERSION_FIELD, instance.apiVersion().name())
        .putProperties(COMMENT_FIELD, instance.comment())
        .build();
  }

  public static EdgeState toEdgeState(TenantDescriptor instance) {
    return toEdgePayload(instance).toEvent(EventType.INSERT).toState(Schemas.SCHEMA_SIMPLE);
  }

  public static String toJson(TenantDescriptor instance) {
    return ActionbaseObjectMapper.toJson(instance);
  }

  static class MM {
    //    default String origin() {
    //      return "origin";
    //    }
    //    String name();
    //
    //    String url();
    //
    //    String apiKey();
    //    Network network();
    //    Phase phase();
    //    default String comment() {
    //      return "";
    //    }
    //    String table();

    public static Object tenant(TenantDescriptor instance) {
      return instance.tenant();
    }
  }

  public static Map<String, Object> toMap(TenantDescriptor instance) {
    Map<String, Object> map = new HashMap<>();
    map.put("name", MM.tenant(instance));

    return map;
  }

  public static EdgeTableDescriptor tenantTable(TenantId tenantId) {
    return ImmutableEdgeTableDescriptor.builder()
        .tenant(tenantId.tenant())
        .database(Constant.SYSTEM_DATABASE_NAME)
        .table(Constant.Table.TENANT_NAME)
        .storage(Constant.Storage.METADATA_NAME)
        .schema(Schemas.EDGE_SCHEMA)
        .mode(MutationMode.DENY)
        .comment(Constant.Table.TENANT_COMMENT)
        .build();
  }

  public static class Schemas {

    public static final String TENANT_NOT_SPECIFIED = "NOT_SPECIFIED";

    public static final StructType SCHEMA =
        ImmutableStructType.builder()
            .addField(ACTIVE_FIELD, DataType.BOOLEAN)
            .addField(ORIGIN_FIELD, DataType.STRING)
            .addField(TENANT_FIELD, DataType.STRING)
            .addField(COMMENT_FIELD, DataType.STRING)
            .build();

    public static final StructType SCHEMA_SIMPLE =
        ImmutableStructType.builder()
            .addField(ACTIVE_FIELD, DataType.BOOLEAN)
            .addField(ORIGIN_FIELD, DataType.STRING)
            .addField(TENANT_FIELD, DataType.STRING)
            .addField(API_VERSION_FIELD, DataType.STRING)
            .addField(COMMENT_FIELD, DataType.STRING)
            .build();

    public static final EdgeSchema EDGE_SCHEMA =
        EdgeSchema.builder()
            .source(DataType.STRING, ORIGIN_FIELD)
            .target(DataType.STRING, TENANT_FIELD)
            .addProperties(COMMENT_FIELD, DataType.STRING)
            .direction(DirectionType.OUT)
            .addDefaultMetadataIndex()
            .build();
  }
}
