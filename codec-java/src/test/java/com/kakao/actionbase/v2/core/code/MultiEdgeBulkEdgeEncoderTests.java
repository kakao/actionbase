package com.kakao.actionbase.v2.core.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kakao.actionbase.v2.core.edge.BulkLoadEdge;
import com.kakao.actionbase.v2.core.metadata.LabelDTO;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MultiEdgeBulkEdgeEncoderTests {

  ObjectMapper objectMapper = new ObjectMapper();

  static final String labelJsonString =
      "{\n"
          + "  \"name\": \"gift.like_product_v1_20240402_132500\",\n"
          + "  \"desc\": \"Gift Wish\",\n"
          + "  \"type\": \"MULTI_EDGE\",\n"
          + "  \"schema\": {\n"
          + "    \"src\": {\n"
          + "      \"type\": \"LONG\"\n"
          + "    },\n"
          + "    \"tgt\": {\n"
          + "      \"type\": \"STRING\"\n"
          + "    },\n"
          + "    \"fields\": [\n"
          + "      {\n"
          + "        \"name\": \"_id\",\n"
          + "        \"type\": \"LONG\",\n"
          + "        \"nullable\": false\n"
          + "      },\n"
          + "      {\n"
          + "        \"name\": \"created_at\",\n"
          + "        \"type\": \"LONG\",\n"
          + "        \"nullable\": false\n"
          + "      },\n"
          + "      {\n"
          + "        \"name\": \"permission\",\n"
          + "        \"type\": \"STRING\",\n"
          + "        \"nullable\": true\n"
          + "      },\n"
          + "      {\n"
          + "        \"name\": \"memo\",\n"
          + "        \"type\": \"STRING\",\n"
          + "        \"nullable\": true\n"
          + "      }\n"
          + "    ]\n"
          + "  },\n"
          + "  \"dirType\": \"BOTH\",\n"
          + "  \"storage\": \"gift.like_product_v1_20240402_132500\",\n"
          + "  \"indices\": [\n"
          + "    {\n"
          + "      \"id\": 0,\n"
          + "      \"name\": \"created_at_desc\",\n"
          + "      \"fields\": [\n"
          + "        {\n"
          + "          \"name\": \"created_at\",\n"
          + "          \"order\": \"DESC\"\n"
          + "        }\n"
          + "      ]\n"
          + "    }\n"
          + "  ],\n"
          + "  \"caches\": [\n"
          + "    {\n"
          + "      \"cache\": \"top_created_at\",\n"
          + "      \"fields\": [{\"name\": \"created_at\", \"order\": \"DESC\"}],\n"
          + "      \"limit\": 100\n"
          + "    }\n"
          + "  ],\n"
          + "  \"event\": false,\n"
          + "  \"readOnly\": false\n"
          + "}";

  static final String edgeJsonString =
      "{\n"
          + "  \"active\": true,\n"
          + "  \"ts\": 1,\n"
          + "  \"src\": 123,\n"
          + "  \"tgt\": \"Coffee10\",\n"
          + "  \"props\": {\n"
          + "    \"_id\": 1,\n"
          + "    \"created_at\": 1,\n"
          + "    \"permission\": \"public\",\n"
          + "    \"memo\": \"for good morning\"\n"
          + "  }\n"
          + "}";

  @Test
  void testMultiEdge() throws JsonProcessingException {
    LabelDTO label = objectMapper.readValue(labelJsonString, LabelDTO.class);
    BulkLoadEdge edge = objectMapper.readValue(edgeJsonString, BulkLoadEdge.class);

    assertEquals(1, label.getCaches().size());

    EdgeEncoderFactory factory = new EdgeEncoderFactory(1);
    EdgeEncoder<byte[]> encoder = factory.bytesKeyValueEdgeEncoder;

    List<KeyFieldValue<byte[]>> encodedEdges = BulkEdgeEncoder.bulkEncodeAll(encoder, edge, label);

    // 1 EdgeState + 2 EdgeIndex (OUT/IN) + 2 EdgeCache (OUT/IN) + 2 EdgeCount (OUT/IN)
    assertEquals(7, encodedEdges.size());

    long cacheRowCount = encodedEdges.stream().filter(kv -> kv.field != null).count();
    assertEquals(2, cacheRowCount, "expected 2 cache rows (OUT/IN)");
  }

  /**
   * Backward-compatibility: a MULTI_EDGE label JSON without a `caches` entry must still deserialize
   * and the bulk encoder must skip cache-row generation.
   */
  @Test
  void testMultiEdgeWithoutCaches() throws JsonProcessingException {
    String labelJsonString1 =
        labelJsonString.replaceAll("(?s),\\s*\"caches\":\\s*\\[[^\\]]+\\]", "");
    assertTrue(!labelJsonString1.contains("\"caches\""));

    LabelDTO label = objectMapper.readValue(labelJsonString1, LabelDTO.class);
    assertNull(label.getCaches());

    BulkLoadEdge edge = objectMapper.readValue(edgeJsonString, BulkLoadEdge.class);

    EdgeEncoderFactory factory = new EdgeEncoderFactory(1);
    EdgeEncoder<byte[]> encoder = factory.bytesKeyValueEdgeEncoder;

    List<KeyFieldValue<byte[]>> encodedEdges = BulkEdgeEncoder.bulkEncodeAll(encoder, edge, label);

    // 1 EdgeState + 2 EdgeIndex (OUT/IN) + 2 EdgeCount (OUT/IN) — no cache rows.
    assertEquals(5, encodedEdges.size());
    assertEquals(0, encodedEdges.stream().filter(kv -> kv.field != null).count());
  }
}
