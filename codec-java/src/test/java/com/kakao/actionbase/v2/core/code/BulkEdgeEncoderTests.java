package com.kakao.actionbase.v2.core.code;

import static org.junit.jupiter.api.Assertions.*;

import com.kakao.actionbase.v2.core.edge.BulkLoadEdge;
import com.kakao.actionbase.v2.core.edge.Edge;
import com.kakao.actionbase.v2.core.metadata.Direction;
import com.kakao.actionbase.v2.core.metadata.EncodedEdgeType;
import com.kakao.actionbase.v2.core.metadata.LabelDTO;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class BulkEdgeEncoderTests {

  ObjectMapper objectMapper = new ObjectMapper();

  static final String labelJsonString =
      "{\"name\":\"gift.like_product_v1\",\"desc\":\"Gift Wish\",\"type\":\"INDEXED\",\"schema\":{\"src\":{\"type\":\"LONG\"},\"tgt\":{\"type\":\"STRING\"},\"fields\":[{\"name\":\"created_at\",\"type\":\"LONG\",\"nullable\":false},{\"name\":\"permission\",\"type\":\"STRING\",\"nullable\":true},{\"name\":\"memo\",\"type\":\"STRING\",\"nullable\":true}]},\"dirType\":\"BOTH\",\"storage\":\"hbase_sandbox\",\"indices\":[{\"id\":0,\"name\":\"created_at_desc\",\"fields\":[{\"name\":\"created_at\",\"order\":\"DESC\"}]}],\"event\":false,\"readOnly\":false}";
  static final String edgeJsonString =
      "{\"active\":true,\"ts\":1,\"src\":123,\"tgt\":\"Coffee10\",\"props\":{\"created_at\":1, \"permission\":\"public\", \"memo\":\"for good morning\"}}";

  @Test
  void testFetchIndexedLabelAndEncodeEdges() throws JsonProcessingException {
    LabelDTO label = objectMapper.readValue(labelJsonString, LabelDTO.class);
    LabelDTO newLabel =
        label.copy("gift.like_product_v1_20240402_132500", "gift.like_product_v1_20240402_132500");

    BulkLoadEdge edge = objectMapper.readValue(edgeJsonString, BulkLoadEdge.class);
    Edge expectedEdge = edge.ensureType(newLabel.getSchema());

    EdgeEncoderFactory factory = new EdgeEncoderFactory(1);
    EdgeEncoder<byte[]> encoder = factory.bytesKeyValueEdgeEncoder;

    List<KeyFieldValue<byte[]>> encodedEdges =
        BulkEdgeEncoder.bulkEncodeAll(encoder, edge, newLabel);

    // 1 item for the hash edge.
    // 2 items for indexed edges within a single index, covering both OUTBOUND and INBOUND
    // directions.
    // 2 items for counter edges, one each for OUTBOUND and INBOUND directions.
    assertEquals(5, encodedEdges.size());

    // bytes encoded edges should not have field.
    encodedEdges.forEach(kv -> assertNull(kv.field));

    List<byte[]> expectedCounterKeys =
        Arrays.asList(
            encoder.encodeCounterEdgeKey(expectedEdge, Direction.OUT, newLabel.getId()),
            encoder.encodeCounterEdgeKey(expectedEdge, Direction.IN, newLabel.getId()));

    encodedEdges.forEach(
        kv -> {
          if (kv.key.length != 0) {
            DecodedEdge decodedEdge = DecodedEdge.from(kv, Collections.emptyMap());
            assertEquals(newLabel.getId(), decodedEdge.getLabelId());
            assertEquals(expectedEdge.getTs(), decodedEdge.getTs());
            if (decodedEdge.getType() == EncodedEdgeType.HASH_EDGE_TYPE) {
              // hash edge
              assertEquals(expectedEdge.getSrc(), decodedEdge.getSrc());
              assertEquals(expectedEdge.getTgt(), decodedEdge.getTgt());
              decodedEdge
                  .getPropertyAsMap()
                  .forEach((k, v) -> assertEquals(expectedEdge.getTs(), v.version));
            } else if (decodedEdge.getType() == EncodedEdgeType.INDEXED_EDGE_TYPE
                && decodedEdge.getDirection() == Direction.OUT) {
              // OUTBOUND of indexed edge
              assertEquals(expectedEdge.getSrc(), decodedEdge.getSrc());
              assertEquals(expectedEdge.getTgt(), decodedEdge.getTgt());
              decodedEdge
                  .getPropertyAsMap()
                  .forEach((k, v) -> assertEquals(VersionValue.NO_VERSION, v.version));
            } else if (decodedEdge.getType() == EncodedEdgeType.INDEXED_EDGE_TYPE
                && decodedEdge.getDirection() == Direction.IN) {
              // INBOUND of indexed edge
              assertEquals(expectedEdge.getSrc(), decodedEdge.getTgt());
              assertEquals(expectedEdge.getTgt(), decodedEdge.getSrc());
              decodedEdge
                  .getPropertyAsMap()
                  .forEach((k, v) -> assertEquals(VersionValue.NO_VERSION, v.version));
            } else {
              fail();
            }
            System.out.println(decodedEdge);
          } else {
            long matchCount =
                expectedCounterKeys.stream().filter(k -> Arrays.equals(k, kv.value)).count();
            assertEquals(1, matchCount);
          }
        });
  }

  @Test
  void testFetchIndexedLabelAndEncodeEdgesOutboundOnly() throws JsonProcessingException {
    String labelJsonString1 =
        labelJsonString.replace("\"dirType\":\"BOTH\"", "\"dirType\":\"OUT\"");
    LabelDTO label = objectMapper.readValue(labelJsonString1, LabelDTO.class);
    LabelDTO newLabel =
        label.copy("gift.like_product_v1_20240402_132500", "gift.like_product_v1_20240402_132500");

    BulkLoadEdge edge = objectMapper.readValue(edgeJsonString, BulkLoadEdge.class);

    EdgeEncoderFactory factory = new EdgeEncoderFactory(1);
    EdgeEncoder<byte[]> encoder = factory.bytesKeyValueEdgeEncoder;

    List<KeyFieldValue<byte[]>> encodedEdges =
        BulkEdgeEncoder.bulkEncodeAll(encoder, edge, newLabel);

    // 1 item for the hash edge.
    // 1 item for indexed edges within a single index, covering only OUTBOUND direction.
    // 1 item for counter edges, only for OUTBOUND direction.
    assertEquals(3, encodedEdges.size());
  }

  @Test
  void testFetchIndexedLabelAndEncodeEdgesInboundOnly() throws JsonProcessingException {
    String labelJsonString1 = labelJsonString.replace("\"dirType\":\"BOTH\"", "\"dirType\":\"IN\"");
    LabelDTO label = objectMapper.readValue(labelJsonString1, LabelDTO.class);
    LabelDTO newLabel =
        label.copy("gift.like_product_v1_20240402_132500", "gift.like_product_v1_20240402_132500");

    BulkLoadEdge edge = objectMapper.readValue(edgeJsonString, BulkLoadEdge.class);

    EdgeEncoderFactory factory = new EdgeEncoderFactory(1);
    EdgeEncoder<byte[]> encoder = factory.bytesKeyValueEdgeEncoder;

    List<KeyFieldValue<byte[]>> encodedEdges =
        BulkEdgeEncoder.bulkEncodeAll(encoder, edge, newLabel);

    // 1 item for the hash edge.
    // 1 item for indexed edges within a single index, covering only INBOUND direction.
    // 1 item for counter edges, only for INBOUND direction.
    assertEquals(3, encodedEdges.size());
  }

  @Test
  void testFetchHashLabelAndEncodeEdges() throws JsonProcessingException {
    String labelJsonString1 = labelJsonString.replace("\"type\":\"INDEXED\"", "\"type\":\"HASH\"");
    LabelDTO label = objectMapper.readValue(labelJsonString1, LabelDTO.class);
    LabelDTO newLabel =
        label.copy("gift.like_product_v1_20240402_132500", "gift.like_product_v1_20240402_132500");

    BulkLoadEdge edge = objectMapper.readValue(edgeJsonString, BulkLoadEdge.class);

    EdgeEncoderFactory factory = new EdgeEncoderFactory(1);
    EdgeEncoder<byte[]> encoder = factory.bytesKeyValueEdgeEncoder;

    List<KeyFieldValue<byte[]>> encodedEdges =
        BulkEdgeEncoder.bulkEncodeAll(encoder, edge, newLabel);

    // 1 item for the hash edge.
    // 2 items for counter edges, one each for OUTBOUND and INBOUND directions.
    assertEquals(3, encodedEdges.size());
  }

  @Test
  void testInactiveEdgeOnIndexedLabel() throws JsonProcessingException {
    LabelDTO label = objectMapper.readValue(labelJsonString, LabelDTO.class);
    LabelDTO newLabel =
        label.copy("gift.like_product_v1_20240402_132500", "gift.like_product_v1_20240402_132500");

    String edgeJsonString1 = edgeJsonString.replace("\"active\":true", "\"active\":false");
    BulkLoadEdge edge = objectMapper.readValue(edgeJsonString1, BulkLoadEdge.class);

    System.out.println(edge);

    EdgeEncoderFactory factory = new EdgeEncoderFactory(1);
    EdgeEncoder<byte[]> encoder = factory.bytesKeyValueEdgeEncoder;

    List<KeyFieldValue<byte[]>> encodedEdges =
        BulkEdgeEncoder.bulkEncodeAll(encoder, edge, newLabel);

    // 1 item for the hash edge.
    assertEquals(1, encodedEdges.size());
  }

  @Test
  void testInactiveEdgeOnHashLabel() throws JsonProcessingException {
    String labelJsonString1 = labelJsonString.replace("\"type\":\"INDEXED\"", "\"type\":\"HASH\"");
    LabelDTO label = objectMapper.readValue(labelJsonString1, LabelDTO.class);
    LabelDTO newLabel =
        label.copy("gift.like_product_v1_20240402_132500", "gift.like_product_v1_20240402_132500");

    String edgeJsonString1 = edgeJsonString.replace("\"active\":true", "\"active\":false");
    BulkLoadEdge edge = objectMapper.readValue(edgeJsonString1, BulkLoadEdge.class);

    System.out.println(edge);

    EdgeEncoderFactory factory = new EdgeEncoderFactory(1);
    EdgeEncoder<byte[]> encoder = factory.bytesKeyValueEdgeEncoder;

    List<KeyFieldValue<byte[]>> encodedEdges =
        BulkEdgeEncoder.bulkEncodeAll(encoder, edge, newLabel);

    // 1 item for the hash edge.
    assertEquals(1, encodedEdges.size());
  }
}
