package com.kakao.actionbase.core.java.dataframe;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

@DisplayName("WherePredicate Test")
class WherePredicateTest {

  @Nested
  @DisplayName("JSON Serialization Test")
  class JsonSerializationTest {

    @Test
    @DisplayName("In condition serialization/deserialization should maintain identity")
    void shouldSerializeAndDeserializeIn() throws JsonProcessingException {
      // Given
      WherePredicate.In predicate =
          WherePredicate.in("category", Arrays.asList("food", "beverage", "snack"));

      // When
      String serialized = predicate.toJson();
      WherePredicate deserialized = WherePredicate.fromJson(serialized);

      // Then
      assertInstanceOf(
          WherePredicate.In.class, deserialized, "Deserialized object should be In type");
      WherePredicate.In inPredicate = (WherePredicate.In) deserialized;
      assertEquals("category", inPredicate.key(), "key value should be preserved");
      assertEquals(3, inPredicate.values().size(), "values size should be preserved");
      assertTrue(
          inPredicate.values().containsAll(Arrays.asList("food", "beverage", "snack")),
          "values content should be preserved");
    }

    @Test
    @DisplayName("Eq condition serialization/deserialization should maintain identity")
    void shouldSerializeAndDeserializeEq() throws JsonProcessingException {
      // Given
      WherePredicate.Eq predicate = WherePredicate.eq("status", "active");

      // When
      String serialized = predicate.toJson();
      WherePredicate deserialized = WherePredicate.fromJson(serialized);

      // Then
      assertInstanceOf(
          WherePredicate.Eq.class, deserialized, "Deserialized object should be Eq type");
      WherePredicate.Eq eqPredicate = (WherePredicate.Eq) deserialized;
      assertEquals("status", eqPredicate.key(), "key value should be preserved");
      assertEquals("active", eqPredicate.value(), "value content should be preserved");
    }

    @Test
    @DisplayName("Gt condition serialization/deserialization should maintain identity")
    void shouldSerializeAndDeserializeGt() throws JsonProcessingException {
      // Given
      WherePredicate.Gt predicate = WherePredicate.gt("price", 1000);

      // When
      String serialized = predicate.toJson();
      WherePredicate deserialized = WherePredicate.fromJson(serialized);

      // Then
      assertInstanceOf(
          WherePredicate.Gt.class, deserialized, "Deserialized object should be Gt type");
      WherePredicate.Gt gtPredicate = (WherePredicate.Gt) deserialized;
      assertEquals("price", gtPredicate.key(), "key value should be preserved");
      assertEquals(1000, gtPredicate.value(), "value should be preserved");
    }

    @Test
    @DisplayName("Gte condition serialization/deserialization should maintain identity")
    void shouldSerializeAndDeserializeGte() throws JsonProcessingException {
      // Given
      WherePredicate.Gte predicate = WherePredicate.gte("age", 18);

      // When
      String serialized = predicate.toJson();
      WherePredicate deserialized = WherePredicate.fromJson(serialized);

      // Then
      assertInstanceOf(
          WherePredicate.Gte.class, deserialized, "Deserialized object should be Gte type");
      WherePredicate.Gte gtePredicate = (WherePredicate.Gte) deserialized;
      assertEquals("age", gtePredicate.key(), "key value should be preserved");
      assertEquals(18, gtePredicate.value(), "value should be preserved");
    }

    @Test
    @DisplayName("Lt condition serialization/deserialization should maintain identity")
    void shouldSerializeAndDeserializeLt() throws JsonProcessingException {
      // Given
      WherePredicate.Lt predicate = WherePredicate.lt("temperature", 30);

      // When
      String serialized = predicate.toJson();
      WherePredicate deserialized = WherePredicate.fromJson(serialized);

      // Then
      assertInstanceOf(
          WherePredicate.Lt.class, deserialized, "Deserialized object should be Lt type");
      WherePredicate.Lt ltPredicate = (WherePredicate.Lt) deserialized;
      assertEquals("temperature", ltPredicate.key(), "key value should be preserved");
      assertEquals(30, ltPredicate.value(), "value should be preserved");
    }

    @Test
    @DisplayName("Lte condition serialization/deserialization should maintain identity")
    void shouldSerializeAndDeserializeLte() throws JsonProcessingException {
      // Given
      WherePredicate.Lte predicate = WherePredicate.lte("weight", 100);

      // When
      String serialized = predicate.toJson();
      WherePredicate deserialized = WherePredicate.fromJson(serialized);

      // Then
      assertInstanceOf(
          WherePredicate.Lte.class, deserialized, "Deserialized object should be Lte type");
      WherePredicate.Lte ltePredicate = (WherePredicate.Lte) deserialized;
      assertEquals("weight", ltePredicate.key(), "key value should be preserved");
      assertEquals(100, ltePredicate.value(), "value should be preserved");
    }

    @Test
    @DisplayName("Between condition serialization/deserialization should maintain identity")
    void shouldSerializeAndDeserializeBetween() throws JsonProcessingException {
      // Given
      WherePredicate.Between predicate = WherePredicate.between("date", "2023-01-01", "2023-12-31");

      // When
      String serialized = predicate.toJson();
      WherePredicate deserialized = WherePredicate.fromJson(serialized);

      // Then
      assertInstanceOf(
          WherePredicate.Between.class, deserialized, "Deserialized object should be Between type");
      WherePredicate.Between betweenPredicate = (WherePredicate.Between) deserialized;
      assertEquals("date", betweenPredicate.key(), "key value should be preserved");
      assertEquals("2023-01-01", betweenPredicate.fromValue(), "fromValue should be preserved");
      assertEquals("2023-12-31", betweenPredicate.toValue(), "toValue should be preserved");
    }

    @Test
    @DisplayName("IsNull condition serialization/deserialization should maintain identity")
    void shouldSerializeAndDeserializeIsNull() throws JsonProcessingException {
      // Given
      WherePredicate.IsNull predicate = WherePredicate.isNull("deletedAt");

      // When
      String serialized = predicate.toJson();
      WherePredicate deserialized = WherePredicate.fromJson(serialized);

      // Then
      assertInstanceOf(
          WherePredicate.IsNull.class, deserialized, "Deserialized object should be IsNull type");
      WherePredicate.IsNull isNullPredicate = (WherePredicate.IsNull) deserialized;
      assertEquals("deletedAt", isNullPredicate.key(), "key value should be preserved");
    }
  }
}
