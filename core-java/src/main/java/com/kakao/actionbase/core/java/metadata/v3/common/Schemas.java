package com.kakao.actionbase.core.java.metadata.v3.common;

import com.kakao.actionbase.core.java.dataframe.row.ArrayRow;
import com.kakao.actionbase.core.java.dataframe.row.Row;
import com.kakao.actionbase.core.java.types.DataType;
import com.kakao.actionbase.core.java.types.common.Field;
import com.kakao.actionbase.core.java.types.common.StructField;

import java.util.Collections;

public final class Schemas {

  private Schemas() {}

  public static EdgeSchema EMPTY_EDGE =
      ImmutableEdgeSchema.builder()
          .source(Field.LONG_FIELD)
          .target(Field.STRING_FIELD)
          .properties(
              Collections.singletonList(
                  StructField.builder().name("created_at").type(DataType.LONG).build()))
          .direction(DirectionType.BOTH)
          .indexes(Collections.emptyList())
          .build();

  public static Schema fromRow(ArrayRow row) {
    row.schema().fields().forEach(field -> System.out.println(field.name()));
    String type = row.getAsString("type");
    if (SchemaType.VERTEX_TYPE.equals(type)) {
      return VertexSchemas.fromRow(row);
    } else if (SchemaType.EDGE_TYPE.equals(type)) {
      return EdgeSchemas.fromRow(row);
    } else {
      throw new IllegalArgumentException("Unknown schema type: " + type);
    }
  }

  public static Row toRow(Schema schema) {
    if (schema instanceof VertexSchema) {
      return VertexSchemas.toRow((VertexSchema) schema);
    } else if (schema instanceof EdgeSchema) {
      return EdgeSchemas.toRow((EdgeSchema) schema);
    } else {
      throw new IllegalArgumentException("Unknown schema type: " + schema.getClass());
    }
  }
}
