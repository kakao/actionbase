package com.kakao.actionbase.core.java.pipeline;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class WALDeserializerTest extends AbstractDeserializerTest<WAL> {

  private static final Set<String> V2_NULLABLE_FIELDS = createV2NullableFields();
  private static final Set<String> V3_NULLABLE_FIELDS = createV3NullableFields();

  private static Set<String> createV2NullableFields() {
    return Arrays.stream(new String[] {"alias", "mode", "audit", "requestId"})
        .collect(Collectors.toSet());
  }

  private static Set<String> createV3NullableFields() {
    return Arrays.stream(new String[] {"alias"}).collect(Collectors.toSet());
  }

  private String walV2Json;
  private String walV3EdgeJson;
  private String walV3VertexJson;
  private ObjectNode walV2;
  private ObjectNode walV3Edge;
  private ObjectNode walV3Vertex;

  @Override
  protected void initializeTestData() throws JsonProcessingException {
    walV2Json = createWalV2JsonString();
    walV3EdgeJson = createWalV3EdgeJsonString();
    walV3VertexJson = createWalV3VertexJsonString();

    walV2 = (ObjectNode) objectMapper.readTree(walV2Json);
    walV3Edge = (ObjectNode) objectMapper.readTree(walV3EdgeJson);
    walV3Vertex = (ObjectNode) objectMapper.readTree(walV3VertexJson);
  }

  @Override
  protected Class<WAL> getMessageClass() {
    return WAL.class;
  }

  @Nested
  class WALV2Tests {
    @Test
    public void testSelfCompareDeserializeAndSerialize() throws Exception {
      testDeserializeAndSerialize(walV2Json, WALV2.class);
    }

    @Test
    public void testDeserializeV2() throws IOException {
      testDeserializeWithValidJson(walV2, WALV2.class, "Deserialized object is not WALV2");
    }

    @Test
    public void testDeserializeV2WithMissingFields() throws IOException {
      testMissingFields(walV2, V2_NULLABLE_FIELDS, WALV2.class);
    }

    @Test
    public void testDeserializeV2WithAllNullableFieldsRemoved() throws IOException {
      testWithAllNullableFieldsRemoved(
          walV2,
          V2_NULLABLE_FIELDS,
          WALV2.class,
          "Deserialization should succeed even if all nullable fields are missing");
    }
  }

  @Nested
  class WALV3EdgeTests {
    @Test
    public void testSelfCompareDeserializeAndSerialize() throws Exception {
      testDeserializeAndSerialize(walV3EdgeJson, WALV3Edge.class);
    }

    @Test
    public void testDeserializeV3() throws IOException {
      testDeserializeWithValidJson(
          walV3Edge, WALV3Edge.class, "Deserialized object is not WAL Edge V3");
    }

    @Test
    public void testDeserializeV3WithMissingFields() throws IOException {
      testMissingFields(walV3Edge, V3_NULLABLE_FIELDS, WALV3Edge.class);
    }

    @Test
    public void testDeserializeV3WithAllNullableFieldsRemoved() throws IOException {
      testWithAllNullableFieldsRemoved(
          walV3Edge,
          V3_NULLABLE_FIELDS,
          WALV3Edge.class,
          "Deserialization should succeed even if all nullable fields are missing");
    }
  }

  @Nested
  class WALV3VertexTests {
    @Test
    public void testSelfCompareDeserializeAndSerialize() throws Exception {
      testDeserializeAndSerialize(walV3VertexJson, WALV3Vertex.class);
    }

    @Test
    public void testDeserializeV3() throws IOException {
      testDeserializeWithValidJson(
          walV3Vertex, WALV3Vertex.class, "Deserialized object is not WAL Vertex V3");
    }

    @Test
    public void testDeserializeV3WithMissingFields() throws IOException {
      testMissingFields(walV3Vertex, V3_NULLABLE_FIELDS, WALV3Vertex.class);
    }

    @Test
    public void testDeserializeV3WithAllNullableFieldsRemoved() throws IOException {
      testWithAllNullableFieldsRemoved(
          walV3Vertex,
          V3_NULLABLE_FIELDS,
          WALV3Vertex.class,
          "Deserialization should succeed even if all nullable fields are missing");
    }
  }

  // Common test methods are inherited from parent class

  private String createWalV2JsonString() {
    return "{"
        + "  \"version\":\"2\","
        + "  \"alias\":\"test.some_alias\","
        + "  \"label\":\"test.some_label\","
        + "  \"edge\":{"
        + "    \"ts\":1,"
        + "    \"src\":\"src\","
        + "    \"tgt\":\"tgt\","
        + "    \"traceId\":\"01JWYM8FJHEKXSVD3WTQ5DB9P7\""
        + "  },"
        + "  \"op\":\"INSERT\","
        + "  \"mode\":{"
        + "    \"l\":\"SYNC\","
        + "    \"r\":null,"
        + "    \"queue\":false"
        + "  },"
        + "  \"audit\":{\"actor\":\"default\"},"
        + "  \"requestId\":\"41c4adba5cf86e0c555e10719729f8ab\","
        + "  \"ts\":1749079572049,"
        + "  \"phase\":\"dev\","
        + "  \"tenant\":\"test-tenant\""
        + "}";
  }

  private String createWalV3EdgeJsonString() {
    return "{"
        + "  \"version\":\"3\","
        + "  \"type\":\"e\","
        + "  \"processedAt\":1749079572049,"
        + "  \"context\":{"
        + "    \"tenant\":\"kc\","
        + "    \"database\":\"test\","
        + "    \"alias\":\"some_alias\","
        + "    \"table\":\"some_table\","
        + "    \"request\":{\"actor\":\"default\",\"requestId\":\"41c4adba5cf86e0c555e10719729f8ab\"}"
        + "  },"
        + "  \"event\":{"
        + "    \"id\":\"01JWYM8FJHEKXSVD3WTQ5DB9P7\","
        + "    \"requestId\":\"41c4adba5cf86e0c555e10719729f8ab\","
        + "    \"type\":\"INSERT\","
        + "    \"version\":1,"
        + "    \"source\":\"src\","
        + "    \"target\":\"tgt\","
        + "    \"properties\":{}"
        + "  },"
        + "  \"mode\":{"
        + "    \"table\":\"SYNC\","
        + "    \"request\":null,"
        + "    \"queue\":false"
        + "  }"
        + "}";
  }

  private String createWalV3VertexJsonString() {
    return "{"
        + "  \"version\":\"3\","
        + "  \"type\":\"v\","
        + "  \"processedAt\":1749079572049,"
        + "  \"context\":{"
        + "    \"tenant\":\"kc\","
        + "    \"database\":\"test\","
        + "    \"alias\":\"some_alias\","
        + "    \"table\":\"some_table\","
        + "    \"request\":{\"actor\":\"default\",\"requestId\":\"41c4adba5cf86e0c555e10719729f8ab\"}"
        + "  },"
        + "  \"event\":{"
        + "    \"id\":\"01JWYM8FJHEKXSVD3WTQ5DB9P7\","
        + "    \"requestId\":\"41c4adba5cf86e0c555e10719729f8ab\","
        + "    \"type\":\"INSERT\","
        + "    \"version\":1,"
        + "    \"key\":\"key\","
        + "    \"properties\":{}"
        + "  },"
        + "  \"mode\":{"
        + "    \"table\":\"SYNC\","
        + "    \"request\":null,"
        + "    \"queue\":false"
        + "  }"
        + "}";
  }
}
