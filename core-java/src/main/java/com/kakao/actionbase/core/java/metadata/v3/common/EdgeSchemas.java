package com.kakao.actionbase.core.java.metadata.v3.common;

import com.kakao.actionbase.core.java.dataframe.row.ArrayRow;
import com.kakao.actionbase.core.java.dataframe.row.ImmutableArrayRow;
import com.kakao.actionbase.core.java.dataframe.row.Row;
import com.kakao.actionbase.core.java.jackson.ActionbaseObjectMapper;
import com.kakao.actionbase.core.java.types.DataType;
import com.kakao.actionbase.core.java.types.ImmutableStructType;
import com.kakao.actionbase.core.java.types.StructType;
import com.kakao.actionbase.core.java.types.common.Field;
import com.kakao.actionbase.core.java.types.common.Fields;
import com.kakao.actionbase.core.java.types.common.StructField;
import com.kakao.actionbase.core.java.types.common.StructFields;
import com.kakao.actionbase.core.java.util.FieldUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class EdgeSchemas {

  public static final String TYPE_FIELD = "type";

  public static final String SRC_FIELD = "source";

  public static final String TGT_FIELD = "target";

  public static final String FIELDS_FIELD = "properties";

  public static final String DIR_FIELD = "direction";

  public static final String INDICES_FIELD = "indexes";

  public static SchemaType type(ArrayRow row) {
    return SchemaType.valueOf(row.getAsString(TYPE_FIELD));
  }

  public static Field source(ArrayRow row) {
    return Fields.fromRow(row.getAsRow(SRC_FIELD));
  }

  public static Field target(ArrayRow row) {
    return Fields.fromRow(row.getAsRow(TGT_FIELD));
  }

  public static List<StructField> fields(ArrayRow row) {
    ArrayRow[] rowsArray = row.getAsRows(FIELDS_FIELD);
    List<StructField> result = new ArrayList<>(rowsArray.length);
    for (ArrayRow fieldRow : rowsArray) {
      result.add(StructFields.fromRow(fieldRow));
    }
    return result;
  }

  public static String dir(ArrayRow row) {
    return row.getAsString(DIR_FIELD);
  }

  public static List<Index> indices(ArrayRow row) {
    ArrayRow[] rowsArray = row.getAsRows(INDICES_FIELD);
    List<Index> result = new ArrayList<>(rowsArray.length);
    for (ArrayRow fieldRow : rowsArray) {
      result.add(IndexCompanion.fromRow(fieldRow));
    }
    return result;
  }

  public static Row toRow(EdgeSchema instance) {
    Objects.requireNonNull(instance, "VertexSchema cannot be null");
    List<StructField> fields = instance.properties();
    Row[] fieldAsRows = new ArrayRow[fields.size()];
    for (int i = 0; i < fields.size(); i++) {
      fieldAsRows[i] = StructFields.toRow(fields.get(i));
    }

    List<Index> indices = instance.indexes();
    ArrayRow[] indicesAsRows = new ArrayRow[indices.size()];
    for (int i = 0; i < indices.size(); i++) {
      indicesAsRows[i] = IndexCompanion.toRow(indices.get(i));
    }

    return ImmutableArrayRow.builder()
        .data(
            instance.type().name(),
            Fields.toRow(instance.source()),
            Fields.toRow(instance.target()),
            fieldAsRows,
            instance.direction().name(),
            indicesAsRows)
        .schema(Schemas.SCHEMA)
        .build();
  }

  public static EdgeSchema fromRow(ArrayRow row) {
    return ImmutableEdgeSchema.builder()
        .source(source(row))
        .target(target(row))
        .properties(fields(row))
        .direction(DirectionType.valueOf(dir(row)))
        .indexes(indices(row))
        .build();
  }

  public static Map<String, Object> toField(EdgeSchema instance) {
    return FieldUtils.mapOf(
        TYPE_FIELD,
        instance.type().name(),
        SRC_FIELD,
        ActionbaseObjectMapper.toJsonNode(instance.source()),
        TGT_FIELD,
        ActionbaseObjectMapper.toJsonNode(instance.target()),
        DIR_FIELD,
        instance.direction().name(),
        FIELDS_FIELD,
        ActionbaseObjectMapper.toJsonNode(instance.properties()),
        INDICES_FIELD,
        ActionbaseObjectMapper.toJsonNode(instance.indexes()));
  }

  public static EdgeSchema fromField(Map<String, Object> field) {
    return ActionbaseObjectMapper.fromObject(field, EdgeSchema.class);
  }

  public static class Schemas {

    public static final StructType SCHEMA =
        ImmutableStructType.builder()
            .addField(TYPE_FIELD, DataType.STRING)
            .addField(SRC_FIELD, Fields.Schemas.SCHEMA)
            .addField(TGT_FIELD, Fields.Schemas.SCHEMA)
            .addField(FIELDS_FIELD, StructFields.Schemas.ARRAY_SCHEMA)
            .addField(DIR_FIELD, DataType.STRING)
            .addField(INDICES_FIELD, IndexCompanion.Schemas.ARRAY_SCHEMA)
            .build();
  }
}
