package com.kakao.actionbase.core.java.jackson;

import com.kakao.actionbase.core.java.annotation.Nullable;
import com.kakao.actionbase.core.java.exception.ActionbaseException;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public class ActionbaseObjectMapper extends ObjectMapper {

  public static final ActionbaseObjectMapper INSTANCE = new ActionbaseObjectMapper();

  private static final ObjectWriter PRETTY_WRITER = INSTANCE.writerWithDefaultPrettyPrinter();

  private ActionbaseObjectMapper() {
    // ✅ Register all automatically added Jackson modules
    this.findAndRegisterModules();

    // ✅ Configure to not serialize NULL values
    this.setSerializationInclusion(JsonInclude.Include.NON_NULL);

    // ✅ **Configure to not throw errors even if unknown fields exist**
    this.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  public static JsonNode toJsonNode(Object value) throws ActionbaseException {
    try {
      return INSTANCE.valueToTree(value);
    } catch (IllegalArgumentException e) {
      throw new ActionbaseException("An error occurred while converting object to JSON string.", e);
    }
  }

  public static <T> T fromObject(Object obj, Class<T> valueType) throws ActionbaseException {
    try {
      return INSTANCE.convertValue(obj, valueType);
    } catch (IllegalArgumentException e) {
      throw new ActionbaseException(
          "An error occurred while converting to " + valueType.getCanonicalName() + " object.", e);
    }
  }

  public static String toJson(Object value) throws ActionbaseException {
    try {
      return INSTANCE.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new ActionbaseException("An error occurred while converting object to JSON string.", e);
    }
  }

  public static byte[] toJsonBytes(Object value) throws ActionbaseException {
    try {
      return INSTANCE.writeValueAsBytes(value);
    } catch (JsonProcessingException e) {
      throw new ActionbaseException("An error occurred while converting object to JSON string.", e);
    }
  }

  public static <T> T fromJson(String json, Class<T> valueType) throws ActionbaseException {
    try {
      return INSTANCE.readValue(json, valueType);
    } catch (JsonProcessingException e) {
      throw new ActionbaseException("An error occurred while converting JSON string to object.", e);
    }
  }

  public static <T> T fromJson(String json, TypeReference<T> typeReference)
      throws ActionbaseException {
    try {
      return INSTANCE.readValue(json, typeReference);
    } catch (JsonProcessingException e) {
      throw new ActionbaseException("An error occurred while converting JSON string to object.", e);
    }
  }

  public static String toPrettyJson(Object value) throws JsonProcessingException {
    return PRETTY_WRITER.writeValueAsString(value);
  }

  public static Map<String, @Nullable Object> toMap(Object value) {
    return INSTANCE.convertValue(value, new TypeReference<Map<String, @Nullable Object>>() {});
  }
}
