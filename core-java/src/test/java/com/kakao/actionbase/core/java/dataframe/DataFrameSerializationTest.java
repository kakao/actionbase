package com.kakao.actionbase.core.java.dataframe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kakao.actionbase.core.java.dataframe.row.ArrayRow;
import com.kakao.actionbase.core.java.dataframe.row.EdgeRow;
import com.kakao.actionbase.core.java.dataframe.row.ImmutableArrayRow;
import com.kakao.actionbase.core.java.dataframe.row.ImmutableEdgeRow;
import com.kakao.actionbase.core.java.dataframe.row.ImmutableListRow;
import com.kakao.actionbase.core.java.dataframe.row.ImmutableMapRow;
import com.kakao.actionbase.core.java.dataframe.row.ListRow;
import com.kakao.actionbase.core.java.dataframe.row.MapRow;
import com.kakao.actionbase.core.java.edge.EdgePayload;
import com.kakao.actionbase.core.java.edge.ImmutableEdgePayload;
import com.kakao.actionbase.core.java.payload.DataFramePayload;
import com.kakao.actionbase.core.java.types.DataType;
import com.kakao.actionbase.core.java.types.ImmutableStructType;
import com.kakao.actionbase.core.java.types.StructType;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DataFrameSerializationTest {

  private ObjectMapper objectMapper;
  private StructType testSchema;

  @BeforeEach
  public void setup() {
    objectMapper = new ObjectMapper();
    testSchema =
        ImmutableStructType.builder()
            .addField("name", DataType.STRING)
            .addField("age", DataType.LONG)
            .addField("active", DataType.BOOLEAN)
            .build();
  }

  @Nested
  class ArrayRowSerializationTest {
    @Test
    public void testPayloadSerialization() throws IOException {
      ArrayRow row =
          ImmutableArrayRow.builder().data("Alice", 31L, true).schema(testSchema).build();
      DataFramePayload payload = DataFrame.single(row).toPayload();

      // Execute
      String json = objectMapper.writeValueAsString(payload);

      // Verify
      assertEquals(
          "{\"total\":1,\"offset\":null,\"hasNext\":false,\"context\":{},\"rows\":[[\"Alice\",31,true]],\"count\":1}",
          json);
    }
  }

  @Nested
  class ListRowSerializationTest {
    @Test
    public void testPayloadSerialization() throws IOException {
      ListRow row =
          ImmutableListRow.builder()
              .addData("Alice")
              .addData(31L)
              .addData(true)
              .schema(testSchema)
              .build();
      DataFramePayload payload = DataFrame.single(row).toPayload();

      // Execute
      String json = objectMapper.writeValueAsString(payload);

      // Verify
      assertEquals(
          "{\"total\":1,\"offset\":null,\"hasNext\":false,\"context\":{},\"rows\":[[\"Alice\",31,true]],\"count\":1}",
          json);

      // Verify that schema is not serialized
      assertFalse(json.contains("\"schema\""), "Schema field should not be included");
    }
  }

  @Nested
  class MapRowSerializationTest {
    @Test
    public void testPayloadSerialization() throws IOException {
      MapRow row =
          ImmutableMapRow.builder()
              .putData("name", "Alice")
              .putData("age", 31L)
              .putData("active", true)
              .schema(testSchema)
              .build();
      DataFramePayload payload = DataFrame.single(row).toPayload();

      // Execute
      String json = objectMapper.writeValueAsString(payload);

      // Verify
      assertTrue(json.contains("\"data\":[{"), "Data should be serialized in map format");
      assertTrue(json.contains("\"name\":\"Alice\""), "name field should be included");
      assertTrue(json.contains("\"age\":31"), "age field should be included");
      assertTrue(json.contains("\"active\":true"), "active field should be included");

      // Verify that schema is not serialized
      assertFalse(json.contains("\"schema\""), "Schema field should not be included");
    }
  }

  @Nested
  class EdgeRowSerializationTest {
    @Test
    public void testPayloadSerialization() throws IOException {
      EdgePayload edge =
          ImmutableEdgePayload.builder()
              .version(1)
              .source("Alice")
              .target("Bob")
              .putProperties("followAt", 456L)
              .build();
      EdgeRow row = ImmutableEdgeRow.builder().data(edge).schema(testSchema).build();
      DataFramePayload payload = DataFrame.single(row).toPayload();

      // Execute
      String json = objectMapper.writeValueAsString(payload);

      // Verify
      assertTrue(json.contains("\"edges\":[{"), "Data should be serialized in object format");
      assertTrue(json.contains("\"source\":\"Alice\""), "source field should be included");
      assertTrue(json.contains("\"target\":\"Bob\""), "target field should be included");
      assertTrue(
          json.contains("\"properties\":{\"followAt\":456}"),
          "properties field should be included");

      // Verify that schema is not serialized
      assertFalse(json.contains("\"schema\""), "Schema field should not be included");
    }
  }
}
