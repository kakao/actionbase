package com.kakao.actionbase.core.java.metadata.v3.common;

import static org.junit.jupiter.api.Assertions.*;

import com.kakao.actionbase.core.java.codec.common.hbase.Order;
import com.kakao.actionbase.core.java.types.DataType;
import com.kakao.actionbase.core.java.types.common.Field;
import com.kakao.actionbase.core.java.types.common.ImmutableField;
import com.kakao.actionbase.core.java.types.common.ImmutableStructField;
import com.kakao.actionbase.core.java.types.common.StructField;
import com.kakao.actionbase.test.AbstractUnitTest;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EdgeSchemaTest extends AbstractUnitTest<EdgeSchema> {

  private static final Field TEST_SOURCE_FIELD = ImmutableField.of(DataType.LONG, "");
  private static final Field TEST_SOURCE_FIELD_NEW = ImmutableField.of(DataType.STRING, "");
  private static final Field TEST_TARGET_FIELD = ImmutableField.of(DataType.STRING, "");
  private static final Field TEST_TARGET_FIELD_NEW = ImmutableField.of(DataType.LONG, "");
  private static final String TEST_INDEX = "created_at_desc";
  private static final String TEST_PROPERTY_KEY = "created_at";
  private static final DirectionType TEST_DIRECTION_TYPE = DirectionType.BOTH;
  private List<StructField> baseProperties;
  private List<Index> baseIndexes;

  EdgeSchema createTarget() {
    return ImmutableEdgeSchema.builder()
        .source(TEST_SOURCE_FIELD)
        .target(TEST_TARGET_FIELD)
        .properties(baseProperties)
        .direction(TEST_DIRECTION_TYPE)
        .indexes(baseIndexes)
        .build();
  }

  @BeforeEach
  protected void setUp() {
    baseProperties =
        Collections.singletonList(ImmutableStructField.of(TEST_PROPERTY_KEY, DataType.LONG));
    Index baseIndex =
        ImmutableIndex.of(
            TEST_INDEX,
            Collections.singletonList(ImmutableIndexField.of(TEST_PROPERTY_KEY, Order.DESC)));
    baseIndexes = Collections.singletonList(baseIndex);

    testTarget = createTarget();
  }

  @Nested
  @DisplayName("Object Equality Tests")
  class EqualityTests {
    @Test
    @DisplayName("Objects with identical data fields should be equal")
    void testDataEquality() {
      EdgeSchema duplicate = createTarget();
      assertEquals(testTarget, duplicate);
      assertEquals(testTarget.hashCode(), duplicate.hashCode());
    }

    @ParameterizedTest(name = "Different field case: {0}")
    @ValueSource(strings = {"source", "target", "properties", "direction", "indexes"})
    @DisplayName("Objects with different fields should not be equal")
    void testDataInequality(String field) {
      ImmutableEdgeSchema.Builder builder = ImmutableEdgeSchema.builder().from(testTarget);

      switch (field) {
        case "source":
          builder.source(TEST_SOURCE_FIELD_NEW).build();
          break;
        case "target":
          builder.target(TEST_TARGET_FIELD_NEW).build();
          break;
        case "properties":
          builder.addProperties(TEST_PROPERTY_KEY + "-different", DataType.STRING).build();
          break;
        case "direction":
          builder
              .direction(
                  TEST_DIRECTION_TYPE == DirectionType.BOTH
                      ? DirectionType.OUT
                      : DirectionType.BOTH)
              .build();
          break;
        case "indexes":
          builder
              .addIndex(
                  TEST_INDEX + "-different", ImmutableIndexField.of(TEST_PROPERTY_KEY, Order.ASC))
              .build();
          break;
      }

      EdgeSchema different = builder.build();
      assertNotEquals(testTarget, different);
    }
  }

  @Nested
  @DisplayName("Domain Rules Tests")
  class DomainRulesTests {

    @ParameterizedTest(name = "Missing required field: {0}")
    @ValueSource(strings = {"source", "target", "direction"})
    @DisplayName("Exception should be thrown when required field is missing")
    void testMissingRequiredField(String excludedField) {
      ImmutableEdgeSchema.Builder builder = ImmutableEdgeSchema.builder();
      configureBuilderExcept(builder, excludedField);
      assertThrows(IllegalStateException.class, builder::build);
    }
  }

  private void configureBuilderExcept(ImmutableEdgeSchema.Builder builder, String excludedField) {
    if (!excludedField.equals("source")) {
      builder.source(TEST_SOURCE_FIELD);
    }
    if (!excludedField.equals("target")) {
      builder.target(TEST_TARGET_FIELD);
    }
    if (!excludedField.equals("direction")) {
      builder.direction(TEST_DIRECTION_TYPE);
    }
  }
}
