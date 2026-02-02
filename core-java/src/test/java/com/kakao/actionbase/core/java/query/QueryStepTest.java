package com.kakao.actionbase.core.java.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kakao.actionbase.core.java.dataframe.WherePredicate;
import com.kakao.actionbase.core.java.exception.ActionbaseException;
import com.kakao.actionbase.core.java.metadata.v3.common.Direction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("QueryStep domain test")
class QueryStepTest {

  @Nested
  @DisplayName("QueryStep creation test")
  class QueryStepCreationTest {

    @Test
    @DisplayName("Should be able to create Get type QueryStep with get() method")
    void shouldCreateGetStep() {
      // When
      QueryStep.Get step =
          QueryStep.get()
              .name("getUser")
              .database("userDatabase")
              .table("user")
              .source(QueryVertex.constant("user123"))
              .target(QueryVertex.constant("profile"))
              .include(true)
              .cache(true)
              .build();

      // Then
      assertInstanceOf(QueryStep.Get.class, step);
      assertEquals(QueryStep.GET_TYPE, step.type());
      assertEquals("getUser", step.name());
      assertEquals("userDatabase", step.database());
      assertEquals("user", step.table());
      assertEquals(QueryVertex.constant("user123"), step.source());
      assertEquals(QueryVertex.constant("profile"), step.target());
      assertTrue(step.include());
      assertTrue(step.cache());
    }

    @Test
    @DisplayName("Should be able to create Scan type QueryStep with scan() method")
    void shouldCreateScanStep() {
      // When
      QueryStep.Scan step =
          QueryStep.scan()
              .name("scanPurchases")
              .database("userDatabase")
              .table("purchase")
              .start(QueryVertex.constant("user123"))
              .direction(Direction.OUT)
              .index("timestamp")
              .limit(10)
              .offset("2024-01-01")
              .addPredicates(WherePredicate.eq("status", "active"))
              .build();

      // Then
      assertInstanceOf(QueryStep.Scan.class, step);
      assertEquals(QueryStep.SCAN_TYPE, step.type());
      assertEquals("scanPurchases", step.name());
      assertEquals("userDatabase", step.database());
      assertEquals("purchase", step.table());
      assertEquals(QueryVertex.constant("user123"), step.start());
      assertEquals(Direction.OUT, step.direction());
      assertEquals("timestamp", step.index());
      assertEquals(10, step.limit());
      assertEquals("2024-01-01", step.offset());
      assertEquals(1, step.predicates().size());
      assertInstanceOf(WherePredicate.Eq.class, step.predicates().get(0));
    }

    @Test
    @DisplayName("Should be able to create Count type QueryStep with count() method")
    void shouldCreateCountStep() {
      // When
      QueryStep.Count step =
          QueryStep.count()
              .name("countPurchases")
              .database("userDatabase")
              .table("purchase")
              .start(QueryVertex.constant("user123"))
              .direction(Direction.OUT)
              .build();

      // Then
      assertInstanceOf(QueryStep.Count.class, step);
      assertEquals(QueryStep.COUNT_TYPE, step.type());
      assertEquals("countPurchases", step.name());
      assertEquals("userDatabase", step.database());
      assertEquals("purchase", step.table());
      assertEquals(QueryVertex.constant("user123"), step.start());
      assertEquals(Direction.OUT, step.direction());
    }
  }

  @Nested
  @DisplayName("QueryStep JSON serialization test")
  class QueryStepSerializationTest {

    @Test
    @DisplayName("Get type QueryStep JSON serialization/deserialization should maintain identity")
    void shouldSerializeAndDeserializeGetStep() {
      // Given
      QueryStep given =
          QueryStep.get()
              .name("getUser")
              .database("userDatabase")
              .table("user")
              .source(QueryVertex.constant("user123"))
              .target(QueryVertex.constant("profile"))
              .include(true)
              .cache(true)
              .build();

      // When
      String serialized = given.toJson();
      QueryStep deserialized = QueryStep.fromJson(serialized);

      // Then
      assertEquals(given, deserialized);
      assertInstanceOf(QueryStep.Get.class, deserialized);
      QueryStep.Get getStep = (QueryStep.Get) deserialized;
      assertEquals("getUser", getStep.name());
      assertEquals("userDatabase", getStep.database());
      assertEquals("user", getStep.table());
      assertEquals(QueryVertex.constant("user123"), getStep.source());
      assertEquals(QueryVertex.constant("profile"), getStep.target());
      assertTrue(getStep.include());
      assertTrue(getStep.cache());
    }

    @Test
    @DisplayName("Scan type QueryStep JSON serialization/deserialization should maintain identity")
    void shouldSerializeAndDeserializeScanStep() {
      // Given
      QueryStep given =
          QueryStep.scan()
              .name("scanPurchases")
              .database("userDatabase")
              .table("purchase")
              .start(QueryVertex.constant("user123"))
              .direction(Direction.OUT)
              .index("timestamp")
              .limit(10)
              .offset("2024-01-01")
              .addPredicates(WherePredicate.eq("status", "active"))
              .build();

      // When
      String serialized = given.toJson();
      QueryStep deserialized = QueryStep.fromJson(serialized);

      // Then
      assertEquals(given, deserialized);
      assertInstanceOf(QueryStep.Scan.class, deserialized);
      QueryStep.Scan scanStep = (QueryStep.Scan) deserialized;
      assertEquals("scanPurchases", scanStep.name());
      assertEquals("userDatabase", scanStep.database());
      assertEquals("purchase", scanStep.table());
      assertEquals(QueryVertex.constant("user123"), scanStep.start());
      assertEquals(Direction.OUT, scanStep.direction());
      assertEquals("timestamp", scanStep.index());
      assertEquals(10, scanStep.limit());
      assertEquals("2024-01-01", scanStep.offset());
      assertEquals(1, scanStep.predicates().size());
      assertInstanceOf(WherePredicate.Eq.class, scanStep.predicates().get(0));
    }

    @Test
    @DisplayName("Count type QueryStep JSON serialization/deserialization should maintain identity")
    void shouldSerializeAndDeserializeCountStep() {
      // Given
      QueryStep given =
          QueryStep.count()
              .name("countPurchases")
              .database("userDatabase")
              .table("purchase")
              .start(QueryVertex.constant("user123"))
              .direction(Direction.OUT)
              .build();

      // When
      String serialized = given.toJson();
      QueryStep deserialized = QueryStep.fromJson(serialized);

      // Then
      assertEquals(given, deserialized);
      assertInstanceOf(QueryStep.Count.class, deserialized);
      QueryStep.Count countStep = (QueryStep.Count) deserialized;
      assertEquals("countPurchases", countStep.name());
      assertEquals("userDatabase", countStep.database());
      assertEquals("purchase", countStep.table());
      assertEquals(QueryVertex.constant("user123"), countStep.start());
      assertEquals(Direction.OUT, countStep.direction());
    }

    @Test
    @DisplayName("Should throw JsonProcessingException when deserializing from invalid JSON")
    void shouldThrowExceptionForInvalidJson() {
      // Given
      String invalidJson = "{invalid-json}";

      // When & Then
      assertThrows(
          ActionbaseException.class,
          () -> QueryStep.fromJson(invalidJson),
          "JsonProcessingException should be thrown for invalid JSON format");
    }
  }
}
