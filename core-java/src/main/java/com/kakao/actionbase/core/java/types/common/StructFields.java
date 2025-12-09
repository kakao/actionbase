package com.kakao.actionbase.core.java.types.common;

import com.kakao.actionbase.core.java.dataframe.row.ArrayRow;
import com.kakao.actionbase.core.java.dataframe.row.ImmutableArrayRow;
import com.kakao.actionbase.core.java.dataframe.row.Row;
import com.kakao.actionbase.core.java.types.ArrayType;
import com.kakao.actionbase.core.java.types.DataType;
import com.kakao.actionbase.core.java.types.ImmutableArrayType;
import com.kakao.actionbase.core.java.types.ImmutableStructType;
import com.kakao.actionbase.core.java.types.StructType;

public final class StructFields {

  public static final String NAME_FIELD = "name";

  public static final String TYPE_FIELD = "type";

  public static final String COMMENT_FIELD = "comment";

  public static final String NULLABLE_FIELD = "nullable";

  public static String name(ArrayRow row) {
    return row.getAsString(NAME_FIELD);
  }

  public static DataType<?> type(ArrayRow row) {
    return DataType.valueOf(row.getAsString(TYPE_FIELD));
  }

  public static String comment(ArrayRow row) {
    return row.getAsString(COMMENT_FIELD);
  }

  public static boolean nullable(ArrayRow row) {
    return row.getAsBoolean(NULLABLE_FIELD);
  }

  public static Row toRow(StructField instance) {
    return ImmutableArrayRow.builder()
        .data(instance.name(), instance.type().type(), instance.comment(), instance.nullable())
        .schema(Schemas.SCHEMA)
        .build();
  }

  public static StructField fromRow(ArrayRow row) {
    return ImmutableStructField.builder()
        .name(name(row))
        .type(type(row))
        .comment(comment(row))
        .nullable(nullable(row))
        .build();
  }

  public static class Schemas {

    public static final StructType SCHEMA =
        ImmutableStructType.builder()
            .addField(NAME_FIELD, DataType.STRING)
            .addField(TYPE_FIELD, DataType.STRING)
            .addField(COMMENT_FIELD, DataType.STRING)
            .addField(NULLABLE_FIELD, DataType.BOOLEAN)
            .build();

    public static final ArrayType ARRAY_SCHEMA =
        ImmutableArrayType.builder().elementType(SCHEMA).build();
  }
}
