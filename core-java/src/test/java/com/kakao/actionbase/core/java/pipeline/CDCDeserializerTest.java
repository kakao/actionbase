package com.kakao.actionbase.core.java.pipeline;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class CDCDeserializerTest extends AbstractDeserializerTest<CDC> {

  private static final Set<String> V2_NULLABLE_FIELDS = createV2NullableFields();
  private static final Set<String> V3_NULLABLE_FIELDS = createV3NullableFields();

  private static Set<String> createV2NullableFields() {
    return Arrays.stream(new String[] {"alias", "before", "after", "message", "audit", "requestId"})
        .collect(Collectors.toSet());
  }

  private static Set<String> createV3NullableFields() {
    return Arrays.stream(new String[] {"alias", "before", "after", "message"})
        .collect(Collectors.toSet());
  }

  private String cdcV2Json;
  private String cdcV3EdgeJson;
  private String cdcV3VertexJson;
  private ObjectNode cdcV2;
  private ObjectNode cdcV3Edge;
  private ObjectNode cdcV3Vertex;

  @Override
  protected void initializeTestData() throws JsonProcessingException {
    cdcV2Json = createCdcV2JsonString();
    cdcV3EdgeJson = createCdcV3EdgeJsonString();
    cdcV3VertexJson = createCdcV3VertexJsonString();

    cdcV2 = (ObjectNode) objectMapper.readTree(cdcV2Json);
    cdcV3Edge = (ObjectNode) objectMapper.readTree(cdcV3EdgeJson);
    cdcV3Vertex = (ObjectNode) objectMapper.readTree(cdcV3VertexJson);
  }

  @Override
  protected Class<CDC> getMessageClass() {
    return CDC.class;
  }

  @Nested
  class CDCV2Tests {
    @Test
    public void testSelfCompareDeserializeAndSerialize() throws Exception {
      testDeserializeAndSerialize(cdcV2Json, CDCV2.class);
    }

    @Test
    public void testDeserializeV2() throws IOException {
      testDeserializeWithValidJson(cdcV2, CDCV2.class, "Deserialized object is not CDCV2");
    }

    @Test
    public void testDeserializeV2WithMissingFields() throws IOException {
      testMissingFields(cdcV2, V2_NULLABLE_FIELDS, CDCV2.class);
    }

    @Test
    public void testDeserializeV2WithAllNullableFieldsRemoved() throws IOException {
      testWithAllNullableFieldsRemoved(
          cdcV2,
          V2_NULLABLE_FIELDS,
          CDCV2.class,
          "Deserialization should succeed even if all nullable fields are missing");
    }
  }

  @Nested
  class CDCV3EdgeTests {
    @Test
    public void testSelfCompareDeserializeAndSerialize() throws Exception {
      testDeserializeAndSerialize(cdcV3EdgeJson, CDCV3Edge.class);
    }

    @Test
    public void testDeserializeV3() throws IOException {
      testDeserializeWithValidJson(
          cdcV3Edge, CDCV3Edge.class, "Deserialized object is not CDC Edge V3");
    }

    @Test
    public void testDeserializeV3WithMissingFields() throws IOException {
      testMissingFields(cdcV3Edge, V3_NULLABLE_FIELDS, CDCV3Edge.class);
    }

    @Test
    public void testDeserializeV3WithAllNullableFieldsRemoved() throws IOException {
      testWithAllNullableFieldsRemoved(
          cdcV3Edge,
          V3_NULLABLE_FIELDS,
          CDCV3Edge.class,
          "Deserialization should succeed even if all nullable fields are missing");
    }
  }

  @Nested
  class CDCV3VertexTests {
    @Test
    public void testSelfCompareDeserializeAndSerialize() throws Exception {
      testDeserializeAndSerialize(cdcV3VertexJson, CDCV3Vertex.class);
    }

    @Test
    public void testDeserializeV3() throws IOException {
      testDeserializeWithValidJson(
          cdcV3Vertex, CDCV3Vertex.class, "Deserialized object is not CDC Vertex V3");
    }

    @Test
    public void testDeserializeV3WithMissingFields() throws IOException {
      testMissingFields(cdcV3Vertex, V3_NULLABLE_FIELDS, CDCV3Vertex.class);
    }

    @Test
    public void testDeserializeV3WithAllNullableFieldsRemoved() throws IOException {
      testWithAllNullableFieldsRemoved(
          cdcV3Vertex,
          V3_NULLABLE_FIELDS,
          CDCV3Vertex.class,
          "Deserialization should succeed even if all nullable fields are missing");
    }
  }

  private String createCdcV2JsonString() {
    return "{"
        + "  \"version\":\"2\","
        + "  \"alias\":\"test_service.gift_received_v2\","
        + "  \"label\":\"test_service.test_label_20250415_000000\","
        + "  \"ts\":1744668000110,"
        + "  \"edge\":{"
        + "    \"ts\":1744667998065,"
        + "    \"src\":100,"
        + "    \"tgt\":200,"
        + "    \"props\":{\"property1\":\"1\"},"
        + "    \"traceId\":\"01JRV51ZV7MX92D9M3Y47CE7A7\""
        + "  },"
        + "  \"op\":\"UPDATE\","
        + "  \"status\":\"IDLE\","
        + "  \"before\":{"
        + "    \"active\":false,"
        + "    \"ts\":1744667983563,"
        + "    \"src\":100,"
        + "    \"tgt\":200,"
        + "    \"props\":{\"property1\":\"1\"}"
        + "  },"
        + "  \"after\":{"
        + "    \"active\":false,"
        + "    \"ts\":1744667998065,"
        + "    \"src\":100,"
        + "    \"tgt\":200,"
        + "    \"props\":{\"property1\":\"1\"}"
        + "  },"
        + "  \"acc\":0,"
        + "  \"message\":null,"
        + "  \"audit\":{\"actor\":\"AsyncRequestProcessor\"},"
        + "  \"requestId\":\"41c4adba5cf86e0c555e10719729f8ab\","
        + "  \"tenant\":\"kc\","
        + "  \"phase\":\"kc\""
        + "}";
  }

  private String createCdcV3EdgeJsonString() {
    return "{"
        + "  \"version\":\"3\","
        + "  \"type\":\"e\","
        + "  \"processedAt\":1744668000110,"
        + "  \"status\":\"IDLE\","
        + "  \"accumulator\":0,"
        + "  \"message\":null,"
        + "  \"context\":{"
        + "    \"tenant\":\"kc\","
        + "    \"database\":\"test\","
        + "    \"alias\":\"some_alias\","
        + "    \"table\":\"some_table\","
        + "    \"request\":{\"actor\":\"default\",\"requestId\":\"41c4adba5cf86e0c555e10719729f8ab\"}"
        + "  },"
        + "  \"events\":["
        + "    {"
        + "      \"type\":\"UPDATE\","
        + "      \"version\":1744667998065,"
        + "      \"source\":100,"
        + "      \"target\":200,"
        + "      \"properties\":{\"property1\":\"1\"},"
        + "      \"id\":\"01JRV51ZV7MX92D9M3Y47CE7A7\","
        + "      \"requestId\":\"41c4adba5cf86e0c555e10719729f8ab\""
        + "    }"
        + "  ],"
        + "  \"before\":{"
        + "    \"active\":false,"
        + "    \"version\":1744667983563,"
        + "    \"source\":100,"
        + "    \"target\":200,"
        + "    \"properties\":{\"property1\":{\"value\":\"1\",\"version\":1744667983563}},"
        + "    \"createdAt\":1744667983563,"
        + "    \"deletedAt\":1744667983563"
        + "  },"
        + "  \"after\":{"
        + "    \"active\":false,"
        + "    \"version\":1744667998065,"
        + "    \"source\":100,"
        + "    \"target\":200,"
        + "    \"properties\":{\"property1\":{\"value\":\"1\",\"version\":1744667983563}},"
        + "    \"createdAt\":1744667983563,"
        + "    \"deletedAt\":1744667983563"
        + "  },"
        + "  \"lockAt\": 1744668000110"
        + "}";
  }

  private String createCdcV3VertexJsonString() {
    return "{"
        + "  \"version\":\"3\","
        + "  \"type\":\"v\","
        + "  \"processedAt\":1744668000110,"
        + "  \"accumulator\":0,"
        + "  \"message\":null,"
        + "  \"status\":\"IDLE\","
        + "  \"context\":{"
        + "    \"tenant\":\"kc\","
        + "    \"database\":\"test\","
        + "    \"alias\":\"some_alias\","
        + "    \"table\":\"some_table\","
        + "    \"request\":{\"actor\":\"default\",\"requestId\":\"41c4adba5cf86e0c555e10719729f8ab\"}"
        + "  },"
        + "  \"events\":["
        + "    {"
        + "      \"type\":\"UPDATE\","
        + "      \"version\":1744667998065,"
        + "      \"key\":100,"
        + "      \"properties\":{\"property1\":\"1\"},"
        + "      \"id\":\"01JRV51ZV7MX92D9M3Y47CE7A7\","
        + "      \"requestId\":\"41c4adba5cf86e0c555e10719729f8ab\""
        + "    }"
        + "  ],"
        + "  \"before\":{"
        + "    \"active\":false,"
        + "    \"version\":1744667983563,"
        + "    \"key\":100,"
        + "    \"properties\":{\"property1\":{\"value\":\"1\",\"version\":1744667983563}},"
        + "    \"createdAt\":1744667983563,"
        + "    \"deletedAt\":1744667983563"
        + "  },"
        + "  \"after\":{"
        + "    \"active\":false,"
        + "    \"version\":1744667998065,"
        + "    \"key\":100,"
        + "    \"properties\":{\"property1\":{\"value\":\"1\",\"version\":1744667983563}},"
        + "    \"createdAt\":1744667983563,"
        + "    \"deletedAt\":1744667983563"
        + "  },"
        + "  \"lockAt\": 1744668000110"
        + "}";
  }
}
