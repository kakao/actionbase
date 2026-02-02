package com.kakao.actionbase.core.java.dataframe;

import com.kakao.actionbase.core.java.dataframe.row.Row;
import com.kakao.actionbase.core.java.types.DataType;
import com.kakao.actionbase.core.java.types.StructType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// package-private
class DataFrameFilter {

  public static DataFrame where(DataFrame df, WherePredicate predicate) {
    if (df == null || predicate == null) {
      return df;
    }

    List<Row> filteredData = new ArrayList<>();
    StructType schema = df.schema();

    for (Row row : df.data()) {
      if (evaluatePredicate(row, schema, predicate)) {
        filteredData.add(row);
      }
    }

    return ImmutableDataFrame.of(
        filteredData, schema, filteredData.size(), filteredData.size(), null, false);
  }

  private static boolean evaluatePredicate(Row row, StructType schema, WherePredicate predicate) {
    String key = predicate.key();

    if (predicate instanceof WherePredicate.In) {
      WherePredicate.In inPredicate = (WherePredicate.In) predicate;
      Object value = getNestedValue(row, schema, key);
      return inPredicate.values().contains(value);
    } else if (predicate instanceof WherePredicate.Eq) {
      WherePredicate.Eq eqPredicate = (WherePredicate.Eq) predicate;
      Object rowValue = getNestedValue(row, schema, key);
      Object predicateValue = eqPredicate.value();

      if (rowValue instanceof Object[] && predicateValue instanceof Object[]) {
        return Arrays.deepEquals((Object[]) rowValue, (Object[]) predicateValue);
      } else {
        return rowValue != null && rowValue.equals(predicateValue);
      }
    } else if (predicate instanceof WherePredicate.Gt) {
      WherePredicate.Gt gtPredicate = (WherePredicate.Gt) predicate;
      Object rowValue = getNestedValue(row, schema, key);
      if (rowValue == null) return false;

      double rowDouble = Double.parseDouble(rowValue.toString());
      double predicateDouble = Double.parseDouble(gtPredicate.value().toString());
      return rowDouble > predicateDouble;
    } else if (predicate instanceof WherePredicate.Gte) {
      WherePredicate.Gte gtePredicate = (WherePredicate.Gte) predicate;
      Object rowValue = getNestedValue(row, schema, key);
      if (rowValue == null) return false;

      double rowDouble = Double.parseDouble(rowValue.toString());
      double predicateDouble = Double.parseDouble(gtePredicate.value().toString());
      return rowDouble >= predicateDouble;
    } else if (predicate instanceof WherePredicate.Lt) {
      WherePredicate.Lt ltPredicate = (WherePredicate.Lt) predicate;
      Object rowValue = getNestedValue(row, schema, key);
      if (rowValue == null) return false;

      double rowDouble = Double.parseDouble(rowValue.toString());
      double predicateDouble = Double.parseDouble(ltPredicate.value().toString());
      return rowDouble < predicateDouble;
    } else if (predicate instanceof WherePredicate.Lte) {
      WherePredicate.Lte ltePredicate = (WherePredicate.Lte) predicate;
      Object rowValue = getNestedValue(row, schema, key);
      if (rowValue == null) return false;

      double rowDouble = Double.parseDouble(rowValue.toString());
      double predicateDouble = Double.parseDouble(ltePredicate.value().toString());
      return rowDouble <= predicateDouble;
    } else if (predicate instanceof WherePredicate.Between) {
      WherePredicate.Between betweenPredicate = (WherePredicate.Between) predicate;
      Object rowValue = getNestedValue(row, schema, key);
      if (rowValue == null) return false;

      double rowDouble = Double.parseDouble(rowValue.toString());
      double fromDouble = Double.parseDouble(betweenPredicate.fromValue().toString());
      double toDouble = Double.parseDouble(betweenPredicate.toValue().toString());
      return rowDouble >= fromDouble && rowDouble <= toDouble;
    } else if (predicate instanceof WherePredicate.IsNull) {
      Object rowValue = getNestedValue(row, schema, key);
      return rowValue == null;
    }

    return false;
  }

  private static Object getNestedValue(Row row, StructType schema, String path) {
    String[] parts = path.split("\\.");
    int fieldIndex = schema.getFieldIndex(parts[0]);

    if (fieldIndex < 0 || fieldIndex >= row.size()) {
      throw new IllegalArgumentException("Field not found: " + parts[0] + " in path " + path);
    }

    Object currentValue = row.get(fieldIndex);
    DataType<?> dataType = schema.fields().get(fieldIndex).type();
    if (dataType instanceof StructType) {
      StructType currentSchema = (StructType) dataType;

      for (int i = 1; i < parts.length; i++) {
        if (!(currentValue instanceof Object[]) || currentSchema == null) {
          throw new IllegalArgumentException("Invalid nested path: " + path);
        }

        int nestedFieldIndex = currentSchema.getFieldIndex(parts[i]);
        if (nestedFieldIndex == -1) {
          throw new IllegalArgumentException("Field not found: " + parts[i] + " in path " + path);
        }

        currentValue = ((Object[]) currentValue)[nestedFieldIndex];
        currentSchema = (StructType) currentSchema.fields().get(nestedFieldIndex).type();
      }
    }
    return currentValue;
  }
}
