package com.kakao.actionbase.core.java.pipeline;

import static org.junit.jupiter.api.Assertions.*;

import com.kakao.actionbase.test.ObjectAssertions;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Abstract base class for deserializer tests with common test methods.
 *
 * @param <T> The type of message being tested (CDC or WAL)
 */
public abstract class AbstractDeserializerTest<T> {

  protected static ObjectMapper objectMapper;

  @BeforeEach
  public void setup() throws JsonProcessingException {
    objectMapper = new ObjectMapper();
    initializeTestData();
  }

  /** Initialize test data for specific implementations. */
  protected abstract void initializeTestData() throws JsonProcessingException;

  /** Get the class type for deserialization. */
  protected abstract Class<T> getMessageClass();

  /** Create unknown version JSON string. */
  protected String createUnknownVersionJson() {
    return "{\"" + MessageConstants.VERSION_FIELD + "\":\"unknown\"}";
  }

  /** Test deserialization and serialization. */
  protected void testDeserializeAndSerialize(String json, Class<? extends T> expectedClass)
      throws Exception {
    T deserializedObj = objectMapper.readValue(json, expectedClass);
    ObjectAssertions.assertEquals(json, deserializedObj);
  }

  /** Test deserialization with valid JSON. */
  protected void testDeserializeWithValidJson(
      ObjectNode testData, Class<? extends T> expectedClass, String errorMessage)
      throws IOException {
    String json = objectMapper.writeValueAsString(testData.deepCopy());
    T message = objectMapper.readValue(json, getMessageClass());
    assertInstanceOf(expectedClass, message, errorMessage);
  }

  /** Test missing fields. */
  protected void testMissingFields(
      ObjectNode originalData, Set<String> nullableFields, Class<? extends T> expectedClass)
      throws IOException {
    Iterator<String> fieldNames = originalData.fieldNames();

    while (fieldNames.hasNext()) {
      String fieldName = fieldNames.next();
      testMissingField(originalData, fieldName, nullableFields, expectedClass);
    }
  }

  /** Test a single missing field. */
  protected void testMissingField(
      ObjectNode originalData,
      String fieldName,
      Set<String> nullableFields,
      Class<? extends T> expectedClass)
      throws IOException {
    ObjectNode testData = originalData.deepCopy();
    testData.remove(fieldName);
    String json = objectMapper.writeValueAsString(testData);

    if (nullableFields.contains(fieldName)) {
      T message = objectMapper.readValue(json, getMessageClass());
      assertInstanceOf(expectedClass, message, "Deserialized object type differs from expected");
    } else {
      assertThrows(
          JsonProcessingException.class,
          () -> objectMapper.readValue(json, getMessageClass()),
          "Exception should occur when required field '" + fieldName + "' is missing");
    }
  }

  /** Test with all nullable fields removed. */
  protected void testWithAllNullableFieldsRemoved(
      ObjectNode originalData,
      Set<String> nullableFields,
      Class<? extends T> expectedClass,
      String errorMessage)
      throws IOException {
    ObjectNode testData = originalData.deepCopy();
    nullableFields.forEach(testData::remove);
    String json = objectMapper.writeValueAsString(testData);

    T message = objectMapper.readValue(json, getMessageClass());
    assertInstanceOf(expectedClass, message, errorMessage);
  }

  @Test
  public void testDeserializeWithEmptyJson() {
    assertThrows(
        Exception.class,
        () -> objectMapper.readValue("{}", getMessageClass()),
        "Exception should occur for empty JSON");
  }

  @Test
  public void testDeserializeUnknownVersion() {
    String json = createUnknownVersionJson();

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> objectMapper.readValue(json, getMessageClass()),
            "Exception should occur for unknown version");

    assertEquals(
        "Unknown version: unknown", exception.getMessage(), "Exception message does not match");
  }
}
