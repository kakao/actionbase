package com.kakao.actionbase.core.java.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kakao.actionbase.core.java.edge.EdgeEvent;
import com.kakao.actionbase.core.java.edge.EdgeEventPayload;
import com.kakao.actionbase.core.java.edge.EdgePayload;
import com.kakao.actionbase.core.java.edge.EdgeState;
import com.kakao.actionbase.core.java.edge.Edges;
import com.kakao.actionbase.core.java.edge.index.StoredEdgeIndex;
import com.kakao.actionbase.core.java.metadata.ImmutableTenantId;
import com.kakao.actionbase.core.java.metadata.v2.ImmutableLabelDescriptor;
import com.kakao.actionbase.core.java.metadata.v2.LabelDescriptor;
import com.kakao.actionbase.core.java.metadata.v3.EdgeTableDescriptor;
import com.kakao.actionbase.core.java.metadata.v3.common.Direction;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class V2CdcBulkEncoderTest {

  private ObjectMapper objectMapper;
  private StateCodec codec;

  private static final String TENANT_ID = "test";
  private static final String RENAMED_LABEL = "test_service.test_table_20250415_000000";

  private static final String V2_BASE_LABEL_JSON_STRING =
      "{\"active\":true,\"name\":\"test_service.test_table\",\"desc\":\"this is a test table\",\"type\":\"INDEXED\",\"schema\":{\"src\":{\"type\":\"LONG\"},\"tgt\":{\"type\":\"STRING\"},\"fields\":[{\"name\":\"created_at\",\"type\":\"LONG\",\"nullable\":false},{\"name\":\"permission\",\"type\":\"STRING\",\"nullable\":true},{\"name\":\"memo\",\"type\":\"STRING\",\"nullable\":true}]},\"dirType\":\"BOTH\",\"storage\":\"hbase_sandbox\",\"indices\":[{\"name\":\"created_at_desc\",\"fields\":[{\"name\":\"created_at\",\"order\":\"DESC\"}]}],\"event\":false,\"readOnly\":false,\"mode\":\"ASYNC\"}";
  private static final String V3_EVENT_INSERT_JSON_STRING =
      "{\"type\":\"INSERT\",\"version\":1,\"source\":123,\"target\":\"Coffee10\",\"properties\":{\"created_at\":1, \"permission\":\"public\", \"memo\":\"Latte\"}}";
  private static final String V3_EVENT_DELETE_JSON_STRING =
      "{\"type\":\"DELETE\",\"version\":1,\"source\":123,\"target\":\"Coffee10\",\"properties\":{\"created_at\":1, \"permission\":\"public\", \"memo\":\"Latte\"}}";
  private static final String V2_CDC =
      "{\"table\":\"test_service.test_table_20250415_000000\",\"edge\":{\"ts\":1744667998065,\"src\":10132487,\"tgt\":2838957562,\"props\":{\"orderDisplay\":\"1\"},\"traceId\":\"01JRV51ZV7MX92D9M3Y47CE7A7\"},\"op\":\"UPDATE\",\"status\":\"IDLE\",\"before\":{\"active\":false,\"ts\":1744667983563,\"src\":10132487,\"tgt\":2838957562,\"props\":{\"orderDisplay\":\"1\"}},\"after\":{\"active\":false,\"ts\":1744667998065,\"src\":10132487,\"tgt\":2838957562,\"props\":{\"orderDisplay\":\"1\"}},\"acc\":0,\"alias\":\"test_service.test_table\",\"message\":null,\"audit\":{\"actor\":\"AsyncRequestProcessor\"},\"requestId\":\"41c4adba5cf86e0c555e10719729f8ab\",\"ts\":1744668000110,\"tenant\":\"test_tenant\",\"phase\":\"prod\",\"version\":\"2\"}";

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    codec = new StateCodecFactory().create();
  }

  /** Modifies label JSON string to set new type or direction */
  private String modifyLabelJson(String tableJson, String typeValue, String dirTypeValue) {
    String modifiedJson = tableJson;

    if (typeValue != null) {
      modifiedJson = modifiedJson.replace("\"type\":\"INDEXED\"", "\"type\":\"" + typeValue + "\"");
    }

    if (dirTypeValue != null) {
      modifiedJson =
          modifiedJson.replace("\"dirType\":\"BOTH\"", "\"dirType\":\"" + dirTypeValue + "\"");
    }

    return modifiedJson;
  }

  private EdgeState createEdgeState2(EdgeTableDescriptor table, String v2CdcJsonString)
      throws JsonProcessingException {
    return null;
  }

  /** Creates EdgeState for testing */
  private EdgeState createEdgeState(EdgeTableDescriptor table, String eventJsonString)
      throws JsonProcessingException {
    EdgeEventPayload event = objectMapper.readValue(eventJsonString, EdgeEventPayload.class);
    EdgeEvent castedEvent = table.schema().ensureType(event.toEvent());
    EdgeState initialState = Edges.initialStateOf(castedEvent.source(), castedEvent.target());
    return initialState.transit(castedEvent, table.schema().getPropertiesSchema());
  }

  /** Creates EdgeTableDescriptor for testing */
  private EdgeTableDescriptor createEdgeTableDescriptor(String tableJsonString)
      throws JsonProcessingException {
    LabelDescriptor table = objectMapper.readValue(tableJsonString, LabelDescriptor.class);
    LabelDescriptor newLabel =
        ImmutableLabelDescriptor.builder()
            .from(table)
            .name(RENAMED_LABEL)
            .desc(RENAMED_LABEL)
            .build();
    return newLabel.toV3(ImmutableTenantId.of(TENANT_ID));
  }

  /** Verifies encoded values */
  private void verifyEncodedValues(
      List<Encoded> encodedValues,
      EdgeState state,
      EdgeTableDescriptor table,
      List<byte[]> expectedCounterKeys) {
    encodedValues.forEach(
        kv -> {
          if (kv instanceof KeyValue<?>) {
            Object decoded =
                codec.decodeAny((KeyValue<byte[]>) kv, table.schema().getPropertiesSchema());
            if (decoded instanceof EdgeState) {
              verifyEdgeState((EdgeState) decoded, state);
            } else if (decoded instanceof StoredEdgeIndex) {
              verifyStoredEdgeIndex((StoredEdgeIndex) decoded, state, table);
            } else {
              throw new IllegalStateException("Unexpected value: " + decoded);
            }
          } else if (kv instanceof Key<?>) {
            verifyCounterKey((Key<byte[]>) kv, expectedCounterKeys);
          } else {
            throw new IllegalStateException("Unexpected value: " + kv);
          }
        });
  }

  private void verifyEdgeState(EdgeState decodedState, EdgeState originalState) {
    assertEquals(originalState.version(), decodedState.version());
    assertEquals(originalState.source(), decodedState.source());
    assertEquals(originalState.target(), decodedState.target());
    assertEquals(originalState.properties(), decodedState.properties());
  }

  private void verifyStoredEdgeIndex(
      StoredEdgeIndex decodedIndex, EdgeState state, EdgeTableDescriptor table) {
    assertEquals(state.version(), decodedIndex.version());
    assertEquals(state.source(), decodedIndex.source());
    assertEquals(state.target(), decodedIndex.target());
    EdgePayload payload =
        decodedIndex.toEdgePayload(table.schema().getPropertiesSchema().hashToFieldNameMap());
    assertEquals(state.toPayload(), payload);
  }

  private void verifyCounterKey(Key<byte[]> key, List<byte[]> expectedCounterKeys) {
    long matchCount = expectedCounterKeys.stream().filter(k -> Arrays.equals(k, key.key())).count();
    assertEquals(1, matchCount);
  }

  @Test
  void testFetchIndexedLabelAndEncodeEdges() throws JsonProcessingException {
    // Create label and state
    EdgeTableDescriptor table = createEdgeTableDescriptor(V2_BASE_LABEL_JSON_STRING);
    EdgeState state = createEdgeState(table, V3_EVENT_INSERT_JSON_STRING);

    // Encode and verify
    List<Encoded> encodedValues = BulkEncoder.encodeAll(codec, state, table);

    // 1 item for the hash edge.
    // 2 items for indexed edges within a single index, covering both OUTBOUND and INBOUND
    // directions.
    // 2 items for counter edges, one each for OUTBOUND and INBOUND directions.
    assertEquals(5, encodedValues.size());

    List<byte[]> expectedCounterKeys =
        Arrays.asList(
            codec.encodeEdgeCountKey(state.source(), Direction.OUT, table.code()),
            codec.encodeEdgeCountKey(state.target(), Direction.IN, table.code()));

    verifyEncodedValues(encodedValues, state, table, expectedCounterKeys);
  }

  @Test
  void testFetchIndexedLabelAndEncodeEdgesOutboundOnly() throws JsonProcessingException {
    // Create label that only processes outbound direction
    String tableJsonString = modifyLabelJson(V2_BASE_LABEL_JSON_STRING, null, "OUT");
    EdgeTableDescriptor table = createEdgeTableDescriptor(tableJsonString);
    EdgeState state = createEdgeState(table, V3_EVENT_INSERT_JSON_STRING);

    // Encode
    List<Encoded> encodedValues = BulkEncoder.encodeAll(codec, state, table);

    // 1 item for the hash edge.
    // 1 item for indexed edges within a single index, covering only OUTBOUND direction.
    // 1 item for counter edges, only for OUTBOUND direction.
    assertEquals(3, encodedValues.size());
  }

  @Test
  void testFetchIndexedLabelAndEncodeEdgesInboundOnly() throws JsonProcessingException {
    // Create label that only processes inbound direction
    String tableJsonString = modifyLabelJson(V2_BASE_LABEL_JSON_STRING, null, "IN");
    EdgeTableDescriptor table = createEdgeTableDescriptor(tableJsonString);
    EdgeState state = createEdgeState(table, V3_EVENT_INSERT_JSON_STRING);

    // Encode
    List<Encoded> encodedValues = BulkEncoder.encodeAll(codec, state, table);

    // 1 item for the hash edge.
    // 1 item for indexed edges within a single index, covering only INBOUND direction.
    // 1 item for counter edges, only for INBOUND direction.
    assertEquals(3, encodedValues.size());
  }

  @Test
  void testFetchHashLabelAndEncodeEdges() throws JsonProcessingException {
    // Create hash type label
    String tableJsonString = modifyLabelJson(V2_BASE_LABEL_JSON_STRING, "HASH", null);
    EdgeTableDescriptor table = createEdgeTableDescriptor(tableJsonString);
    EdgeState state = createEdgeState(table, V3_EVENT_INSERT_JSON_STRING);

    // Encode
    List<Encoded> encodedValues = BulkEncoder.encodeAll(codec, state, table);

    // 1 item for the hash edge.
    // 2 items for counter edges, one each for OUTBOUND and INBOUND directions.
    assertEquals(3, encodedValues.size());
  }

  @Test
  void testInactiveEdgeOnIndexedLabel() throws JsonProcessingException {
    // Process delete event on index label
    EdgeTableDescriptor table = createEdgeTableDescriptor(V2_BASE_LABEL_JSON_STRING);
    EdgeState state = createEdgeState(table, V3_EVENT_DELETE_JSON_STRING);

    // Encode
    List<Encoded> encodedValues = BulkEncoder.encodeAll(codec, state, table);

    // 1 item for the hash edge.
    assertEquals(1, encodedValues.size());
  }

  @Test
  void testInactiveEdgeOnHashLabel() throws JsonProcessingException {
    // Process delete event on hash label
    String tableJsonString = modifyLabelJson(V2_BASE_LABEL_JSON_STRING, "HASH", null);
    EdgeTableDescriptor table = createEdgeTableDescriptor(tableJsonString);
    EdgeState state = createEdgeState(table, V3_EVENT_DELETE_JSON_STRING);

    // Encode
    List<Encoded> encodedValues = BulkEncoder.encodeAll(codec, state, table);

    // 1 item for the hash edge.
    assertEquals(1, encodedValues.size());
  }
}
