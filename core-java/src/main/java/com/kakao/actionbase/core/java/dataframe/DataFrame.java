package com.kakao.actionbase.core.java.dataframe;

import com.kakao.actionbase.core.java.annotation.AllowNulls;
import com.kakao.actionbase.core.java.annotation.Nullable;
import com.kakao.actionbase.core.java.codec.common.hbase.Order;
import com.kakao.actionbase.core.java.dataframe.column.Column;
import com.kakao.actionbase.core.java.dataframe.row.ArrayRow;
import com.kakao.actionbase.core.java.dataframe.row.EdgeRow;
import com.kakao.actionbase.core.java.dataframe.row.ListRow;
import com.kakao.actionbase.core.java.dataframe.row.MapRow;
import com.kakao.actionbase.core.java.dataframe.row.Row;
import com.kakao.actionbase.core.java.dataframe.row.RowType;
import com.kakao.actionbase.core.java.edge.EdgePayload;
import com.kakao.actionbase.core.java.payload.DataFramePayload;
import com.kakao.actionbase.core.java.payload.ImmutableDataFrameArrayPayload;
import com.kakao.actionbase.core.java.payload.ImmutableDataFrameEdgePayload;
import com.kakao.actionbase.core.java.payload.ImmutableDataFrameListPayload;
import com.kakao.actionbase.core.java.payload.ImmutableDataFrameMapPayload;
import com.kakao.actionbase.core.java.types.ImmutableStructType;
import com.kakao.actionbase.core.java.types.StructType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.immutables.value.Value;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/** Represents a collection of rows with a schema. DataFrame is immutable and thread-safe. */
@Value.Immutable
@Value.Style(allParameters = true)
@JsonSerialize(as = ImmutableDataFrame.class)
@JsonDeserialize(as = ImmutableDataFrame.class)
public abstract class DataFrame {

  @AllowNulls
  public abstract List<Row> data();

  public abstract StructType schema();

  public abstract int count();

  public abstract int total();

  @Nullable
  public abstract String offset();

  public abstract boolean hasNext();

  @JsonIgnore
  public boolean isEmpty() {
    return data().isEmpty();
  }

  public DataFramePayload toPayload() {
    RowType type = data().isEmpty() ? RowType.ARRAY : data().get(0).type();
    switch (type) {
      case ARRAY:
        List<Object[]> arrayData = new ArrayList<>(count());
        for (Row row : data()) {
          arrayData.add(((ArrayRow) row).data());
        }
        return ImmutableDataFrameArrayPayload.builder()
            .rows(arrayData)
            .total(total())
            .offset(offset())
            .hasNext(hasNext())
            .build();
      case LIST:
        List<List<Object>> listData = new ArrayList<>(count());
        for (Row row : data()) {
          listData.add(((ListRow) row).data());
        }
        return ImmutableDataFrameListPayload.builder()
            .rows(listData)
            .total(total())
            .offset(offset())
            .hasNext(hasNext())
            .build();
      case MAP:
        List<Map<String, Object>> mapData = new ArrayList<>(count());
        for (Row row : data()) {
          mapData.add(((MapRow) row).data());
        }
        return ImmutableDataFrameMapPayload.builder()
            .data(mapData)
            .total(total())
            .offset(offset())
            .hasNext(hasNext())
            .build();
      case EDGE:
        List<EdgePayload> edgeData = new ArrayList<>(count());
        for (Row row : data()) {
          edgeData.add(((EdgeRow) row).data());
        }
        return ImmutableDataFrameEdgePayload.builder()
            .edges(edgeData)
            .total(total())
            .offset(offset())
            .hasNext(hasNext())
            .build();
    }
    return null;
  }

  public static DataFrame fromActionbaseModel(
      List<Row> data, StructType schema, int count, int total, String offset, boolean hasNext) {
    return ImmutableDataFrame.of(data, schema, count, total, offset, hasNext);
  }

  public static DataFrame single(Row row) {
    return ImmutableDataFrame.of(Collections.singletonList(row), row.schema(), 1, 1, null, false);
  }

  public static DataFrame of(List<Row> data, StructType schema, int count) {
    return ImmutableDataFrame.of(data, schema, count, -1, null, false);
  }

  //  public static <T extends Rowable> DataFrame fromActionbaseModel(T model) {
  //    return fromActionbaseModel(Collections.singletonList(model));
  //  }

  //  public static <T extends Rowable> DataFrame fromActionbaseModel(List<T> models) {
  //    if (models.isEmpty()) {
  //      return DataFrame.empty();
  //    }
  //
  //    List<Row> rows = models.stream().map(Rowable::toRow).collect(Collectors.toList());

  //    Row firstRow = rows.get(0);
  //
  // List<Object[]> data = rows.stream().map().collect(Collectors.toList());

  //    return DataFrame.of(rows, firstRow.schema(), models.size());
  //  }

  static ImmutableDataFrame.Builder builder() {
    return ImmutableDataFrame.builder();
  }

  static class Schemas {
    // Empty schema definition
    static final StructType EMPTY_SCHEMA = ImmutableStructType.builder().build();

    // Empty DataFrame constant
    static final DataFrame EMPTY =
        ImmutableDataFrame.builder()
            .data(Collections.emptyList())
            .schema(EMPTY_SCHEMA)
            .count(0)
            .total(0)
            .offset(null)
            .hasNext(false)
            .build();
  }

  public static DataFrame empty() {
    return Schemas.EMPTY;
  }

  static DataFrame empty(StructType schema) {
    return ImmutableDataFrame.of(Collections.emptyList(), schema, 0, 0, null, false);
  }

  @Value.Check
  protected void check() {
    if (!data().isEmpty()) {
      Row firstRow = data().get(0);
      for (Row row : data()) {
        if (firstRow.getClass() != row.getClass()) {
          throw new IllegalArgumentException("Row classes do not match.");
        }
      }
    }
  }

  // === methods ===

  // # Printer

  public void show() {
    DataFramePrinter.show(this);
  }

  public void show(int maxRows, int truncate) {
    DataFramePrinter.show(this, maxRows, truncate);
  }

  public String showString() {
    return DataFramePrinter.showString(this);
  }

  public String showString(int maxRows, int truncate) {
    return DataFramePrinter.showString(this, maxRows, truncate);
  }

  // # Transformer

  public DataFrame flatten() {
    return DataFrameTransformer.flatten(this);
  }

  public DataFrame take(int n) {
    return DataFrameTransformer.take(this, n);
  }

  public DataFrame filterNot(String fieldName, Object value) {
    return DataFrameTransformer.filterNot(this, fieldName, value);
  }

  public DataFrame filterNotIn(String fieldName, Set<Object> values) {
    return DataFrameTransformer.filterNotIn(this, fieldName, values);
  }

  public DataFrame union(DataFrame other) {
    return DataFrameTransformer.union(this, other);
  }

  public DataFrame select(String... fieldNames) {
    return DataFrameTransformer.select(this, fieldNames);
  }

  // # Filter
  public DataFrame where(WherePredicate predicate) {
    return DataFrameFilter.where(this, predicate);
  }

  // # Aggregator

  public DataFrame groupByCount(String... keyFields) {
    return DataFrameAggregator.groupByCount(this, keyFields);
  }

  // # Statistics

  public DataFrame stats() {
    return DataFrameStatistics.stats(this);
  }

  public DataFrame groupBySum(String valueField, String... keyFields) {
    return DataFrameAggregator.groupBySum(this, valueField, keyFields);
  }

  public DataFrame groupByCountDistinct(String valueField, String... keyFields) {
    return DataFrameAggregator.groupByCountDistinct(this, valueField, keyFields);
  }

  public DataFrame orderByCount(int n, Order order, String... keyFields) {
    return DataFrameAggregator.orderByCount(this, n, order, keyFields);
  }

  public DataFrame orderBySum(String valueField, int n, Order order, String... keyFields) {
    return DataFrameAggregator.orderBySum(this, valueField, n, order, keyFields);
  }

  // # Accessor

  public Column getColumn(String fieldName) {
    return DataFrameAccessor.getColumn(this, fieldName);
  }

  public Row getRow(int index) {
    return DataFrameAccessor.getRow(this, index);
  }
}
