package com.kakao.actionbase.core.java.dataframe;

import com.kakao.actionbase.core.java.dataframe.row.ImmutableArrayRow;
import com.kakao.actionbase.core.java.dataframe.row.Row;
import com.kakao.actionbase.core.java.types.DataType;
import com.kakao.actionbase.core.java.types.StructType;
import com.kakao.actionbase.core.java.types.common.StructField;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class DataFrameStatistics {

  /** Calculates statistical information for DataFrame. */
  public static DataFrame stats(DataFrame df) {
    List<StructField> fields = df.schema().fields();
    Row[] stats = new Row[fields.size()];

    StructType statsSchema =
        StructType.builder()
            .addField("column", DataType.STRING, "Column name")
            .addField("type", DataType.STRING, "Column type")
            .addField("count", DataType.LONG, "Total count")
            .addField("count_not_null", DataType.LONG, "Count of non-null values")
            .addField("cardinality", DataType.LONG, "Number of distinct values")
            .addField("mean", DataType.DOUBLE, "Mean value for numeric")
            .addField("std", DataType.DOUBLE, "Standard deviation for numeric")
            .addField("min", DataType.DOUBLE, "Minimum value for numeric")
            .addField("max", DataType.DOUBLE, "Maximum value for numeric")
            .build();

    for (int i = 0; i < fields.size(); i++) {
      StructField field = fields.get(i);
      String fieldName = field.name();
      DataType<?> fieldType = field.type();

      final int fieldIndex = i;
      List<Object> values =
          df.data().stream().map(row -> row.get(fieldIndex)).collect(Collectors.toList());

      List<Object> nonNullValues =
          values.stream().filter(Objects::nonNull).collect(Collectors.toList());

      long count = values.size();
      long countNotNull = nonNullValues.size();
      long cardinality = nonNullValues.stream().distinct().count();

      // Calculate statistics for numeric types
      Object[] numericStats = new Object[] {null, null, null, null};
      if (!nonNullValues.isEmpty()) {
        List<Double> numericValues =
            nonNullValues.stream()
                .map(
                    value -> {
                      if (value instanceof Number) {
                        return ((Number) value).doubleValue();
                      }
                      throw new IllegalArgumentException("Non-numeric value included: " + value);
                    })
                .collect(Collectors.toList());

        double mean = numericValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        double variance = 0.0;
        if (numericValues.size() > 1) {
          variance =
              numericValues.stream().mapToDouble(v -> Math.pow(v - mean, 2)).sum()
                  / (numericValues.size() - 1);
        }

        double std = Math.sqrt(variance);
        double min = numericValues.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double max = numericValues.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

        numericStats = new Object[] {mean, std, min, max};
      }

      stats[i] =
          ImmutableArrayRow.builder()
              .data(
                  fieldName,
                  fieldType.type(),
                  count,
                  countNotNull,
                  cardinality,
                  numericStats[0],
                  numericStats[1],
                  numericStats[2],
                  numericStats[3])
              .schema(statsSchema)
              .build();
    }

    return ImmutableDataFrame.of(
        Arrays.asList(stats), statsSchema, stats.length, stats.length, null, false);
  }
}
