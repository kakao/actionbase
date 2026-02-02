package com.kakao.actionbase.core.java.metadata.v3.common;

import com.kakao.actionbase.core.java.codec.common.hbase.Order;
import com.kakao.actionbase.core.java.dataframe.row.ArrayRow;
import com.kakao.actionbase.core.java.dataframe.row.ImmutableArrayRow;
import com.kakao.actionbase.core.java.types.ArrayType;
import com.kakao.actionbase.core.java.types.DataType;
import com.kakao.actionbase.core.java.types.ImmutableArrayType;
import com.kakao.actionbase.core.java.types.ImmutableStructType;
import com.kakao.actionbase.core.java.types.StructType;
import com.kakao.actionbase.core.java.util.FieldUtils;

import java.util.Map;

public final class IndexFields {

  public static final String FIELD_FIELD = "field";

  public static final String ORDER_FIELD = "order";

  public static String field(ArrayRow row) {
    return row.getAsString(FIELD_FIELD);
  }

  public static String order(ArrayRow row) {
    return row.getAsString(ORDER_FIELD);
  }

  public static ArrayRow toRow(IndexField instance) {
    return ImmutableArrayRow.builder()
        .data(instance.field(), instance.order().name())
        .schema(Schemas.SCHEMA)
        .build();
  }

  public static IndexField fromRow(ArrayRow row) {
    return ImmutableIndexField.builder().field(field(row)).order(Order.valueOf(order(row))).build();
  }

  public static Map<String, Object> toField(IndexField instance) {
    return FieldUtils.mapOf(FIELD_FIELD, instance.field(), ORDER_FIELD, instance.order().name());
  }

  public static class Schemas {

    public static final StructType SCHEMA =
        ImmutableStructType.builder()
            .addField(FIELD_FIELD, DataType.STRING)
            .addField(ORDER_FIELD, DataType.STRING)
            .build();

    public static final ArrayType ARRAY_SCHEMA =
        ImmutableArrayType.builder().elementType(SCHEMA).build();
  }
}
