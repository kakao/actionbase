# Actionbase DataFrame Conversion Guide

In the Actionbase project, all data models can be converted to DataFrames for processing. This document explains how to convert data models to DataFrames.

## Overview

Actionbase's data models are designed with the following structure:

1. `RowProvider<T>` - Interface that provides schema information and Row conversion/back-conversion functionality
2. Concrete data model classes (e.g., `Service`) - Implement `RowProvider<T>` to support DataFrame conversion

## How to Define Data Models

When defining a new data model, follow this pattern:

```java
@Value.Immutable
@Value.Style(allParameters = true)
@JsonSerialize(as = ImmutableYourModel.class)
@JsonDeserialize(as = ImmutableYourModel.class)
public interface YourModel extends RowProvider<YourModel> {

  // Field definitions
  String field1();
  int field2();
  boolean field3();

  // Schema definition (static)
  StructType SCHEMA =
      StructType.builder()
          .addField("field1", DataType.STRING)
          .addField("field2", DataType.INTEGER)
          .addField("field3", DataType.BOOLEAN)
          .build();

  // RowProvider interface implementation
  @Override
  default StructType schema() {
    return SCHEMA;
  }

  @Override
  default Object[] rawRow() {
    return RawRow.of(field1(), field2(), field3());
  }

  @Override
  default Row row() {
    return Row.of(schema(), rawRow());
  }

  @Override
  default YourModel valueOf(Row row) {
    return ImmutableYourModel.builder()
        .field1(row.get("field1").toString())
        .field2((Integer) row.get("field2"))
        .field3((Boolean) row.get("field3"))
        .build();
  }

  @Override
  default YourModel valueOf(Object[] rawRow) {
    return ImmutableYourModel.builder()
        .field1((String) rawRow[0])
        .field2((Integer) rawRow[1])
        .field3((Boolean) rawRow[2])
        .build();
  }
}
```

## DataFrame Conversion Methods

To convert a list of data models to a DataFrame, use the `DataFrameConverter` utility class:

```java
// Create data model list
List<YourModel> models = ...;

// Convert to DataFrame
DataFrame dataFrame = DataFrameConverter.toDataFrame(models);
```

To create an empty DataFrame:

```java
// Create empty DataFrame (no schema)
DataFrame emptyDataFrame = DataFrame.empty();

// Create empty DataFrame with schema
DataFrame emptyWithSchema = DataFrame.empty(YourModel.SCHEMA);

// Or use DataFrameConverter
YourModel schemaProvider = ...; // Instance for schema provision
DataFrame emptyDataFrame = DataFrameConverter.emptyDataFrame(YourModel.class, schemaProvider);
```

## Best Practices

1. Design all data models as immutable objects.
2. Define schemas as static constants for reuse.
3. Perform appropriate type casting and conversion in the `valueOf` method.
4. Use Stream API when processing large amounts of data.

## Notes

1. Row and DataFrame are immutable objects, so you must create a new instance when modification is needed.
2. Be careful as exceptions may occur if schema and data types do not match.
3. Pay attention to memory usage when processing large amounts of data.