package com.kakao.actionbase.core.java.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kakao.actionbase.core.java.dataframe.WherePredicate;
import com.kakao.actionbase.core.java.metadata.v3.common.Direction;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;

@DisplayName("Query Domain Test")
class QueryTest {

  @Nested
  @DisplayName("Query Creation Test")
  class QueryCreationTest {

    @Test
    @DisplayName("Should be able to create Query with empty step list")
    void shouldCreateEmptyQuery() {
      // When
      Query query = ImmutableQuery.builder().build();

      // Then
      assertTrue(query.steps().isEmpty());
    }

    @Test
    @DisplayName("Should throw NullPointerException when adding null step")
    void shouldThrowExceptionWhenAddingNullStep() {
      // When & Then
      assertThrows(
          NullPointerException.class,
          () -> ImmutableQuery.builder().addSteps((QueryStep) null).build(),
          "NullPointerException should occur when adding null step");
    }
  }

  @Nested
  @DisplayName("Query JSON Serialization Test")
  class JsonSerializationTest {

    @Test
    @DisplayName(
        "Serialization/deserialization of query with GET query step should maintain identity")
    void shouldSerializeAndDeserializeGetQueryStep() throws JsonProcessingException {
      // Given
      Query given =
          ImmutableQuery.builder()
              .addSteps(
                  QueryStep.get()
                      .name("getUser")
                      .database("userDatabase")
                      .table("user")
                      .source(QueryVertex.constant("user123"))
                      .target(QueryVertex.constant("profile"))
                      .include(true)
                      .cache(true)
                      .build())
              .build();

      // When
      String serialized = given.toJson();
      Query deserialized = Query.fromJson(serialized);

      // Then
      assertEquals(
          given, deserialized, "Serialized/deserialized object should be identical to original");
      assertEquals(1, deserialized.steps().size(), "Number of query steps should be preserved");

      QueryStep.Get getStep = (QueryStep.Get) deserialized.steps().get(0);
      assertEquals("getUser", getStep.name(), "Query step name should be preserved");
      assertEquals("userDatabase", getStep.database(), "Service information should be preserved");
      assertEquals("user", getStep.table(), "Label information should be preserved");
      assertEquals(
          QueryVertex.constant("user123"),
          getStep.source(),
          "Source information should be preserved");
      assertEquals(
          QueryVertex.constant("profile"),
          getStep.target(),
          "Target information should be preserved");
      assertTrue(getStep.include(), "include information should be preserved");
      assertTrue(getStep.cache(), "cache information should be preserved");
    }

    @Test
    @DisplayName(
        "Serialization/deserialization of query with SCAN query step should maintain identity")
    void shouldSerializeAndDeserializeScanQueryStep() throws JsonProcessingException {
      // Given
      Query given =
          ImmutableQuery.builder()
              .addSteps(
                  QueryStep.scan()
                      .name("scanUserProducts")
                      .database("userDatabase")
                      .table("purchase")
                      .start(QueryVertex.constant("user123"))
                      .direction(Direction.OUT)
                      .index("timestamp")
                      .limit(10)
                      .offset("2024-01-01")
                      .addAggregators(QueryAggregator.flatten())
                      .build())
              .build();

      // When
      String serialized = given.toJson();
      Query deserialized = Query.fromJson(serialized);

      // Then
      assertEquals(
          given, deserialized, "Serialized/deserialized object should be identical to original");
      assertEquals(1, deserialized.steps().size(), "Number of query steps should be preserved");

      QueryStep.Scan scanStep = (QueryStep.Scan) deserialized.steps().get(0);
      assertEquals("scanUserProducts", scanStep.name(), "Query step name should be preserved");
      assertEquals("userDatabase", scanStep.database(), "Service information should be preserved");
      assertEquals("purchase", scanStep.table(), "Label information should be preserved");
      assertEquals(
          QueryVertex.constant("user123"),
          scanStep.start(),
          "Source information should be preserved");
      assertEquals(
          Direction.OUT, scanStep.direction(), "Direction information should be preserved");
      assertEquals("timestamp", scanStep.index(), "Index information should be preserved");
      assertNotNull(scanStep.limit(), "Limit count should exist");
      assertEquals(10, scanStep.limit(), "Limit count should be preserved");
      assertNotNull(scanStep.offset(), "Offset information should exist");
      assertEquals("2024-01-01", scanStep.offset(), "Offset information should be preserved");
      assertEquals(
          1, scanStep.aggregators().size(), "Aggregator function information should be preserved");
      assertEquals(
          QueryAggregator.flatten(),
          scanStep.aggregators().get(0),
          "Aggregator function information should be preserved");
    }

    @Test
    @DisplayName(
        "Serialization/deserialization of SCAN query step with WherePredicate should maintain identity")
    void shouldSerializeAndDeserializeScanQueryStepWithWherePredicate()
        throws JsonProcessingException {
      // Given
      WherePredicate.In inPredicate =
          WherePredicate.in("category", Arrays.asList("food", "beverage"));
      WherePredicate.Eq eqPredicate = WherePredicate.eq("status", "active");
      WherePredicate.Gt gtPredicate = WherePredicate.gt("price", 1000);

      Query given =
          ImmutableQuery.builder()
              .addSteps(
                  QueryStep.scan()
                      .name("scanUserProducts")
                      .database("userDatabase")
                      .table("purchase")
                      .start(QueryVertex.constant("user123"))
                      .direction(Direction.OUT)
                      .index("timestamp")
                      .limit(10)
                      .offset("2024-01-01")
                      .addPredicates(inPredicate, eqPredicate, gtPredicate)
                      .build())
              .build();

      // When
      String serialized = given.toJson();
      Query deserialized = Query.fromJson(serialized);

      // Then
      assertEquals(
          given, deserialized, "Serialized/deserialized object should be identical to original");
      assertEquals(1, deserialized.steps().size(), "Number of query steps should be preserved");

      QueryStep.Scan scanStep = (QueryStep.Scan) deserialized.steps().get(0);
      assertEquals("scanUserProducts", scanStep.name(), "Query step name should be preserved");
      assertEquals("userDatabase", scanStep.database(), "Service information should be preserved");
      assertEquals("purchase", scanStep.table(), "Label information should be preserved");
      assertEquals(
          QueryVertex.constant("user123"),
          scanStep.start(),
          "Source information should be preserved");
      assertEquals(
          Direction.OUT, scanStep.direction(), "Direction information should be preserved");
      assertEquals("timestamp", scanStep.index(), "Index information should be preserved");
      assertNotNull(scanStep.limit(), "Limit count should exist");
      assertEquals(10, scanStep.limit(), "Limit count should be preserved");
      assertNotNull(scanStep.offset(), "Offset information should exist");
      assertEquals("2024-01-01", scanStep.offset(), "Offset information should be preserved");

      assertEquals(3, scanStep.predicates().size(), "Predicate information should be preserved");
      assertTrue(
          scanStep.predicates().contains(inPredicate),
          "In predicate information should be preserved");
      assertTrue(
          scanStep.predicates().contains(eqPredicate),
          "Eq predicate information should be preserved");
      assertTrue(
          scanStep.predicates().contains(gtPredicate),
          "Gt predicate information should be preserved");
    }

    @Test
    @DisplayName(
        "Serialization/deserialization of query with composite query steps should maintain identity")
    void shouldSerializeAndDeserializeCompositeQuerySteps() throws JsonProcessingException {
      // Given
      Query given =
          ImmutableQuery.builder()
              .addSteps(
                  QueryStep.get()
                      .name("getUser")
                      .database("userDatabase")
                      .table("user")
                      .source(QueryVertex.constant("user123"))
                      .target(QueryVertex.constant("profile"))
                      .build())
              .addSteps(
                  QueryStep.scan()
                      .name("scanUserProducts")
                      .database("userDatabase")
                      .table("purchase")
                      .start(QueryVertex.ref("getUser", ""))
                      .direction(Direction.OUT)
                      .index("timestamp")
                      .limit(10)
                      .build())
              .addSteps(
                  QueryStep.count()
                      .name("countUserProducts")
                      .database("userDatabase")
                      .table("purchase")
                      .start(QueryVertex.ref("getUser", ""))
                      .direction(Direction.OUT)
                      .build())
              .build();

      // When
      String serialized = given.toJson();
      Query deserialized = Query.fromJson(serialized);

      // Then
      assertEquals(
          given, deserialized, "Serialized/deserialized object should be identical to original");
      assertEquals(3, deserialized.steps().size(), "Number of query steps should be preserved");

      // Verify first step
      assertInstanceOf(QueryStep.Get.class, deserialized.steps().get(0));
      QueryStep.Get getStep = (QueryStep.Get) deserialized.steps().get(0);
      assertEquals("getUser", getStep.name());
      assertEquals(QueryVertex.constant("user123"), getStep.source());
      assertEquals(QueryVertex.constant("profile"), getStep.target());

      // Verify second step
      assertInstanceOf(QueryStep.Scan.class, deserialized.steps().get(1));
      QueryStep.Scan scanStep = (QueryStep.Scan) deserialized.steps().get(1);
      assertEquals("scanUserProducts", scanStep.name());
      assertEquals(QueryVertex.ref("getUser", ""), scanStep.start());
      assertNotNull(scanStep.limit(), "Limit count should exist");
      assertEquals(10, scanStep.limit(), "Limit count should be preserved");

      // Verify third step
      assertInstanceOf(QueryStep.Count.class, deserialized.steps().get(2));
      QueryStep.Count countStep = (QueryStep.Count) deserialized.steps().get(2);
      assertEquals("countUserProducts", countStep.name());
      assertEquals(QueryVertex.ref("getUser", ""), countStep.start());
    }

    @Test
    @DisplayName("Should be able to create Query object from user-provided JSON string")
    void shouldCreateQueryFromUserProvidedJson() throws JsonProcessingException {
      // Given
      String userProvidedJson =
          "{"
              + "\"steps\":["
              + "{"
              + "\"type\":\"get\","
              + "\"name\":\"getUser\","
              + "\"database\":\"userDatabase\","
              + "\"table\":\"user\","
              + "\"source\":{\"type\":\"constant\",\"values\":[\"user123\"]},"
              + "\"target\":{\"type\":\"constant\",\"values\":[\"profile\"]}"
              + "}"
              + "]"
              + "}";

      // When
      Query query = Query.fromJson(userProvidedJson);

      // Then
      assertEquals(1, query.steps().size());
      QueryStep.Get getStep = (QueryStep.Get) query.steps().get(0);
      assertEquals("getUser", getStep.name());
      assertEquals("userDatabase", getStep.database());
      assertEquals("user", getStep.table());
      assertEquals(QueryVertex.constant("user123"), getStep.source());
      assertEquals(QueryVertex.constant("profile"), getStep.target());
      assertFalse(getStep.include());
      assertFalse(getStep.cache());
      assertTrue(getStep.aggregators().isEmpty());
      assertTrue(getStep.predicates().isEmpty());
    }

    @Test
    @DisplayName(
        "Fields with default values should have default values set even if omitted in JSON")
    void shouldSetDefaultValuesWhenOmittedInJson() throws JsonProcessingException {
      // Given
      String jsonWithoutDefaults =
          "{"
              + "\"steps\":["
              + "{"
              + "\"type\":\"scan\","
              + "\"name\":\"scanProducts\","
              + "\"database\":\"userDatabase\","
              + "\"table\":\"purchase\","
              + "\"start\":{\"type\":\"constant\",\"values\":[\"user123\"]},"
              + "\"direction\":\"OUT\","
              + "\"index\":\"timestamp\","
              + "\"limit\":10,"
              + "\"offset\":\"2024-01-01\""
              + "}"
              + "]"
              + "}";

      // When
      Query query = Query.fromJson(jsonWithoutDefaults);

      // Then
      assertEquals(1, query.steps().size());
      QueryStep.Scan scanStep = (QueryStep.Scan) query.steps().get(0);
      // Verify explicitly set values
      assertEquals("scanProducts", scanStep.name());
      assertEquals("userDatabase", scanStep.database());
      assertEquals("purchase", scanStep.table());
      assertEquals("user123", ((QueryVertex.Constant) scanStep.start()).values().get(0));
      assertEquals(Direction.OUT, scanStep.direction());
      assertEquals("timestamp", scanStep.index());
      assertNotNull(scanStep.limit(), "Limit count should exist");
      assertEquals(10, scanStep.limit(), "Limit count should be preserved");
      assertNotNull(scanStep.offset(), "Offset information should exist");
      assertEquals("2024-01-01", scanStep.offset(), "Offset information should be preserved");
      // Verify default values
      assertFalse(scanStep.include());
      assertFalse(scanStep.cache());
      assertTrue(scanStep.aggregators().isEmpty());
      assertTrue(scanStep.predicates().isEmpty());
    }

    @Test
    @DisplayName(
        "When fields with default values are explicitly set in JSON, those values should be applied")
    void shouldOverrideDefaultValuesWhenSpecifiedInJson() throws JsonProcessingException {
      // Given
      String jsonWithDefaults =
          "{"
              + "\"steps\":["
              + "{"
              + "\"type\":\"scan\","
              + "\"name\":\"scanProducts\","
              + "\"database\":\"userDatabase\","
              + "\"table\":\"purchase\","
              + "\"start\":{\"type\":\"constant\",\"values\":[\"user123\"]},"
              + "\"direction\":\"OUT\","
              + "\"index\":\"timestamp\","
              + "\"limit\":10,"
              + "\"offset\":\"2024-01-01\","
              + "\"include\":true,"
              + "\"cache\":true,"
              + "\"aggregators\":[{\"type\":\"flatten\"}],"
              + "\"predicates\":[{\"type\":\"eq\",\"key\":\"status\",\"value\":\"active\"}]"
              + "}"
              + "]"
              + "}";

      // When
      Query query = Query.fromJson(jsonWithDefaults);

      // Then
      assertEquals(1, query.steps().size());
      QueryStep.Scan scanStep = (QueryStep.Scan) query.steps().get(0);
      // Verify explicitly set values
      assertEquals("scanProducts", scanStep.name());
      assertEquals("userDatabase", scanStep.database());
      assertEquals("purchase", scanStep.table());
      assertEquals("user123", ((QueryVertex.Constant) scanStep.start()).values().get(0));
      assertEquals(Direction.OUT, scanStep.direction());
      assertEquals("timestamp", scanStep.index());
      assertNotNull(scanStep.limit(), "Limit count should exist");
      assertEquals(Integer.valueOf(10), scanStep.limit(), "Limit count should be preserved");
      assertNotNull(scanStep.offset(), "Offset information should exist");
      assertEquals("2024-01-01", scanStep.offset(), "Offset information should be preserved");
      // Verify explicitly set default values
      assertTrue(scanStep.include());
      assertTrue(scanStep.cache());
      assertEquals(1, scanStep.aggregators().size());
      assertEquals(QueryAggregator.flatten(), scanStep.aggregators().get(0));
      assertEquals(1, scanStep.predicates().size());
      assertInstanceOf(WherePredicate.Eq.class, scanStep.predicates().get(0));
    }
  }

  @Nested
  @DisplayName("Query JSON Conversion Exception Test")
  class JsonExceptionTest {

    @Test
    @DisplayName("Should throw RuntimeException when deserializing from invalid JSON string")
    void shouldThrowRuntimeExceptionForInvalidJson() {
      // Given
      String invalidJson = "{invalid-json}";

      // When & Then
      assertThrows(
          JsonParseException.class,
          () -> Query.fromJson(invalidJson),
          "JsonParseException should occur for invalid JSON format");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when deserializing from null JSON string")
    void shouldThrowExceptionForNullJson() {
      // When & Then
      assertThrows(
          IllegalArgumentException.class,
          () -> Query.fromJson(null),
          "IllegalArgumentException should occur for null JSON string");
    }

    @Test
    @DisplayName("Should throw JsonProcessingException when deserializing from empty JSON string")
    void shouldThrowExceptionForEmptyJson() {
      // Given
      String emptyJson = "";

      // When & Then
      assertThrows(
          JsonProcessingException.class,
          () -> Query.fromJson(emptyJson),
          "JsonProcessingException should occur for empty JSON string");
    }

    @Test
    @DisplayName("Deserialization of JSON with unknown fields should be ignored")
    void shouldIgnoreUnknownProperties() throws JsonProcessingException {
      // Given
      String jsonWithUnknownProperties =
          "{"
              + "\"steps\":["
              + "{"
              + "\"type\":\"get\","
              + "\"name\":\"getUser\","
              + "\"database\":\"userDatabase\","
              + "\"table\":\"user\","
              + "\"source\":{\"type\":\"constant\",\"values\":[\"user123\"]},"
              + "\"target\":{\"type\":\"constant\",\"values\":[\"profile\"]},"
              + "\"unknown\":\"value\""
              + "}"
              + "],"
              + "\"unknown\":\"value\""
              + "}";

      // When
      Query query = Query.fromJson(jsonWithUnknownProperties);

      // Then
      assertEquals(1, query.steps().size());
      assertEquals("getUser", query.steps().get(0).name());
    }
  }

  @Nested
  @DisplayName("Query Equality Test")
  class QueryEqualityTest {

    @Test
    @DisplayName("Query objects with the same steps should be equal")
    void shouldBeEqualWithSameSteps() {
      // Given
      Query query1 =
          ImmutableQuery.builder()
              .addSteps(
                  QueryStep.get()
                      .name("getUser")
                      .database("userDatabase")
                      .table("user")
                      .source(QueryVertex.constant("user123"))
                      .target(QueryVertex.constant("profile"))
                      .build())
              .build();

      Query query2 =
          ImmutableQuery.builder()
              .addSteps(
                  QueryStep.get()
                      .name("getUser")
                      .database("userDatabase")
                      .table("user")
                      .source(QueryVertex.constant("user123"))
                      .target(QueryVertex.constant("profile"))
                      .build())
              .build();

      // Then
      assertEquals(query1, query2);
      assertEquals(query1.hashCode(), query2.hashCode());
    }

    @Test
    @DisplayName("Query objects with different steps should not be equal")
    void shouldNotBeEqualWithDifferentSteps() {
      // Given
      Query query1 =
          ImmutableQuery.builder()
              .addSteps(
                  QueryStep.get()
                      .name("getUser")
                      .database("userDatabase")
                      .table("user")
                      .source(QueryVertex.constant("user123"))
                      .target(QueryVertex.constant("profile"))
                      .build())
              .build();

      Query query2 =
          ImmutableQuery.builder()
              .addSteps(
                  QueryStep.get()
                      .name("getDifferentUser")
                      .database("userDatabase")
                      .table("user")
                      .source(QueryVertex.constant("user456"))
                      .target(QueryVertex.constant("profile"))
                      .build())
              .build();

      // Then
      assertNotEquals(query1, query2);
      assertNotEquals(query1.hashCode(), query2.hashCode());
    }
  }
}
