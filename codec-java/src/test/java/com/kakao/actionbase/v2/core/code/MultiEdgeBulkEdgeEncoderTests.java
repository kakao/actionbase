package com.kakao.actionbase.v2.core.code;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kakao.actionbase.v2.core.edge.BulkLoadEdge;
import com.kakao.actionbase.v2.core.metadata.LabelDTO;

import java.util.Base64;
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

    EdgeEncoderFactory factory = new EdgeEncoderFactory(1);
    EdgeEncoder<byte[]> encoder = factory.bytesKeyValueEdgeEncoder;

    List<KeyFieldValue<byte[]>> encodedEdges = BulkEdgeEncoder.bulkEncodeAll(encoder, edge, label);

    // 1 EdgeState
    // 2 EdgeIndex (OUT, IN)
    // 2 EdgeCount (OUT, IN)
    assertEquals(5, encodedEdges.size());

    encodedEdges.forEach(
        kv -> {
          String key = Base64.getEncoder().encodeToString(kv.getKey());
          String value = Base64.getEncoder().encodeToString(kv.getValue());
          System.out.println(key + ", " + value);
        });

    // see [com.kakao.actionbase.core.bulkload.V2MultiEdgeBulkLoadTest]
    // jb3NsSyAAAAAAAAAASuiY3G2KX0sgAAAAAAAAAE=,
    // KYEsgAAAAAAAAAEr1Wc4JSyAAAAAAAAAASyAAAAAAAAAASsLXozXNGZvciBnb29kIG1vcm5pbmcALIAAAAAAAAABKyqUN440cHVibGljACyAAAAAAAAAASvEPlKJLIAAAAAAAAB7LIAAAAAAAAABK0noVpM0Q29mZmVlMTAALIAAAAAAAAABK5JB3jEsgAAAAAAAAAEsgAAAAAAAAAE=
    // XskydSyAAAAAAAAAeyuiY3G2KXwpgitptzPH03/////////+LIAAAAAAAAAB,
    // LIAAAAAAAAABK9VnOCUsgAAAAAAAAAErC16M1zRmb3IgZ29vZCBtb3JuaW5nACsqlDeONHB1YmxpYwArxD5SiSyAAAAAAAAAeytJ6FaTNENvZmZlZTEwAA==
    // 4IU4UDRDb2ZmZWUxMAAromNxtil8KYMrabczx9N//////////iyAAAAAAAAAAQ==,
    // LIAAAAAAAAABK9VnOCUsgAAAAAAAAAErC16M1zRmb3IgZ29vZCBtb3JuaW5nACsqlDeONHB1YmxpYwArxD5SiSyAAAAAAAAAeytJ6FaTNENvZmZlZTEwAA==
    // , dc/qIyyAAAAAAAAAeyuiY3G2KX4pgg==
    // , 7s9/xDRDb2ZmZWUxMAAromNxtil+KYM=
  }
}
