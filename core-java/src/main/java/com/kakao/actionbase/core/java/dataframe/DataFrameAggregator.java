package com.kakao.actionbase.core.java.dataframe;

import com.kakao.actionbase.core.java.codec.common.hbase.Order;
import com.kakao.actionbase.core.java.dataframe.row.ImmutableListRow;
import com.kakao.actionbase.core.java.dataframe.row.Row;
import com.kakao.actionbase.core.java.types.DataType;
import com.kakao.actionbase.core.java.types.StructType;
import com.kakao.actionbase.core.java.types.common.StructField;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DataFrameAggregator {

  /** Base aggregation function. Serves as the foundation for other aggregation functions. */
  private static DataFrame aggregate(
      DataFrame df,
      List<String> keyFields,
      Function<List<Row>, Object> aggregator,
      String aggregateName,
      DataType<?> aggregateType) {

    // Find key field indices
    int[] keyIndices = new int[keyFields.size()];
    for (int i = 0; i < keyFields.size(); i++) {
      keyIndices[i] = df.schema().getFieldIndex(keyFields.get(i));
      if (keyIndices[i] < 0) {
        throw new IllegalArgumentException("Field not found: " + keyFields.get(i));
      }
    }

    StructType.Builder resultSchemaBuilder = StructType.builder();

    // Create new schema for key fields
    // List<StructField> keyFieldSchemas = new ArrayList<>();
    for (int i = 0; i < keyFields.size(); i++) {
      int index = keyIndices[i];
      StructField field = df.schema().fields().get(index);

      resultSchemaBuilder.addField(field.name(), field.type(), field.comment());

      // keyFieldSchemas.add(ImmutableStructField.of(field.name(), field.type(), field.comment()));
    }

    StructType resultSchema =
        resultSchemaBuilder.addField(aggregateName, aggregateType, "Aggregated value").build();

    // Add aggregation result field
    // keyFieldSchemas.add(ImmutableStructField.of(aggregateName, aggregateType, "Aggregated
    // value"));
    // StructType resultSchema =
    //    ImmutableStructType.of(false, keyFieldSchemas.toArray(new StructField[0]));

    // Group and aggregate data
    Map<List<Object>, List<Row>> groups = new HashMap<>();
    for (Row row : df.data()) {
      List<Object> key = new ArrayList<>();
      for (int keyIndex : keyIndices) {
        key.add(row.get(keyIndex));
      }
      groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
    }

    // Create result data
    List<Row> resultData = new ArrayList<>();
    for (Map.Entry<List<Object>, List<Row>> entry : groups.entrySet()) {
      ImmutableListRow.Builder resultRowBuilder = ImmutableListRow.builder().schema(resultSchema);
      for (int i = 0; i < entry.getKey().size(); i++) {
        resultRowBuilder.addData(entry.getKey().get(i));
      }
      resultRowBuilder.addData(aggregator.apply(entry.getValue()));
      resultData.add(resultRowBuilder.build());
    }

    return DataFrame.builder()
        .data(resultData)
        .schema(resultSchema)
        .count(resultData.size())
        .build();
  }

  /** Groups by specified key fields and calculates the count for each group. */
  public static DataFrame groupByCount(DataFrame df, String... keyFields) {
    if (keyFields.length == 0) {
      throw new IllegalArgumentException("At least one key field is required.");
    }

    return aggregate(
        df, Arrays.asList(keyFields), rows -> (long) rows.size(), "COUNT(*)", DataType.LONG);
  }

  /** Groups by specified key fields and calculates the sum of value field for each group. */
  public static DataFrame groupBySum(DataFrame df, String valueField, String... keyFields) {
    if (keyFields.length == 0) {
      throw new IllegalArgumentException("At least one key field is required.");
    }

    final int valueIndex = df.schema().getFieldIndex(valueField);
    if (valueIndex < 0) {
      throw new IllegalArgumentException("Field not found: " + valueField);
    }

    return aggregate(
        df,
        Arrays.asList(keyFields),
        rows ->
            rows.stream()
                .map(row -> row.get(valueIndex))
                .filter(value -> value instanceof Number)
                .mapToDouble(value -> ((Number) value).doubleValue())
                .sum(),
        "SUM(" + valueField + ")",
        DataType.DOUBLE);
  }

  /**
   * Groups by specified key fields and calculates the number of distinct values in the value field.
   */
  public static DataFrame groupByCountDistinct(
      DataFrame df, String valueField, String... keyFields) {
    if (keyFields.length == 0) {
      throw new IllegalArgumentException("At least one key field is required.");
    }

    final int valueIndex = df.schema().getFieldIndex(valueField);
    if (valueIndex < 0) {
      throw new IllegalArgumentException("Field not found: " + valueField);
    }

    return aggregate(
        df,
        Arrays.asList(keyFields),
        rows -> rows.stream().map(row -> row.get(valueIndex)).distinct().count(),
        "COUNT(DISTINCT " + valueField + ")",
        DataType.LONG);
  }

  /**
   * Groups by specified key fields, calculates the count for each group, sorts by count in
   * descending order, and returns the top n.
   */
  public static DataFrame orderByCount(DataFrame df, int n, Order order, String... keyFields) {
    if (keyFields.length == 0) {
      throw new IllegalArgumentException("At least one key field is required.");
    }
    if (n <= 0) {
      throw new IllegalArgumentException("n must be greater than 0.");
    }

    DataFrame grouped = groupByCount(df, keyFields);
    List<Row> sortedData =
        grouped.data().stream()
            .sorted(
                (a, b) ->
                    order == Order.ASC
                        ? Long.compare((Long) a.get(a.size() - 1), (Long) b.get(b.size() - 1))
                        : Long.compare((Long) b.get(b.size() - 1), (Long) a.get(a.size() - 1)))
            .limit(n)
            .collect(Collectors.toList());

    return ImmutableDataFrame.of(
        sortedData, grouped.schema(), sortedData.size(), sortedData.size(), null, false);
  }

  /**
   * Groups by specified key fields, calculates the sum of value field for each group, sorts by sum
   * in descending order, and returns the top n.
   */
  public static DataFrame orderBySum(
      DataFrame df, String valueField, int n, Order order, String... keyFields) {
    if (keyFields.length == 0) {
      throw new IllegalArgumentException("At least one key field is required.");
    }
    if (n <= 0) {
      throw new IllegalArgumentException("n must be greater than 0.");
    }

    DataFrame grouped = groupBySum(df, valueField, keyFields);
    List<Row> sortedData =
        grouped.data().stream()
            .sorted(
                (a, b) ->
                    order == Order.ASC
                        ? Double.compare((Double) a.get(a.size() - 1), (Double) b.get(b.size() - 1))
                        : Double.compare(
                            (Double) b.get(b.size() - 1), (Double) a.get(a.size() - 1)))
            .limit(n)
            .collect(Collectors.toList());

    return ImmutableDataFrame.of(
        sortedData, grouped.schema(), sortedData.size(), sortedData.size(), null, false);
  }
}
