package com.kakao.actionbase.core.java.dataframe;

import com.kakao.actionbase.core.java.dataframe.column.Column;
import com.kakao.actionbase.core.java.dataframe.column.ImmutableColumn;
import com.kakao.actionbase.core.java.dataframe.row.Row;
import com.kakao.actionbase.core.java.types.common.StructField;

import java.util.List;

/** Helper class for DataFrame column operations. */
public class DataFrameAccessor {

  /**
   * Extracts the Column corresponding to the specified field from DataFrame.
   *
   * @param df DataFrame instance
   * @param fieldName Field name to extract
   * @return Column object for the field
   * @throws IllegalArgumentException If field name does not exist
   */
  public static Column getColumn(DataFrame df, String fieldName) {
    // Get field information from schema
    StructField field = df.schema().getField(fieldName);
    if (field == null) {
      throw new IllegalArgumentException(
          "Field name '" + fieldName + "' does not exist in schema.");
    }

    // Get field index
    int fieldIndex = df.schema().getFieldIndex(fieldName);

    // Pre-create data array
    Object[] columnData = new Object[fieldIndex];
    List<Row> rows = df.data();

    // Extract field value from each row in DataFrame
    for (int i = 0; i < rows.size(); i++) {
      columnData[i] = rows.get(i).get(fieldIndex);
    }

    // Create and return Column object
    return ImmutableColumn.builder().data(columnData).field(field).build();
  }

  public static Row getRow(DataFrame df, int index) {
    if (index < 0 || index >= df.count()) {
      throw new IndexOutOfBoundsException("Index is out of valid range: " + index);
    }
    return df.data().get(index);
  }

  public static List<Row> getRows(DataFrame dataFrame) {
    return dataFrame.data();
  }
}
