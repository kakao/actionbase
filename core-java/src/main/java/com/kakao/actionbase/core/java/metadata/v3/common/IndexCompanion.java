package com.kakao.actionbase.core.java.metadata.v3.common;

import com.kakao.actionbase.core.java.dataframe.row.ArrayRow;
import com.kakao.actionbase.core.java.dataframe.row.ImmutableArrayRow;
import com.kakao.actionbase.core.java.types.ArrayType;
import com.kakao.actionbase.core.java.types.DataType;
import com.kakao.actionbase.core.java.types.ImmutableArrayType;
import com.kakao.actionbase.core.java.types.ImmutableStructType;
import com.kakao.actionbase.core.java.types.StructType;
import com.kakao.actionbase.core.java.util.FieldUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class IndexCompanion {

  public static final String INDEX_FIELD = "index";

  public static final String COMMENT_FIELD = "comment";

  public static final String FIELDS_FIELD = "fields";

  public static String index(ArrayRow row) {
    return row.getAsString(INDEX_FIELD);
  }

  public static String comment(ArrayRow row) {
    return row.getAsString(COMMENT_FIELD);
  }

  public static List<IndexField> fields(ArrayRow row) {
    ArrayRow[] rows = row.getAsRows(FIELDS_FIELD);
    List<IndexField> result = new ArrayList<>(rows.length);
    for (ArrayRow fieldRow : rows) {
      result.add(IndexFields.fromRow(fieldRow));
    }
    return result;
  }

  public static ArrayRow toRow(Index instance) {
    List<IndexField> fields = instance.fields();
    ArrayRow[] fieldAsRows = new ArrayRow[fields.size()];
    for (int i = 0; i < fields.size(); i++) {
      fieldAsRows[i] = IndexFields.toRow(fields.get(i));
    }
    return ImmutableArrayRow.builder()
        .data(instance.index(), instance.comment(), fieldAsRows)
        .schema(Schemas.SCHEMA)
        .build();
  }

  public static Index fromRow(ArrayRow row) {
    return ImmutableIndex.builder()
        .index(index(row))
        .comment(comment(row))
        .fields(fields(row))
        .build();
  }

  public static Map<String, Object> toField(Index instance) {
    return FieldUtils.mapOf(
        INDEX_FIELD,
        instance.index(),
        COMMENT_FIELD,
        instance.comment(),
        FIELDS_FIELD,
        FieldUtils.toFieldArray(instance.fields(), IndexFields::toField));
  }

  public static class Schemas {

    public static final StructType SCHEMA =
        ImmutableStructType.builder()
            .addField(INDEX_FIELD, DataType.STRING)
            .addField(COMMENT_FIELD, DataType.STRING)
            .addField(FIELDS_FIELD, IndexFields.Schemas.ARRAY_SCHEMA)
            .build();

    public static final ArrayType ARRAY_SCHEMA =
        ImmutableArrayType.builder().elementType(SCHEMA).build();
  }
}
