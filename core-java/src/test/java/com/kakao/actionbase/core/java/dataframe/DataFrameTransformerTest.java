package com.kakao.actionbase.core.java.dataframe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.kakao.actionbase.core.java.dataframe.row.ImmutableArrayRow;
import com.kakao.actionbase.core.java.dataframe.row.Row;
import com.kakao.actionbase.core.java.types.DataType;
import com.kakao.actionbase.core.java.types.ImmutableStructType;
import com.kakao.actionbase.core.java.types.StructType;
import com.kakao.actionbase.core.java.types.common.StructField;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class DataFrameTransformerTest {

  @Test
  @DisplayName("flatten method should flatten nested array structures")
  public void testFlatten() {
    // Create nested schema
    StructType nestedType =
        ImmutableStructType.builder()
            .addField("field1", DataType.STRING, "First field")
            .addField("field2", DataType.LONG, "Second field")
            .build();

    // Create root schema
    StructType schema =
        ImmutableStructType.builder()
            .addField("id", DataType.STRING, "ID")
            .addField("nested", nestedType, "Nested field")
            .addField("value", DataType.LONG, "Value")
            .build();

    // Create test data
    List<Object[]> data = new ArrayList<>();
    Object[] nestedData1 = new Object[] {"nested1", 10L};
    Object[] row1 = new Object[] {"id1", nestedData1, 100L};

    Object[] nestedData2 = new Object[] {"nested2", 20L};
    Object[] row2 = new Object[] {"id2", nestedData2, 200L};

    data.add(row1);
    data.add(row2);

    List<Row> rows = new ArrayList<>();
    for (Object[] row : data) {
      rows.add(ImmutableArrayRow.builder().data(row).schema(schema).build());
    }

    DataFrame df = ImmutableDataFrame.of(rows, schema, data.size(), data.size(), null, false);

    // Call flatten method
    DataFrame flattened = df.flatten();

    // Verify results
    assertNotNull(flattened);
    assertEquals(2, flattened.count(), "Row count should not change");
    assertEquals(4, flattened.schema().fields().size(), "Field count should be 4 after flattening");

    // Verify schema
    List<StructField> fields = flattened.schema().fields();
    assertEquals("id", fields.get(0).name());
    assertEquals("nested.field1", fields.get(1).name());
    assertEquals("nested.field2", fields.get(2).name());
    assertEquals("value", fields.get(3).name());

    // Verify data
    List<Row> flattenedData = flattened.data();
    //    assertEquals("id1", flattenedData.get(0).get(0));
    //    assertEquals("nested1", flattenedData.get(0).get(1));
    //    assertEquals(10L, flattenedData.get(0).get(2));
    //    assertEquals(100L, flattenedData.get(0).get(3));
    //
    //    assertEquals("id2", flattenedData.get(1).get(0));
    //    assertEquals("nested2", flattenedData.get(1).get(1));
    //    assertEquals(20L, flattenedData.get(1).get(2));
    //    assertEquals(200L, flattenedData.get(1).get(3));
  }

  @Test
  @DisplayName("flatten method should handle different array lengths correctly")
  public void testFlattenWithDifferentArrayLengths() {
    // Create nested schema
    StructType nestedType =
        ImmutableStructType.builder()
            .addField("field1", DataType.STRING, "First field")
            .addField("field2", DataType.LONG, "Second field")
            .addField("field3", DataType.BOOLEAN, "Third field")
            .build();

    // Create root schema
    StructType schema =
        ImmutableStructType.builder()
            .addField("id", DataType.STRING, "ID")
            .addField("nested", nestedType, "Nested field")
            .build();

    // Create test data - different array lengths
    List<Object[]> data = new ArrayList<>();
    // First row includes all fields
    Object[] nestedData1 = new Object[] {"nested1", 10L, true};
    Object[] row1 = new Object[] {"id1", nestedData1};

    // Second row has shorter array length
    Object[] nestedData2 = new Object[] {"nested2", 20L}; // field3 missing
    Object[] row2 = new Object[] {"id2", nestedData2};

    data.add(row1);
    data.add(row2);

    List<Row> rows = new ArrayList<>();
    for (Object[] row : data) {
      rows.add(ImmutableArrayRow.builder().data(row).schema(schema).build());
    }

    DataFrame df = ImmutableDataFrame.of(rows, schema, data.size(), data.size(), null, false);

    // Call flatten method
    DataFrame flattened = df.flatten();

    // Verify results
    assertNotNull(flattened);
    assertEquals(2, flattened.count(), "Row count should not change");
    assertEquals(4, flattened.schema().fields().size(), "Field count should be 4 after flattening");

    // Verify schema
    List<StructField> fields = flattened.schema().fields();
    assertEquals("id", fields.get(0).name());
    assertEquals("nested.field1", fields.get(1).name());
    assertEquals("nested.field2", fields.get(2).name());
    assertEquals("nested.field3", fields.get(3).name());

    // Verify data
    List<Row> flattenedData = flattened.data();
    //    assertEquals("id1", flattenedData.get(0).get(0));
    //    assertEquals("nested1", flattenedData.get(0).get(1));
    //    assertEquals(10L, flattenedData.get(0).get(2));
    //    assertEquals(true, flattenedData.get(0).get(3));
    //
    //    assertEquals("id2", flattenedData.get(1).get(0));
    //    assertEquals("nested2", flattenedData.get(1).get(1));
    //    assertEquals(20L, flattenedData.get(1).get(2));
    //    assertEquals(null, flattenedData.get(1).get(3), "Missing field should be null");
  }
}
