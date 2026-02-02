package com.kakao.actionbase.core.java.types.common;

import com.kakao.actionbase.core.java.dataframe.row.ArrayRow;
import com.kakao.actionbase.core.java.dataframe.row.ImmutableArrayRow;
import com.kakao.actionbase.core.java.types.DataType;
import com.kakao.actionbase.core.java.types.ImmutableStructType;
import com.kakao.actionbase.core.java.types.StructType;

public final class Fields {

  public static final String TYPE_FIELD = "type";

  public static final String COMMENT_FIELD = "comment";

  public static DataType<?> type(ArrayRow row) {
    return DataType.valueOf(row.getAsString(TYPE_FIELD));
  }

  public static String comment(ArrayRow row) {
    return row.getAsString(COMMENT_FIELD);
  }

  public static ArrayRow toRow(Field instance) {
    return ImmutableArrayRow.builder()
        .data(instance.type().type(), instance.comment())
        .schema(Schemas.SCHEMA)
        .build();
  }

  public static Field fromRow(ArrayRow row) {
    return ImmutableField.builder().type(type(row)).comment(comment(row)).build();
  }

  public static class Schemas {

    public static final StructType SCHEMA =
        ImmutableStructType.builder()
            .addField(TYPE_FIELD, DataType.STRING)
            .addField(COMMENT_FIELD, DataType.STRING)
            .build();
  }
}
