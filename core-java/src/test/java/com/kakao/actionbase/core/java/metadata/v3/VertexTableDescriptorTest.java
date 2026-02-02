package com.kakao.actionbase.core.java.metadata.v3;

import static org.junit.jupiter.api.Assertions.*;

import com.kakao.actionbase.core.java.constant.Constant;
import com.kakao.actionbase.core.java.metadata.v3.common.*;
import com.kakao.actionbase.core.java.types.DataType;
import com.kakao.actionbase.core.java.types.common.ImmutableField;
import com.kakao.actionbase.test.AbstractUnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VertexTableDescriptorTest extends AbstractUnitTest<VertexTableDescriptor> {

  private static final boolean TEST_ACTIVE = true;
  private static final String TEST_TENANT = "test-tenant";
  private static final String TEST_DATABASE = "test-database";
  private static final String TEST_TABLE = "test-table";
  private static final String TEST_STORAGE = "test-storage";
  private static final String TEST_COMMENT = "Test comment";
  private static final String TEST_FIELD = "created_at";
  private static final MutationMode TEST_MODE = MutationMode.SYNC;

  VertexSchema baseEdgeSchema;

  VertexTableDescriptor createTarget() {
    return ImmutableVertexTableDescriptor.builder()
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
        ImmutableVertexSchema.builder()
            .key(ImmutableField.builder().type(DataType.LONG).build())
            .addProperties(TEST_FIELD, DataType.LONG)
            .build();
    testTarget = createTarget();
  }

  @Nested
  @DisplayName("Object Equality Tests")
  class EqualityTests {
    @Test
    @DisplayName("Objects with identical data fields should be equal")
    void testDataEquality() {
      VertexTableDescriptor sameData = createTarget();

      assertEquals(testTarget, sameData);
      assertEquals(testTarget.hashCode(), sameData.hashCode());
    }

    @ParameterizedTest(name = "Different field case: {0}")
    @ValueSource(
        strings = {"active", "tenant", "database", "table", "storage", "comment", "schema", "mode"})
    @DisplayName("Objects with different fields should not be equal")
    void testDataInequality(String field) {
      VertexTableDescriptor differentData;

      switch (field) {
        case "active":
          differentData =
              ImmutableVertexTableDescriptor.builder()
                  .from(testTarget)
                  .active(!TEST_ACTIVE)
                  .build();
          break;
        case "tenant":
          differentData =
              ImmutableVertexTableDescriptor.builder()
                  .from(testTarget)
                  .tenant(TEST_TENANT + "-different")
                  .build();
          break;
        case "database":
          differentData =
              ImmutableVertexTableDescriptor.builder()
                  .from(testTarget)
                  .database(TEST_DATABASE + "-different")
                  .build();
          break;
        case "table":
          differentData =
              ImmutableVertexTableDescriptor.builder()
                  .from(testTarget)
                  .table(TEST_TABLE + "-different")
                  .build();
          break;
        case "storage":
          differentData =
              ImmutableVertexTableDescriptor.builder()
                  .from(testTarget)
                  .storage(TEST_STORAGE + "-different")
                  .build();
          break;
        case "comment":
          differentData =
              ImmutableVertexTableDescriptor.builder()
                  .from(testTarget)
                  .comment(TEST_COMMENT + "-different")
                  .build();
          break;
        case "schema":
          VertexSchema differentSchema =
              ImmutableVertexSchema.builder()
                  .from(testTarget.schema())
                  .addProperties(TEST_FIELD + "-different", DataType.LONG)
                  .build();
          differentData =
              ImmutableVertexTableDescriptor.builder()
                  .from(testTarget)
                  .schema(differentSchema)
                  .build();
          break;
        case "mode":
          differentData =
              ImmutableVertexTableDescriptor.builder()
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
      ImmutableVertexTableDescriptor.Builder builder =
          ImmutableVertexTableDescriptor.builder().active(TEST_ACTIVE).comment(TEST_COMMENT);

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
      VertexTableDescriptor defaultAlias =
          ImmutableVertexTableDescriptor.builder()
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
        ImmutableVertexTableDescriptor.Builder builder, String excludedField) {
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
