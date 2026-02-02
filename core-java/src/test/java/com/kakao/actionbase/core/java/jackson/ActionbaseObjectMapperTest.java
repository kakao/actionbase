package com.kakao.actionbase.core.java.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kakao.actionbase.core.java.edge.EdgePayload;
import com.kakao.actionbase.core.java.edge.ImmutableEdgePayload;
import com.kakao.actionbase.core.java.metadata.v3.*;
import com.kakao.actionbase.core.java.metadata.v3.DatabaseDescriptor;
import com.kakao.actionbase.core.java.metadata.v3.ImmutableDatabaseDescriptor;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;

class ActionbaseObjectMapperTest {

  @Test
  void testEdgeJsonConversion() throws Exception {
    // Given
    EdgePayload edge =
        ImmutableEdgePayload.builder()
            .version(1)
            .source("source")
            .target("target")
            .putProperties("key", "value")
            .build();

    // When
    String json = ActionbaseObjectMapper.toJson(edge);
    EdgePayload deserializedEdge = ActionbaseObjectMapper.fromJson(json, EdgePayload.class);

    // Then
    assertEquals("source", deserializedEdge.source());
    assertEquals("target", deserializedEdge.target());
    assertEquals("value", deserializedEdge.properties().get("key"));
    assertEquals(
        "{\"version\":1,\"source\":\"source\",\"target\":\"target\",\"properties\":{\"key\":\"value\"},\"context\":{}}",
        json);
  }

  @Test
  void testEdgeJsonConversionWithEmptyProps() throws Exception {
    // Given
    EdgePayload edge =
        ImmutableEdgePayload.builder().version(1).source("source").target("target").build();

    // When
    String json = ActionbaseObjectMapper.toJson(edge);
    EdgePayload deserializedEdge = ActionbaseObjectMapper.fromJson(json, EdgePayload.class);

    // Then
    assertEquals("source", deserializedEdge.source());
    assertEquals("target", deserializedEdge.target());
    assertEquals(0, deserializedEdge.properties().size());
    assertEquals(
        "{\"version\":1,\"source\":\"source\",\"target\":\"target\",\"properties\":{},\"context\":{}}",
        json);
  }

  @Test
  void testTypeInferenceJsonConversion() throws Exception {
    // Given
    List<DatabaseDescriptor> databases =
        Arrays.asList(
            ImmutableDatabaseDescriptor.builder()
                .active(true)
                .tenant("test-tenant")
                .database("test-database1")
                .comment("Test database 1")
                .build(),
            ImmutableDatabaseDescriptor.builder()
                .active(true)
                .tenant("test-tenant")
                .database("test-database2")
                .comment("Test database 2")
                .build());
    Page<DatabaseDescriptor> page = ImmutablePage.of(databases);

    // When
    String json = ActionbaseObjectMapper.toJson(page);
    Page<DatabaseDescriptor> deserializedPage =
        ActionbaseObjectMapper.fromJson(json, new TypeReference<Page<DatabaseDescriptor>>() {});

    // Then
    assertEquals(2, deserializedPage.count());
    assertEquals(2, deserializedPage.content().size());
    assertEquals("test-database1", deserializedPage.content().get(0).database());
    assertEquals("test-database2", deserializedPage.content().get(1).database());
  }
}
