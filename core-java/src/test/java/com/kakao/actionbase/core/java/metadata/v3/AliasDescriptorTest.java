package com.kakao.actionbase.core.java.metadata.v3;

import static org.junit.jupiter.api.Assertions.*;

import com.kakao.actionbase.core.java.constant.Constant;
import com.kakao.actionbase.test.AbstractUnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AliasDescriptorTest extends AbstractUnitTest<AliasDescriptor> {

  private static final boolean TEST_ACTIVE = true;
  private static final String TEST_TENANT = "test-tenant";
  private static final String TEST_DATABASE = "test-database";
  private static final String TEST_ALIAS = "test-alias";
  private static final String TEST_TABLE = "test-table";
  private static final String TEST_COMMENT = "Test comment";

  AliasDescriptor createTarget() {
    return ImmutableAliasDescriptor.builder()
        .active(TEST_ACTIVE)
        .tenant(TEST_TENANT)
        .database(TEST_DATABASE)
        .alias(TEST_ALIAS)
        .table(TEST_TABLE)
        .comment(TEST_COMMENT)
        .build();
  }

  @BeforeEach
  protected void setUp() {
    testTarget = createTarget();
  }

  @Nested
  @DisplayName("Object Equality Tests")
  class EqualityTests {
    @Test
    @DisplayName("Objects with identical data fields should be equal")
    void testDataEquality() {
      AliasDescriptor sameData = createTarget();

      assertEquals(testTarget, sameData);
      assertEquals(testTarget.hashCode(), sameData.hashCode());
    }

    @ParameterizedTest(name = "Different field case: {0}")
    @ValueSource(strings = {"active", "tenant", "database", "alias", "table", "comment"})
    @DisplayName("Objects with different fields should not be equal")
    void testDataInequality(String field) {
      AliasDescriptor differentData;

      switch (field) {
        case "active":
          differentData =
              ImmutableAliasDescriptor.builder().from(testTarget).active(!TEST_ACTIVE).build();
          break;
        case "tenant":
          differentData =
              ImmutableAliasDescriptor.builder()
                  .from(testTarget)
                  .tenant(TEST_TENANT + "-different")
                  .build();
          break;
        case "database":
          differentData =
              ImmutableAliasDescriptor.builder()
                  .from(testTarget)
                  .database(TEST_DATABASE + "-different")
                  .build();
          break;
        case "alias":
          differentData =
              ImmutableAliasDescriptor.builder()
                  .from(testTarget)
                  .alias(TEST_ALIAS + "-different")
                  .build();
          break;
        case "table":
          differentData =
              ImmutableAliasDescriptor.builder()
                  .from(testTarget)
                  .table(TEST_TABLE + "-different")
                  .build();
          break;
        case "comment":
          differentData =
              ImmutableAliasDescriptor.builder()
                  .from(testTarget)
                  .comment(TEST_COMMENT + "-different")
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
    @ValueSource(strings = {"tenant", "database", "alias", "table"})
    @DisplayName("Exception should be thrown when required field is missing")
    void testMissingRequiredField(String excludedField) {
      ImmutableAliasDescriptor.Builder builder =
          ImmutableAliasDescriptor.builder().active(TEST_ACTIVE).comment(TEST_COMMENT);

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
      AliasDescriptor defaultAlias =
          ImmutableAliasDescriptor.builder()
              .tenant(TEST_TENANT)
              .database(TEST_DATABASE)
              .alias(TEST_ALIAS)
              .table(TEST_TABLE)
              .build();

      assertTrue(defaultAlias.active());
      assertEquals(Constant.DEFAULT_COMMENT, defaultAlias.comment());
    }
  }

  private void configureBuilderExcept(
      ImmutableAliasDescriptor.Builder builder, String excludedField) {
    if (!"tenant".equals(excludedField)) {
      builder.tenant(TEST_TENANT);
    }
    if (!"database".equals(excludedField)) {
      builder.database(TEST_DATABASE);
    }
    if (!"alias".equals(excludedField)) {
      builder.alias(TEST_ALIAS);
    }
    if (!"table".equals(excludedField)) {
      builder.table(TEST_TABLE);
    }
  }
}
