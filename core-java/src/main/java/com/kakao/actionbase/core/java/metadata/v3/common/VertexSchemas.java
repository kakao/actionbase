package com.kakao.actionbase.core.java.metadata.v3.common;

import com.kakao.actionbase.core.java.dataframe.row.ArrayRow;
import com.kakao.actionbase.core.java.dataframe.row.ImmutableArrayRow;
import com.kakao.actionbase.core.java.dataframe.row.Row;
import com.kakao.actionbase.core.java.types.DataType;
import com.kakao.actionbase.core.java.types.ImmutableStructType;
import com.kakao.actionbase.core.java.types.StructType;
import com.kakao.actionbase.core.java.types.common.Field;
import com.kakao.actionbase.core.java.types.common.Fields;
import com.kakao.actionbase.core.java.types.common.StructField;
import com.kakao.actionbase.core.java.types.common.StructFields;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class VertexSchemas {

  public static final String TYPE_FIELD = "type";

  public static final String KEY_FIELD = "key";

  public static final String FIELDS_FIELD = "fields";

  public static String type(ArrayRow row) {
    return row.getAsString(TYPE_FIELD);
  }

  public static Field key(ArrayRow row) {
    Objects.requireNonNull(row, "Row cannot be null");
    return Fields.fromRow(row.getAsRow(KEY_FIELD));
  }

  public static List<StructField> fields(ArrayRow row) {
    Objects.requireNonNull(row, "Row cannot be null");
    ArrayRow[] rowsArray = row.getAsRows(FIELDS_FIELD);
    List<StructField> result = new ArrayList<>(rowsArray.length);
    for (ArrayRow fieldRow : rowsArray) {
      result.add(StructFields.fromRow(fieldRow));
    }
    return result;
  }

  public static Row toRow(VertexSchema instance) {
    Objects.requireNonNull(instance, "VertexSchema cannot be null");
    List<StructField> fields = instance.properties();
    Row[] fieldAsRows = new ArrayRow[fields.size()];
    for (int i = 0; i < fields.size(); i++) {
      fieldAsRows[i] = StructFields.toRow(fields.get(i));
    }
    return ImmutableArrayRow.builder()
        .data(instance.type().name(), Fields.toRow(instance.key()), fieldAsRows)
        .schema(Schemas.SCHEMA)
        .build();
  }

  public static VertexSchema fromRow(ArrayRow row) {
    return ImmutableVertexSchema.builder().key(key(row)).properties(fields(row)).build();
  }

  public static class Schemas {

    public static final StructType SCHEMA =
        ImmutableStructType.builder()
            .addField(TYPE_FIELD, DataType.STRING)
            .addField(KEY_FIELD, Fields.Schemas.SCHEMA)
            .addField(FIELDS_FIELD, StructFields.Schemas.ARRAY_SCHEMA)
            .build();
  }
}
