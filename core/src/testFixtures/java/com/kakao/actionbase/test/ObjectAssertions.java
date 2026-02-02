package com.kakao.actionbase.test;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

public class ObjectAssertions {

  private static final String IGNORE_MARKER = "__IGNORE__";
  private static final String ARRAY_SUFFIX = "[]";

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
  private static final YAMLMapper YAML_MAPPER = createYamlMapper();

  private ObjectAssertions() {}

  private static YAMLMapper createYamlMapper() {
    YAMLFactory factory = new YAMLFactory();
    factory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
    factory.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
    factory.enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR);

    YAMLMapper mapper = new YAMLMapper(factory);
    mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    return mapper;
  }

  public static void assertEquals(Object actualObj, Object expectedObj) throws Exception {
    Map<String, Object> actualMap = createMap(actualObj);
    assertEquals(actualMap, expectedObj, Collections.emptyList());
  }

  public static void assertEquals(Object actualObj, Object expectedObj, List<String> excludePaths)
      throws Exception {
    Map<String, Object> actualMap = createMap(actualObj);
    assertEquals(actualMap, expectedObj, excludePaths);
  }

  public static void assertEquals(String actualJson, Object expectedObj) throws Exception {
    Map<String, Object> actualMap = createMap(actualJson);
    assertEquals(actualMap, expectedObj, Collections.emptyList());
  }

  /** Compares JSON string with expected object. */
  public static void assertEquals(String actualJson, Object expectedObj, List<String> excludePaths)
      throws Exception {
    Map<String, Object> actualMap = createMap(actualJson);
    assertEquals(actualMap, expectedObj, excludePaths);
  }

  /** Compares JSON byte array with expected object. */
  public static void assertEquals(byte[] actualJson, Object expectedObj, List<String> excludePaths)
      throws Exception {
    Map<String, Object> actualMap = createMap(actualJson);
    assertEquals(actualMap, expectedObj, excludePaths);
  }

  /** Compares Map with expected object. */
  public static void assertEquals(
      Map<String, Object> actualMap, Object expectedObj, List<String> excludePaths)
      throws Exception {
    Map<String, Object> expectedMap = createMap(expectedObj);
    assertEquals(actualMap, expectedMap, excludePaths);
  }

  /** Compares two Maps. Fields at specified paths are excluded. */
  public static void assertEquals(
      Map<String, Object> actualMap, Map<String, Object> expectedMap, List<String> excludePaths)
      throws Exception {
    Map<String, Object> filteredActual = excludeFields(actualMap, excludePaths);
    Map<String, Object> filteredExpected = excludeFields(expectedMap, excludePaths);

    String actualYaml = YAML_MAPPER.writeValueAsString(filteredActual);
    String expectedYaml = YAML_MAPPER.writeValueAsString(filteredExpected);

    Assertions.assertEquals(
        expectedYaml, actualYaml, "Filtered YAML representation does not match");
  }

  /**
   * Verifies that a value exists and is not empty at the specified path.
   *
   * @param input Map object to validate
   * @param path Path to validate (e.g., "users[].name", "data.items[].status")
   */
  public static void assertNotEmpty(Object input, String path) throws Exception {
    Map<String, Object> inputMap = createMap(input);
    assertNotEmpty(inputMap, path);
  }

  /**
   * Verifies that a value exists and is not empty at the specified path.
   *
   * @param inputMap Map object to validate
   * @param path Path to validate (e.g., "users[].name", "data.items[].status")
   */
  public static void assertNotEmpty(Map<String, Object> inputMap, String path) {
    List<Object> actualValues = extractAllValues(inputMap, path);

    if (actualValues.isEmpty()) {
      Assertions.fail("No values found at path: " + path);
    }

    List<String> mismatches = new ArrayList<>();
    for (int i = 0; i < actualValues.size(); i++) {
      Object actualValue = actualValues.get(i);
      if (isEmpty(actualValue)) {
        mismatches.add(String.format("Index %d: value is empty or null: <%s>", i, actualValue));
      }
    }

    if (!mismatches.isEmpty()) {
      String errorMessage =
          String.format(
              "Some values at path '%s' are empty or null:\n%s",
              path, String.join("\n", mismatches));
      Assertions.fail(errorMessage);
    }
  }

  /**
   * Checks if a value is empty.
   *
   * @param value Value to check
   * @return true if value is null or empty
   */
  private static boolean isEmpty(Object value) {
    if (value == null) {
      return true;
    }
    if (value instanceof String) {
      return ((String) value).trim().isEmpty();
    }
    if (value instanceof Collection) {
      return ((Collection<?>) value).isEmpty();
    }
    if (value instanceof Map) {
      return ((Map<?, ?>) value).isEmpty();
    }
    if (value.getClass().isArray()) {
      return Array.getLength(value) == 0;
    }
    return false;
  }

  /**
   * Extracts all values from the specified path.
   *
   * @param map Target Map
   * @param path Path (e.g., "users[].name", "data.items[].status")
   * @return All values at the specified path
   */
  private static List<Object> extractAllValues(Map<String, Object> map, String path) {
    List<Object> result = new ArrayList<>();
    String[] pathParts = path.split("\\.");

    extractValuesRecursive(Collections.singletonList(map), pathParts, 0, result);
    return result;
  }

  /**
   * Recursively extracts values.
   *
   * @param currentObjects Objects currently being processed
   * @param pathParts Path parts
   * @param partIndex Index of the path part currently being processed
   * @param result List to store results
   */
  @SuppressWarnings("unchecked")
  private static void extractValuesRecursive(
      List<Object> currentObjects, String[] pathParts, int partIndex, List<Object> result) {

    if (partIndex >= pathParts.length) {
      result.addAll(currentObjects);
      return;
    }

    String currentPart = pathParts[partIndex];
    List<Object> nextObjects = new ArrayList<>();

    for (Object obj : currentObjects) {
      if (!(obj instanceof Map)) {
        continue;
      }

      Map<String, Object> currentMap = (Map<String, Object>) obj;

      if (isArrayKey(currentPart)) {
        // Handle array key (e.g., "users[]")
        String actualKey = currentPart.substring(0, currentPart.length() - ARRAY_SUFFIX.length());
        Object arrayValue = currentMap.get(actualKey);

        if (arrayValue instanceof List) {
          nextObjects.addAll((List<Object>) arrayValue);
        } else if (arrayValue instanceof Object[]) {
          nextObjects.addAll(Arrays.asList((Object[]) arrayValue));
        }
      } else {
        // Handle regular key
        Object value = currentMap.get(currentPart);
        if (value != null) {
          nextObjects.add(value);
        }
      }
    }

    extractValuesRecursive(nextObjects, pathParts, partIndex + 1, result);
  }

  /** Returns a new Map excluding fields at the specified paths. */
  private static Map<String, Object> excludeFields(
      Map<String, Object> sourceMap, List<String> excludePaths) {
    Map<String, Object> result = new HashMap<>(sourceMap);
    if (excludePaths == null || excludePaths.isEmpty()) {
      return result;
    }

    for (String path : excludePaths) {
      excludeFieldByPath(result, path);
    }

    return result;
  }

  /** Excludes a field corresponding to a single path. */
  private static void excludeFieldByPath(Map<String, Object> map, String path) {
    String[] pathParts = path.split("\\.");

    if (pathParts.length == 1) {
      map.put(pathParts[0], IGNORE_MARKER);
    } else {
      excludeNestedField(map, pathParts);
    }
  }

  /** Excludes nested fields. */
  private static void excludeNestedField(Map<String, Object> rootMap, String[] pathParts) {
    Map<String, Object> currentMap = rootMap;

    for (int i = 0; i < pathParts.length - 1; i++) {
      String key = pathParts[i];

      if (isArrayKey(key)) {
        handleArrayPath(currentMap, key, pathParts, i);
        return;
      } else {
        currentMap = navigateToNextLevel(currentMap, key);
      }
    }

    // Exclude field at the last level
    String finalKey = pathParts[pathParts.length - 1];
    currentMap.put(finalKey, IGNORE_MARKER);
  }

  /** Checks if the key is an array key. */
  private static boolean isArrayKey(String key) {
    return key.endsWith(ARRAY_SUFFIX);
  }

  /** Processes array path. */
  @SuppressWarnings("unchecked")
  private static void handleArrayPath(
      Map<String, Object> currentMap, String arrayKey, String[] pathParts, int currentIndex) {
    String actualKey = arrayKey.substring(0, arrayKey.length() - ARRAY_SUFFIX.length());
    Object arrayValue = currentMap.get(actualKey);

    validateArrayValue(arrayValue, arrayKey);

    String[] remainingPath = Arrays.copyOfRange(pathParts, currentIndex + 1, pathParts.length);

    if (arrayValue instanceof List) {
      processListItems((List<Object>) arrayValue, remainingPath);
    } else {
      processArrayItems((Object[]) arrayValue, remainingPath);
    }
  }

  /** Validates array value. */
  private static void validateArrayValue(Object value, String key) {
    if (!(value instanceof List) && !(value instanceof Object[])) {
      throw new IllegalArgumentException("Path does not point to a list or array: " + key);
    }
  }

  /** Processes each item in the List. */
  @SuppressWarnings("unchecked")
  private static void processListItems(List<Object> list, String[] remainingPath) {
    for (Object item : list) {
      if (item instanceof Map) {
        excludeNestedField((Map<String, Object>) item, remainingPath);
      }
    }
  }

  /** Processes each item in the array. */
  @SuppressWarnings("unchecked")
  private static void processArrayItems(Object[] array, String[] remainingPath) {
    for (Object item : array) {
      if (item instanceof Map) {
        excludeNestedField((Map<String, Object>) item, remainingPath);
      }
    }
  }

  /** Moves to the next level. */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> navigateToNextLevel(
      Map<String, Object> currentMap, String key) {
    Object value = currentMap.get(key);

    if (!(value instanceof Map)) {
      throw new IllegalArgumentException("Path does not point to a map: " + key);
    }

    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> createMap(byte[] input) throws IOException {
    return JSON_MAPPER.readValue(input, Map.class);
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> createMap(String input) throws IOException {
    return JSON_MAPPER.readValue(input, Map.class);
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> createMap(Object input) throws IOException {
    if (input instanceof String) {
      return createMap(input.toString());
    }

    return JSON_MAPPER.convertValue(input, Map.class);
  }
}
