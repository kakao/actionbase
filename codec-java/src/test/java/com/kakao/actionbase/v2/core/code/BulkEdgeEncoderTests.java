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
      "{\"name\":\"gift.like_product_v1\",\"desc\":\"Gift Wish\",\"type\":\"INDEXED\",\"schema\":{\"src\":{\"type\":\"LONG\"},\"tgt\":{\"type\":\"STRING\"},\"fields\":[{\"name\":\"created_at\",\"type\":\"LONG\",\"nullable\":false},{\"name\":\"permission\",\"type\":\"STRING\",\"nullable\":true},{\"name\":\"memo\",\"type\":\"STRING\",\"nullable\":true}]},\"dirType\":\"BOTH\",\"storage\":\"hbase_sandbox\",\"indices\":[{\"id\":0,\"name\":\"created_at_desc\",\"fields\":[{\"name\":\"created_at\",\"order\":\"DESC\"}]}],\"caches\":[{\"cache\":\"top_created_at\",\"fields\":[{\"field\":\"created_at\",\"order\":\"DESC\"}],\"limit\":100}],\"event\":false,\"readOnly\":false}";
  static final String edgeJsonString =
      "{\"active\":true,\"ts\":1,\"src\":123,\"tgt\":\"Coffee10\",\"props\":{\"created_at\":1, \"permission\":\"public\", \"memo\":\"for good morning\"}}";

  @Test
  void testFetchIndexedLabelAndEncodeEdges() throws JsonProcessingException {
    LabelDTO label = objectMapper.readValue(labelJsonString, LabelDTO.class);
    LabelDTO newLabel =
        label.copy("gift.like_product_v1_20240402_132500", "gift.like_product_v1_20240402_132500");

    assertEquals(1, newLabel.getCaches().size());
    assertEquals("top_created_at", newLabel.getCaches().get(0).getCache());

    BulkLoadEdge edge = objectMapper.readValue(edgeJsonString, BulkLoadEdge.class);
    Edge expectedEdge = edge.ensureType(newLabel.getSchema());

    EdgeEncoderFactory factory = new EdgeEncoderFactory(1);
    EdgeEncoder<byte[]> encoder = factory.bytesKeyValueEdgeEncoder;

    List<TypedKeyFieldValue<byte[]>> encodedEdges =
        BulkEdgeEncoder.bulkEncodeAll(encoder, edge, newLabel);

    // 1 hash edge + 2 indexed (OUT/IN) + 2 cache (OUT/IN) + 2 counter (OUT/IN)
    assertEquals(7, encodedEdges.size());

    List<byte[]> expectedCounterKeys =
        Arrays.asList(
            encoder.encodeCounterEdgeKey(expectedEdge, Direction.OUT, newLabel.getId()),
            encoder.encodeCounterEdgeKey(expectedEdge, Direction.IN, newLabel.getId()));

    int cacheRowCount = 0;

    for (TypedKeyFieldValue<byte[]> kv : encodedEdges) {
      // Cache rows are the only ones carrying a non-null qualifier in the bytes encoder;
      // round-trip via V3 decoder is covered by V2MultiEdgeBulkLoadTest.testEdgeCache*.
      if (kv.field != null) {
        cacheRowCount++;
        continue;
      }
      assertNull(kv.field);

      if (kv.key.length != 0) {
        DecodedEdge decodedEdge = DecodedEdge.from(KeyFieldValue.from(kv), Collections.emptyMap());
        assertEquals(newLabel.getId(), decodedEdge.getLabelId());
        assertEquals(expectedEdge.getTs(), decodedEdge.getTs());
        if (decodedEdge.getType() == EncodedEdgeType.HASH_EDGE_TYPE) {
          assertEquals(expectedEdge.getSrc(), decodedEdge.getSrc());
          assertEquals(expectedEdge.getTgt(), decodedEdge.getTgt());
          decodedEdge
              .getPropertyAsMap()
              .forEach((k, v) -> assertEquals(expectedEdge.getTs(), v.version));
        } else if (decodedEdge.getType() == EncodedEdgeType.INDEXED_EDGE_TYPE
            && decodedEdge.getDirection() == Direction.OUT) {
          assertEquals(expectedEdge.getSrc(), decodedEdge.getSrc());
          assertEquals(expectedEdge.getTgt(), decodedEdge.getTgt());
          decodedEdge
              .getPropertyAsMap()
              .forEach((k, v) -> assertEquals(VersionValue.NO_VERSION, v.version));
        } else if (decodedEdge.getType() == EncodedEdgeType.INDEXED_EDGE_TYPE
            && decodedEdge.getDirection() == Direction.IN) {
          assertEquals(expectedEdge.getSrc(), decodedEdge.getTgt());
          assertEquals(expectedEdge.getTgt(), decodedEdge.getSrc());
          decodedEdge
              .getPropertyAsMap()
              .forEach((k, v) -> assertEquals(VersionValue.NO_VERSION, v.version));
        } else {
          fail();
        }
      } else {
        long matchCount =
            expectedCounterKeys.stream().filter(k -> Arrays.equals(k, kv.value)).count();
        assertEquals(1, matchCount);
      }
    }

    assertEquals(2, cacheRowCount, "expected 2 cache rows (OUT/IN)");

    // No dimension configured on this cache → dimensionValue must be null on cache rows.
    encodedEdges.stream()
        .filter(kv -> kv.getEncodedEdgeType() == EncodedEdgeType.EDGE_CACHE_TYPE)
        .forEach(
            kv ->
                assertNull(
                    kv.getDimensionValue(),
                    "dimensionValue should be null when cache has no dimension config"));
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

    List<TypedKeyFieldValue<byte[]>> encodedEdges =
        BulkEdgeEncoder.bulkEncodeAll(encoder, edge, newLabel);

    // 1 hash + 1 indexed(OUT) + 1 cache(OUT) + 1 counter(OUT)
    assertEquals(4, encodedEdges.size());
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

    List<TypedKeyFieldValue<byte[]>> encodedEdges =
        BulkEdgeEncoder.bulkEncodeAll(encoder, edge, newLabel);

    // 1 hash + 1 indexed(IN) + 1 cache(IN) + 1 counter(IN)
    assertEquals(4, encodedEdges.size());
  }

  @Test
  void testFetchHashLabelAndEncodeEdges() throws JsonProcessingException {
    // HASH labels never emit cache rows even when the `caches` field is set on the label.
    String labelJsonString1 = labelJsonString.replace("\"type\":\"INDEXED\"", "\"type\":\"HASH\"");
    LabelDTO label = objectMapper.readValue(labelJsonString1, LabelDTO.class);
    LabelDTO newLabel =
        label.copy("gift.like_product_v1_20240402_132500", "gift.like_product_v1_20240402_132500");

    BulkLoadEdge edge = objectMapper.readValue(edgeJsonString, BulkLoadEdge.class);

    EdgeEncoderFactory factory = new EdgeEncoderFactory(1);
    EdgeEncoder<byte[]> encoder = factory.bytesKeyValueEdgeEncoder;

    List<TypedKeyFieldValue<byte[]>> encodedEdges =
        BulkEdgeEncoder.bulkEncodeAll(encoder, edge, newLabel);

    // 1 hash + 2 counter (no indexed, no cache)
    assertEquals(3, encodedEdges.size());
  }

  @Test
  void testInactiveEdgeOnIndexedLabel() throws JsonProcessingException {
    LabelDTO label = objectMapper.readValue(labelJsonString, LabelDTO.class);
    LabelDTO newLabel =
        label.copy("gift.like_product_v1_20240402_132500", "gift.like_product_v1_20240402_132500");

    String edgeJsonString1 = edgeJsonString.replace("\"active\":true", "\"active\":false");
    BulkLoadEdge edge = objectMapper.readValue(edgeJsonString1, BulkLoadEdge.class);

    EdgeEncoderFactory factory = new EdgeEncoderFactory(1);
    EdgeEncoder<byte[]> encoder = factory.bytesKeyValueEdgeEncoder;

    List<TypedKeyFieldValue<byte[]>> encodedEdges =
        BulkEdgeEncoder.bulkEncodeAll(encoder, edge, newLabel);

    // Inactive edge on an INDEXED label with caches: only the hash tombstone is emitted.
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

    EdgeEncoderFactory factory = new EdgeEncoderFactory(1);
    EdgeEncoder<byte[]> encoder = factory.bytesKeyValueEdgeEncoder;

    List<TypedKeyFieldValue<byte[]>> encodedEdges =
        BulkEdgeEncoder.bulkEncodeAll(encoder, edge, newLabel);

    // 1 item for the hash edge.
    assertEquals(1, encodedEdges.size());
  }

  /**
   * dimension whitelist on a cache field: edges whose value matches the whitelist still produce
   * cache rows alongside the hash/indexed/counter rows.
   */
  @Test
  void testCacheDimensionWhitelistKeepsMatchingEdge() throws JsonProcessingException {
    String labelWithDimension =
        labelJsonString.replace(
            "\"caches\":[{\"cache\":\"top_created_at\",\"fields\":[{\"field\":\"created_at\",\"order\":\"DESC\"}],\"limit\":100}]",
            "\"caches\":[{\"cache\":\"public_top_created_at\",\"fields\":[{\"field\":\"permission\",\"order\":\"ASC\",\"dimension\":[\"others\"]},{\"field\":\"created_at\",\"order\":\"DESC\"}],\"limit\":100}]");
    LabelDTO label = objectMapper.readValue(labelWithDimension, LabelDTO.class);
    LabelDTO newLabel =
        label.copy("gift.like_product_v1_20240402_132500", "gift.like_product_v1_20240402_132500");

    String othersEdgeJson =
        edgeJsonString.replace("\"permission\":\"public\"", "\"permission\":\"others\"");
    BulkLoadEdge edge = objectMapper.readValue(othersEdgeJson, BulkLoadEdge.class);

    EdgeEncoderFactory factory = new EdgeEncoderFactory(1);
    EdgeEncoder<byte[]> encoder = factory.bytesKeyValueEdgeEncoder;

    List<TypedKeyFieldValue<byte[]>> encodedEdges =
        BulkEdgeEncoder.bulkEncodeAll(encoder, edge, newLabel);

    // permission="others" matches dimension → 1 hash + 2 indexed + 2 cache + 2 counter
    assertEquals(7, encodedEdges.size());
    long cacheRows =
        encodedEdges.stream()
            .filter(kv -> kv.getEncodedEdgeType() == EncodedEdgeType.EDGE_CACHE_TYPE)
            .count();
    assertEquals(2, cacheRows);

    // Matching cache rows must carry an encoded dimensionValue (non-null, non-empty).
    encodedEdges.stream()
        .filter(kv -> kv.getEncodedEdgeType() == EncodedEdgeType.EDGE_CACHE_TYPE)
        .forEach(
            kv -> {
              assertNotNull(
                  kv.getDimensionValue(),
                  "dimensionValue should be encoded when cache field matches its dimension");
              assertTrue(kv.getDimensionValue().length > 0, "dimensionValue should be non-empty");
            });
  }

  /**
   * dimension whitelist on a cache field: edges outside the whitelist are skipped at the cache
   * layer, but the hash/indexed/counter rows are unaffected.
   */
  @Test
  void testCacheDimensionWhitelistSkipsNonMatchingEdge() throws JsonProcessingException {
    String labelWithDimension =
        labelJsonString.replace(
            "\"caches\":[{\"cache\":\"top_created_at\",\"fields\":[{\"field\":\"created_at\",\"order\":\"DESC\"}],\"limit\":100}]",
            "\"caches\":[{\"cache\":\"public_top_created_at\",\"fields\":[{\"field\":\"permission\",\"order\":\"ASC\",\"dimension\":[\"others\"]},{\"field\":\"created_at\",\"order\":\"DESC\"}],\"limit\":100}]");
    LabelDTO label = objectMapper.readValue(labelWithDimension, LabelDTO.class);
    LabelDTO newLabel =
        label.copy("gift.like_product_v1_20240402_132500", "gift.like_product_v1_20240402_132500");

    String meEdgeJson =
        edgeJsonString.replace("\"permission\":\"public\"", "\"permission\":\"me\"");
    BulkLoadEdge edge = objectMapper.readValue(meEdgeJson, BulkLoadEdge.class);

    EdgeEncoderFactory factory = new EdgeEncoderFactory(1);
    EdgeEncoder<byte[]> encoder = factory.bytesKeyValueEdgeEncoder;

    List<TypedKeyFieldValue<byte[]>> encodedEdges =
        BulkEdgeEncoder.bulkEncodeAll(encoder, edge, newLabel);

    // permission="me" outside dimension=["others"] → cache rows skipped, others kept
    // 1 hash + 2 indexed + 0 cache + 2 counter
    assertEquals(5, encodedEdges.size());
    long cacheRows =
        encodedEdges.stream()
            .filter(kv -> kv.getEncodedEdgeType() == EncodedEdgeType.EDGE_CACHE_TYPE)
            .count();
    assertEquals(0, cacheRows);
  }

  /**
   * Empty {@code dimension: []} is treated as equivalent to omitting the key — no filtering, no
   * dimensionValue encoded. Any edge value passes through and cache rows carry a null
   * dimensionValue.
   */
  @Test
  void testCacheDimensionEmptyArrayBehavesAsNoFilter() throws JsonProcessingException {
    String labelWithEmptyDimension =
        labelJsonString.replace(
            "\"caches\":[{\"cache\":\"top_created_at\",\"fields\":[{\"field\":\"created_at\",\"order\":\"DESC\"}],\"limit\":100}]",
            "\"caches\":[{\"cache\":\"public_top_created_at\",\"fields\":[{\"field\":\"permission\",\"order\":\"ASC\",\"dimension\":[]},{\"field\":\"created_at\",\"order\":\"DESC\"}],\"limit\":100}]");
    LabelDTO label = objectMapper.readValue(labelWithEmptyDimension, LabelDTO.class);
    LabelDTO newLabel =
        label.copy("gift.like_product_v1_20240402_132500", "gift.like_product_v1_20240402_132500");

    BulkLoadEdge edge = objectMapper.readValue(edgeJsonString, BulkLoadEdge.class);

    EdgeEncoderFactory factory = new EdgeEncoderFactory(1);
    EdgeEncoder<byte[]> encoder = factory.bytesKeyValueEdgeEncoder;

    List<TypedKeyFieldValue<byte[]>> encodedEdges =
        BulkEdgeEncoder.bulkEncodeAll(encoder, edge, newLabel);

    // dimension=[] acts as no filter → 1 hash + 2 indexed + 2 cache + 2 counter
    assertEquals(7, encodedEdges.size());
    encodedEdges.stream()
        .filter(kv -> kv.getEncodedEdgeType() == EncodedEdgeType.EDGE_CACHE_TYPE)
        .forEach(
            kv ->
                assertNull(
                    kv.getDimensionValue(),
                    "dimensionValue should be null when dimension is empty (no filter)"));
  }

  /**
   * Backward-compatibility: a label JSON without a `caches` entry must still deserialize, and the
   * bulk encoder must skip cache-row generation without error.
   */
  @Test
  void testIndexedLabelWithoutCachesProducesNoCacheRows() throws JsonProcessingException {
    String labelJsonWithoutCaches =
        labelJsonString.replaceAll(
            ",\"caches\":\\[\\{\"cache\":\"top_created_at\",\"fields\":\\[\\{\"field\":\"created_at\",\"order\":\"DESC\"}],\"limit\":100}]",
            "");
    assertFalse(labelJsonWithoutCaches.contains("caches"));

    LabelDTO label = objectMapper.readValue(labelJsonWithoutCaches, LabelDTO.class);
    assertNull(label.getCaches());

    BulkLoadEdge edge = objectMapper.readValue(edgeJsonString, BulkLoadEdge.class);

    EdgeEncoderFactory factory = new EdgeEncoderFactory(1);
    EdgeEncoder<byte[]> encoder = factory.bytesKeyValueEdgeEncoder;

    List<TypedKeyFieldValue<byte[]>> encodedEdges =
        BulkEdgeEncoder.bulkEncodeAll(encoder, edge, label);

    // 1 hash + 2 indexed(OUT/IN) + 2 counter(OUT/IN) — no cache rows.
    assertEquals(5, encodedEdges.size());
    encodedEdges.forEach(kv -> assertNull(kv.field, "no cache rows should be produced"));
  }
}
