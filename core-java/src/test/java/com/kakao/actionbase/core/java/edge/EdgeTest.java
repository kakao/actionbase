package com.kakao.actionbase.core.java.edge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kakao.actionbase.core.java.jackson.ActionbaseObjectMapper;
import com.kakao.actionbase.core.java.state.EventType;

import org.junit.jupiter.api.Test;

class EdgeTest {

  @Test
  void testEdgeSerialization() {
    EdgePayload edge =
        ImmutableEdgePayload.builder()
            .version(1)
            .source("source")
            .target("target")
            .putProperties("key", "value")
            .build();
    String jsonString = ActionbaseObjectMapper.toJson(edge);

    assertEquals(
        "{\"version\":1,\"source\":\"source\",\"target\":\"target\",\"properties\":{\"key\":\"value\"},\"context\":{}}",
        jsonString);
  }

  @Test
  void testEdgeSerializationEmptyProps() {
    EdgePayload edge =
        ImmutableEdgePayload.builder().version(1).source("source").target("target").build();
    String jsonString = ActionbaseObjectMapper.toJson(edge);

    assertEquals(
        "{\"version\":1,\"source\":\"source\",\"target\":\"target\",\"properties\":{},\"context\":{}}",
        jsonString);
  }

  @Test
  void testEdgeDeserialization() {
    String jsonString =
        "{\"version\":1,\"source\":\"source\",\"target\":\"target\",\"properties\":{\"key\":\"value\"}}";
    EdgePayload edge = ActionbaseObjectMapper.fromJson(jsonString, EdgePayload.class);

    assertEquals(1L, edge.version());
    assertEquals("source", edge.source());
    assertEquals("target", edge.target());
    assertEquals("value", edge.properties().get("key"));
  }

  @Test
  void testEdgeDeserializationEmptyProps() {
    String jsonString =
        "{\"active\":true,\"version\":123,\"source\":\"source\",\"target\":\"target\",\"properties\":{}}";
    EdgePayload edge = ActionbaseObjectMapper.fromJson(jsonString, EdgePayload.class);

    assertEquals("source", edge.source());
    assertEquals("target", edge.target());
    assertEquals(0, edge.properties().size());
  }

  @Test
  void testTraceEdgeSerialization() {
    EdgeEvent edge =
        ImmutableEdgePayload.builder()
            .version(123)
            .source("source")
            .target("target")
            .build()
            .toEvent(EventType.INSERT);
    String jsonString = ActionbaseObjectMapper.toJson(edge);

    assertEquals(
        "{\"type\":\"INSERT\",\"version\":123,\"source\":\"source\",\"target\":\"target\",\"properties\":{}"
            + ",\"id\":\""
            + edge.id()
            + "\"}",
        jsonString);
  }
}
