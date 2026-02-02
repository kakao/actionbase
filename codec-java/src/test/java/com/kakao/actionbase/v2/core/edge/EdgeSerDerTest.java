package com.kakao.actionbase.v2.core.edge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class EdgeSerDerTest {

  ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void testEdgeSerialization() throws JsonProcessingException {
    HashMap<String, Object> props = new HashMap<>();
    props.put("key", "value");
    Edge edge = new Edge(123, "src", "tgt", props);
    String jsonString = objectMapper.writeValueAsString(edge);

    assertEquals(
        "{\"ts\":123,\"src\":\"src\",\"tgt\":\"tgt\",\"props\":{\"key\":\"value\"}}", jsonString);
  }

  @Test
  void testEdgeSerializationEmptyProps() throws JsonProcessingException {
    Edge edge = new Edge(123, "src", "tgt");
    String jsonString = objectMapper.writeValueAsString(edge);

    assertEquals("{\"ts\":123,\"src\":\"src\",\"tgt\":\"tgt\",\"props\":{}}", jsonString);
  }

  @Test
  void testEdgeDeserialization() throws JsonProcessingException {
    String jsonString =
        "{\"ts\":123,\"src\":\"src\",\"tgt\":\"tgt\",\"props\":{\"key\":\"value\"}}";
    Edge edge = objectMapper.readValue(jsonString, Edge.class);

    assertEquals(123, edge.getTs());
    assertEquals("src", edge.getSrc());
    assertEquals("tgt", edge.getTgt());
    assertEquals("value", edge.getProps().get("key"));
  }

  @Test
  void testEdgeDeserializationEmptyProps() throws JsonProcessingException {
    String jsonString = "{\"ts\":123,\"src\":\"src\",\"tgt\":\"tgt\",\"props\":{}}";
    Edge edge = objectMapper.readValue(jsonString, Edge.class);

    assertEquals(123, edge.getTs());
    assertEquals("src", edge.getSrc());
    assertEquals("tgt", edge.getTgt());
    assertEquals(0, edge.props.size());
  }

  @Test
  void testTraceEdgeSerialization() throws JsonProcessingException {
    TraceEdge edge = new Edge(123, "src", "tgt").toTraceEdge();
    String jsonString = objectMapper.writeValueAsString(edge);

    assertEquals(
        "{\"ts\":123,\"src\":\"src\",\"tgt\":\"tgt\",\"props\":{},\"traceId\":\""
            + edge.traceId
            + "\"}",
        jsonString);
  }
}
