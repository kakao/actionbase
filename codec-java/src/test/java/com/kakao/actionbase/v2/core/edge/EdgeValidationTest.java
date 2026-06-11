package com.kakao.actionbase.v2.core.edge;

import static org.junit.jupiter.api.Assertions.*;

import com.kakao.actionbase.v2.core.types.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class EdgeValidationTest {

  private static final EdgeSchema SCHEMA =
      new EdgeSchema(
          new VertexField(VertexType.LONG),
          new VertexField(VertexType.STRING),
          Arrays.asList(
              new Field("score", DataType.LONG, false), // required
              new Field("rank", DataType.INT, true))); // nullable

  @Test
  void shouldPassWhenAllRequiredFieldsPresent() {
    Map<String, Object> props = new HashMap<>();
    props.put("score", 42L);
    Edge edge = new Edge(1L, 1L, "tgt", props);
    assertDoesNotThrow(() -> edge.validateAgainstSchema(SCHEMA));
  }

  @Test
  void shouldPassWhenNullableFieldIsMissing() {
    Map<String, Object> props = new HashMap<>();
    props.put("score", 42L);
    // "rank" not present — nullable, so OK
    Edge edge = new Edge(1L, 1L, "tgt", props);
    assertDoesNotThrow(() -> edge.validateAgainstSchema(SCHEMA));
  }

  @Test
  void shouldPassWhenNullableFieldIsNull() {
    Map<String, Object> props = new HashMap<>();
    props.put("score", 42L);
    props.put("rank", null);
    Edge edge = new Edge(1L, 1L, "tgt", props);
    assertDoesNotThrow(() -> edge.validateAgainstSchema(SCHEMA));
  }

  @Test
  void shouldRejectWhenRequiredFieldIsMissing() {
    Map<String, Object> props = new HashMap<>();
    props.put("rank", 1);
    Edge edge = new Edge(1L, 1L, "tgt", props);
    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> edge.validateAgainstSchema(SCHEMA));
    assertTrue(ex.getMessage().contains("score"), ex.getMessage());
    assertTrue(ex.getMessage().contains("missing"), ex.getMessage());
  }

  @Test
  void shouldRejectWhenRequiredFieldIsNull() {
    Map<String, Object> props = new HashMap<>();
    props.put("score", null);
    Edge edge = new Edge(1L, 1L, "tgt", props);
    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> edge.validateAgainstSchema(SCHEMA));
    assertTrue(ex.getMessage().contains("score"), ex.getMessage());
    assertTrue(ex.getMessage().contains("null"), ex.getMessage());
  }

  @Test
  void shouldRejectWhenRequiredFieldHasTypeMismatch() {
    Map<String, Object> props = new HashMap<>();
    props.put("score", "not-a-long");
    Edge edge = new Edge(1L, 1L, "tgt", props);
    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> edge.validateAgainstSchema(SCHEMA));
    assertTrue(ex.getMessage().contains("score"), ex.getMessage());
    assertTrue(ex.getMessage().contains("type mismatch"), ex.getMessage());
  }

  @Test
  void shouldRejectWhenNullableFieldHasTypeMismatch() {
    // "rank" is nullable but has a non-castable value — silent null coercion must be rejected
    Map<String, Object> props = new HashMap<>();
    props.put("score", 42L);
    props.put("rank", "not-an-int");
    Edge edge = new Edge(1L, 1L, "tgt", props);
    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> edge.validateAgainstSchema(SCHEMA));
    assertTrue(ex.getMessage().contains("rank"), ex.getMessage());
    assertTrue(ex.getMessage().contains("type mismatch"), ex.getMessage());
  }
}
