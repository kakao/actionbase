package com.kakao.actionbase.core.java.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

@DisplayName("QueryVertex Domain Test")
class QueryVertexTest {

  @Nested
  @DisplayName("QueryVertex Creation Test")
  class QueryVertexCreationTest {

    @Test
    @DisplayName("Should be able to create Constant type QueryVertex using constant() method")
    void shouldCreateConstantVertex() {
      // When
      QueryVertex.Constant vertex = QueryVertex.constant("value1", "value2");

      // Then
      assertInstanceOf(QueryVertex.Constant.class, vertex);
      assertEquals(2, vertex.values().size());
      assertEquals("value1", vertex.values().get(0));
      assertEquals("value2", vertex.values().get(1));
      assertEquals(QueryVertex.CONSTANT_TYPE, vertex.type());
    }

    @Test
    @DisplayName("Should be able to create Ref type QueryVertex using ref() method")
    void shouldCreateRefVertex() {
      // When
      QueryVertex.Ref vertex = QueryVertex.ref("refName", "fieldName");

      // Then
      assertInstanceOf(QueryVertex.Ref.class, vertex);
      assertEquals("refName", vertex.ref());
      assertEquals("fieldName", vertex.field());
      assertEquals(QueryVertex.REF_TYPE, vertex.type());
    }

    @Test
    @DisplayName("Should be able to create Step type QueryVertex using step() method")
    void shouldCreateStepVertex() {
      // Given
      QueryStep step =
          QueryStep.get()
              .name("stepName")
              .database("database")
              .table("table")
              .source(QueryVertex.constant("src"))
              .target(QueryVertex.constant("tgt"))
              .build();

      // When
      QueryVertex.Step vertex = QueryVertex.step(step, "fieldName");

      // Then
      assertInstanceOf(QueryVertex.Step.class, vertex);
      assertEquals(step, vertex.step());
      assertEquals("fieldName", vertex.field());
      assertEquals(QueryVertex.STEP_TYPE, vertex.type());
    }
  }

  @Nested
  @DisplayName("QueryVertex JSON Serialization Test")
  class QueryVertexSerializationTest {

    @Test
    @DisplayName(
        "Constant type QueryVertex JSON serialization/deserialization should maintain identity")
    void shouldSerializeAndDeserializeConstant() throws JsonProcessingException {
      // Given
      QueryVertex given = QueryVertex.constant("value1", "value2");

      // When
      String serialized = given.toJson();
      QueryVertex deserialized = QueryVertex.fromJson(serialized);

      // Then
      assertEquals(given, deserialized);
      assertInstanceOf(QueryVertex.Constant.class, deserialized);
      QueryVertex.Constant constantValue = (QueryVertex.Constant) deserialized;
      assertEquals(2, constantValue.values().size());
      assertEquals("value1", constantValue.values().get(0));
      assertEquals("value2", constantValue.values().get(1));
    }

    @Test
    @DisplayName("Ref type QueryVertex JSON serialization/deserialization should maintain identity")
    void shouldSerializeAndDeserializeRef() throws JsonProcessingException {
      // Given
      QueryVertex given = QueryVertex.ref("refName", "fieldName");

      // When
      String serialized = given.toJson();
      QueryVertex deserialized = QueryVertex.fromJson(serialized);

      // Then
      assertEquals(given, deserialized);
      assertInstanceOf(QueryVertex.Ref.class, deserialized);
      QueryVertex.Ref nameRef = (QueryVertex.Ref) deserialized;
      assertEquals("refName", nameRef.ref());
      assertEquals("fieldName", nameRef.field());
    }

    @Test
    @DisplayName("Should throw JsonProcessingException when deserializing from invalid JSON")
    void shouldThrowExceptionForInvalidJson() {
      // Given
      String invalidJson = "{invalid-json}";

      // When & Then
      assertThrows(
          JsonProcessingException.class,
          () -> QueryVertex.fromJson(invalidJson),
          "JsonProcessingException should occur for invalid JSON format");
    }
  }
}
