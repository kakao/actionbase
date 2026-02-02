package com.kakao.actionbase.core.java.dataframe;

import com.kakao.actionbase.core.java.dataframe.row.ImmutableListRow;
import com.kakao.actionbase.core.java.dataframe.row.Row;
import com.kakao.actionbase.core.java.types.ImmutableStructType;
import com.kakao.actionbase.core.java.types.StructType;
import com.kakao.actionbase.core.java.types.common.StructField;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// package-private
class DataFrameTransformer {

  static DataFrame flatten(DataFrame df) {
    if (df.data().isEmpty()) {
      return df;
    }

    List<StructField> oldFields = df.schema().fields();
    Row firstRow = df.data().get(0);
    ImmutableStructType.Builder newSchemaBuilder = StructType.builder();

    // Store field mapping information
    List<FieldMapping> fieldMappings = new ArrayList<>();

    // Convert schema and create field mappings
    for (int i = 0; i < firstRow.size(); i++) {
      Object cell = firstRow.get(i);
      String originalName = oldFields.get(i).name();

      if (cell != null && cell.getClass().isArray()) {
        StructType nestedType = (StructType) oldFields.get(i).type();
        List<StructField> nestedFields = nestedType.fields();

        for (int j = 0; j < nestedFields.size(); j++) {
          String fieldName = originalName + "." + nestedFields.get(j).name();
          newSchemaBuilder.addField(
              fieldName, nestedFields.get(j).type(), nestedFields.get(j).comment());

          // Store nested field mapping information
          fieldMappings.add(new FieldMapping(i, j, true));
        }
      } else {
        newSchemaBuilder.addField(
            originalName, oldFields.get(i).type(), oldFields.get(i).comment());

        // Store regular field mapping information
        fieldMappings.add(new FieldMapping(i, -1, false));
      }
    }

    StructType newSchema = newSchemaBuilder.build();

    // Transform data
    List<Row> newData = new ArrayList<>();
    for (Row row : df.data()) {
      ImmutableListRow.Builder newRowBuilder = ImmutableListRow.builder().schema(newSchema);

      for (FieldMapping mapping : fieldMappings) {
        Object cell = row.get(mapping.originalIndex);

        if (mapping.isNested) {
          if (cell != null && cell.getClass().isArray()) {
            Object[] arrayCell = (Object[]) cell;
            int nestedIndex = mapping.nestedIndex;

            if (nestedIndex < arrayCell.length) {
              newRowBuilder.addData(arrayCell[nestedIndex]);
            } else {
              newRowBuilder.addData((Object) null); // Fill with null if array is shorter
            }
          } else {
            newRowBuilder.addData((Object) null); // Fill with null if not an array
          }
        } else {
          newRowBuilder.addData(cell);
        }
      }

      newData.add(newRowBuilder.build());
    }

    return ImmutableDataFrame.of(
        newData, newSchema, newData.size(), df.total(), df.offset(), df.hasNext());
  }

  public static DataFrame select(DataFrame df, String[] fieldNames) {
    List<Row> newData = new ArrayList<>();

    // Create new schema builder
    ImmutableStructType.Builder newSchemaBuilder = StructType.builder();

    // Create field index array
    int[] fieldIndices = new int[fieldNames.length];

    // Create new schema with only selected fields and index mapping
    for (int i = 0; i < fieldNames.length; i++) {
      String fieldName = fieldNames[i];
      int idx = df.schema().getFieldIndex(fieldName);
      if (idx == -1) {
        throw new IllegalArgumentException(
            "Field name '" + fieldName + "' does not exist in schema.");
      }
      fieldIndices[i] = idx;

      // Get field information from original schema and add to new schema
      StructField field = df.schema().getField(fieldName);
      newSchemaBuilder.addField(field.name(), field.type(), field.comment());
    }

    // Create new schema
    StructType newSchema = newSchemaBuilder.build();

    // Transform data
    for (Row row : df.data()) {
      ImmutableListRow.Builder newRowBuilder = ImmutableListRow.builder().schema(newSchema);
      for (int i = 0; i < fieldNames.length; i++) {
        newRowBuilder.addData(row.get(fieldIndices[i]));
      }
      newData.add(newRowBuilder.build());
    }

    return ImmutableDataFrame.of(
        newData, newSchema, newData.size(), df.total(), df.offset(), df.hasNext());
  }

  // Inner class to store field mapping information
  private static class FieldMapping {
    final int originalIndex; // Original field index
    final int nestedIndex; // Nested field index (-1 if not nested)
    final boolean isNested; // Whether it is a nested field

    FieldMapping(int originalIndex, int nestedIndex, boolean isNested) {
      this.originalIndex = originalIndex;
      this.nestedIndex = nestedIndex;
      this.isNested = isNested;
    }
  }

  /** Recursively flattens the given array (which may be nested) and returns it as a single List. */
  private static List<Object> flatten(Object array) {
    List<Object> result = new ArrayList<>();
    if (array != null && array.getClass().isArray()) {
      int len = java.lang.reflect.Array.getLength(array);
      for (int i = 0; i < len; i++) {
        Object element = java.lang.reflect.Array.get(array, i);
        if (element != null && element.getClass().isArray()) {
          result.addAll(flatten(element));
        } else {
          result.add(element);
        }
      }
    } else {
      result.add(array);
    }
    return result;
  }

  public static DataFrame take(DataFrame df, int n) {
    if (n >= df.data().size()) {
      return df;
    }
    return ImmutableDataFrame.of(df.data().subList(0, n), df.schema(), n, df.total(), null, false);
  }

  public static DataFrame union(DataFrame a, DataFrame b) {
    if (a.count() == 0) {
      return b;
    } else if (b.count() == 0) {
      return a;
    }
    if (!a.schema().equals(b.schema())) {
      throw new IllegalArgumentException("Cannot union DataFrames with different schemas");
    }
    List<Row> unionData = new ArrayList<>(a.data());
    unionData.addAll(b.data());
    return ImmutableDataFrame.of(
        unionData, a.schema(), a.count() + b.count(), a.total() + b.total(), null, false);
  }

  public static DataFrame filterNot(DataFrame df, String fieldName, Object value) {
    List<Row> filteredData = new ArrayList<>();
    int fieldIndex = df.schema().getFieldIndex(fieldName);
    for (Row row : df.data()) {
      if (!row.get(fieldIndex).equals(value)) {
        filteredData.add(row);
      }
    }
    return ImmutableDataFrame.of(
        filteredData, df.schema(), filteredData.size(), df.total(), null, false);
  }

  public static DataFrame filterNotIn(DataFrame dataFrame, String fieldName, Set<Object> values) {
    List<Row> filteredData = new ArrayList<>();
    int fieldIndex = dataFrame.schema().getFieldIndex(fieldName);
    for (Row row : dataFrame.data()) {
      if (!values.contains(row.get(fieldIndex))) {
        filteredData.add(row);
      }
    }
    return ImmutableDataFrame.of(
        filteredData, dataFrame.schema(), filteredData.size(), dataFrame.total(), null, false);
  }
}
