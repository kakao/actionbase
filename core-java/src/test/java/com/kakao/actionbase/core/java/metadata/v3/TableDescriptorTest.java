package com.kakao.actionbase.core.java.metadata.v3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kakao.actionbase.core.java.codec.common.hbase.Order;
import com.kakao.actionbase.core.java.constant.Constant;
import com.kakao.actionbase.core.java.metadata.v3.common.DirectionType;
import com.kakao.actionbase.core.java.metadata.v3.common.EdgeSchema;
import com.kakao.actionbase.core.java.metadata.v3.common.ImmutableEdgeSchema;
import com.kakao.actionbase.core.java.metadata.v3.common.ImmutableIndexField;
import com.kakao.actionbase.core.java.metadata.v3.common.MutationMode;
import com.kakao.actionbase.core.java.types.DataType;
import com.kakao.actionbase.core.java.types.common.ImmutableField;
import com.kakao.actionbase.test.AbstractUnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TableDescriptorTest extends AbstractUnitTest<EdgeTableDescriptor> {

  private static final boolean TEST_ACTIVE = true;
  private static final String TEST_TENANT = "test-tenant";
  private static final String TEST_DATABASE = "test-database";
  private static final String TEST_TABLE = "test-table";
  private static final String TEST_STORAGE = "test-storage";
  private static final String TEST_COMMENT = "Test comment";
  private static final String TEST_INDEX = "created_at_desc";
  private static final String TEST_FIELD = "created_at";
  private static final MutationMode TEST_MODE = MutationMode.SYNC;
  private static final DirectionType TEST_DIRECTION_TYPE = DirectionType.BOTH;

  EdgeSchema baseEdgeSchema;

  EdgeTableDescriptor createTarget() {
    return ImmutableEdgeTableDescriptor.builder()
        .active(TEST_ACTIVE)
        .tenant(TEST_TENANT)
        .database(TEST_DATABASE)
        .table(TEST_TABLE)
        .storage(TEST_STORAGE)
        .comment(TEST_COMMENT)
        .schema(baseEdgeSchema)
        .mode(TEST_MODE)
        .build();
  }

  @BeforeEach
  void setUp() {
    baseEdgeSchema =
        ImmutableEdgeSchema.builder()
            .source(ImmutableField.builder().type(DataType.LONG).build())
            .target(ImmutableField.builder().type(DataType.STRING).build())
            .addProperties(TEST_FIELD, DataType.LONG)
            .direction(TEST_DIRECTION_TYPE)
            .addIndex(TEST_INDEX, ImmutableIndexField.of(TEST_FIELD, Order.DESC))
            .build();
    testTarget = createTarget();
  }

  @Nested
  @DisplayName("Object Equality Tests")
  class EqualityTests {
    @Test
    @DisplayName("Objects with identical data fields should be equal")
    void testDataEquality() {
      TableDescriptor<?> sameData = createTarget();

      assertEquals(testTarget, sameData);
      assertEquals(testTarget.hashCode(), sameData.hashCode());
    }

    @ParameterizedTest(name = "Different field case: {0}")
    @ValueSource(
        strings = {"active", "tenant", "database", "table", "storage", "comment", "schema", "mode"})
    @DisplayName("Objects with different fields should not be equal")
    void testDataInequality(String field) {
      TableDescriptor<?> differentData;

      switch (field) {
        case "active":
          differentData =
              ImmutableEdgeTableDescriptor.builder().from(testTarget).active(!TEST_ACTIVE).build();
          break;
        case "tenant":
          differentData =
              ImmutableEdgeTableDescriptor.builder()
                  .from(testTarget)
                  .tenant(TEST_TENANT + "-different")
                  .build();
          break;
        case "database":
          differentData =
              ImmutableEdgeTableDescriptor.builder()
                  .from(testTarget)
                  .database(TEST_DATABASE + "-different")
                  .build();
          break;
        case "table":
          differentData =
              ImmutableEdgeTableDescriptor.builder()
                  .from(testTarget)
                  .table(TEST_TABLE + "-different")
                  .build();
          break;
        case "storage":
          differentData =
              ImmutableEdgeTableDescriptor.builder()
                  .from(testTarget)
                  .storage(TEST_STORAGE + "-different")
                  .build();
          break;
        case "comment":
          differentData =
              ImmutableEdgeTableDescriptor.builder()
                  .from(testTarget)
                  .comment(TEST_COMMENT + "-different")
                  .build();
          break;
        case "schema":
          EdgeSchema differentSchema =
              ImmutableEdgeSchema.builder()
                  .from(testTarget.schema())
                  .direction(
                      TEST_DIRECTION_TYPE == DirectionType.BOTH
                          ? DirectionType.OUT
                          : DirectionType.BOTH)
                  .build();
          differentData =
              ImmutableEdgeTableDescriptor.builder()
                  .from(testTarget)
                  .schema(differentSchema)
                  .build();
          break;
        case "mode":
          differentData =
              ImmutableEdgeTableDescriptor.builder()
                  .from(testTarget)
                  .mode(TEST_MODE == MutationMode.SYNC ? MutationMode.ASYNC : MutationMode.SYNC)
                  .build();
          break;
        default:
          throw new IllegalArgumentException("Unexpected field: " + field);
      }

      assertNotEquals(testTarget, differentData);
      assertNotEquals(testTarget.hashCode(), differentData.hashCode());
    }
  }

  @Nested
  @DisplayName("Domain Rules Tests")
  class DomainRulesTests {

    @ParameterizedTest(name = "Missing required field: {0}")
    @ValueSource(strings = {"tenant", "database", "table", "storage", "schema", "mode"})
    @DisplayName("Exception should be thrown when required field is missing")
    void testMissingRequiredField(String excludedField) {
      ImmutableEdgeTableDescriptor.Builder builder =
          ImmutableEdgeTableDescriptor.builder().active(TEST_ACTIVE).comment(TEST_COMMENT);

      assertThrows(
          IllegalStateException.class,
          () -> {
            configureBuilderExcept(builder, excludedField);
            builder.build();
          });
    }

    @Test
    @DisplayName("Default state should be active=true, comment=V3Metadata.DEFAULT_COMMENT")
    void testDefaultState() {
      EdgeTableDescriptor defaultAlias =
          ImmutableEdgeTableDescriptor.builder()
              .tenant(TEST_TENANT)
              .database(TEST_DATABASE)
              .table(TEST_TABLE)
              .storage(TEST_STORAGE)
              .schema(baseEdgeSchema)
              .mode(TEST_MODE)
              .build();

      assertTrue(defaultAlias.active());
      assertEquals(Constant.DEFAULT_COMMENT, defaultAlias.comment());
    }

    private void configureBuilderExcept(
        ImmutableEdgeTableDescriptor.Builder builder, String excludedField) {
      if (!"tenant".equals(excludedField)) {
        builder.tenant(TEST_TENANT);
      }
      if (!"database".equals(excludedField)) {
        builder.database(TEST_DATABASE);
      }
      if (!"table".equals(excludedField)) {
        builder.table(TEST_TABLE);
      }
      if (!"storage".equals(excludedField)) {
        builder.storage(TEST_STORAGE);
      }
      if (!"schema".equals(excludedField)) {
        builder.schema(baseEdgeSchema);
      }
      if (!"mode".equals(excludedField)) {
        builder.mode(TEST_MODE);
      }
    }
  }
}
