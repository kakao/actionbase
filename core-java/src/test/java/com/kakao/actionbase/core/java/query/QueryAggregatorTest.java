package com.kakao.actionbase.core.java.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kakao.actionbase.core.java.codec.common.hbase.Order;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

@DisplayName("QueryAggregator Domain Test")
class QueryAggregatorTest {

  @Nested
  @DisplayName("QueryAggregator Creation Test")
  class QueryAggregatorCreationTest {

    @Test
    @DisplayName("Should be able to create Flatten type QueryAggregator using flatten() method")
    void shouldCreateFlattenAggregator() {
      // When
      QueryAggregator aggregator = QueryAggregator.flatten();

      // Then
      assertInstanceOf(QueryAggregator.Flatten.class, aggregator);
      assertEquals(QueryAggregator.FLATTEN_TYPE, aggregator.type());
    }

    @Test
    @DisplayName("Should be able to create Count type QueryAggregator using count() method")
    void shouldCreateCountAggregator() {
      // When
      QueryAggregator.Count aggregator = QueryAggregator.count("fieldName", Order.ASC, 10);

      // Then
      assertInstanceOf(QueryAggregator.Count.class, aggregator);
      assertEquals(QueryAggregator.COUNT_TYPE, aggregator.type());
      assertEquals("fieldName", aggregator.field());
      assertEquals(Order.ASC, aggregator.order());
      assertEquals(10, aggregator.limit());
    }

    @Test
    @DisplayName("Should be able to create Sum type QueryAggregator using sum() method")
    void shouldCreateSumAggregator() {
      // When
      QueryAggregator.Sum aggregator =
          QueryAggregator.sum("value", Arrays.asList("key1", "key2"), Order.DESC, 5);

      // Then
      assertInstanceOf(QueryAggregator.Sum.class, aggregator);
      assertEquals(QueryAggregator.SUM_TYPE, aggregator.type());
      assertEquals("value", aggregator.valueField());
      assertEquals(Arrays.asList("key1", "key2"), aggregator.keyFields());
      assertEquals(Order.DESC, aggregator.order());
      assertEquals(5, aggregator.limit());
    }
  }

  @Nested
  @DisplayName("QueryAggregator JSON Serialization Test")
  class QueryAggregatorSerializationTest {

    @Test
    @DisplayName(
        "Flatten type QueryAggregator JSON serialization/deserialization should maintain identity")
    void shouldSerializeAndDeserializeFlatten() throws JsonProcessingException {
      // Given
      QueryAggregator given = QueryAggregator.flatten();

      // When
      String serialized = given.toJson();
      QueryAggregator deserialized = QueryAggregator.fromJson(serialized);

      // Then
      assertEquals(given, deserialized);
      assertInstanceOf(QueryAggregator.Flatten.class, deserialized);
      assertEquals(QueryAggregator.FLATTEN_TYPE, deserialized.type());
    }

    @Test
    @DisplayName(
        "Count type QueryAggregator JSON serialization/deserialization should maintain identity")
    void shouldSerializeAndDeserializeCount() throws JsonProcessingException {
      // Given
      QueryAggregator given = QueryAggregator.count("fieldName", Order.ASC, 10);

      // When
      String serialized = given.toJson();
      QueryAggregator deserialized = QueryAggregator.fromJson(serialized);

      // Then
      assertEquals(given, deserialized);
      assertInstanceOf(QueryAggregator.Count.class, deserialized);
      QueryAggregator.Count count = (QueryAggregator.Count) deserialized;
      assertEquals("fieldName", count.field());
      assertEquals(Order.ASC, count.order());
      assertEquals(10, count.limit());
    }

    @Test
    @DisplayName(
        "Sum type QueryAggregator JSON serialization/deserialization should maintain identity")
    void shouldSerializeAndDeserializeSum() throws JsonProcessingException {
      // Given
      QueryAggregator given =
          QueryAggregator.sum("value", Arrays.asList("key1", "key2"), Order.DESC, 5);

      // When
      String serialized = given.toJson();
      QueryAggregator deserialized = QueryAggregator.fromJson(serialized);

      // Then
      assertEquals(given, deserialized);
      assertInstanceOf(QueryAggregator.Sum.class, deserialized);
      QueryAggregator.Sum sum = (QueryAggregator.Sum) deserialized;
      assertEquals("value", sum.valueField());
      assertEquals(Arrays.asList("key1", "key2"), sum.keyFields());
      assertEquals(Order.DESC, sum.order());
      assertEquals(5, sum.limit());
    }

    @Test
    @DisplayName("Should throw JsonProcessingException when deserializing from invalid JSON")
    void shouldThrowExceptionForInvalidJson() {
      // Given
      String invalidJson = "{invalid-json}";

      // When & Then
      assertThrows(
          JsonProcessingException.class,
          () -> QueryAggregator.fromJson(invalidJson),
          "JsonProcessingException should occur for invalid JSON format");
    }
  }
}
