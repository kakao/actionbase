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

class DatabaseDescriptorDescriptorTest extends AbstractUnitTest<DatabaseDescriptor> {

  private static final boolean TEST_ACTIVE = true;
  private static final String TEST_TENANT = "test-tenant";
  private static final String TEST_DATABASE = "test-database";
  private static final String TEST_COMMENT = "Test comment";

  DatabaseDescriptor createTarget() {
    return ImmutableDatabaseDescriptor.builder()
        .active(TEST_ACTIVE)
        .tenant(TEST_TENANT)
        .database(TEST_DATABASE)
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
      DatabaseDescriptor sameData = createTarget();

      assertEquals(testTarget, sameData);
      assertEquals(testTarget.hashCode(), sameData.hashCode());
    }

    @ParameterizedTest(name = "Different field case: {0}")
    @ValueSource(strings = {"active", "tenant", "database", "comment"})
    @DisplayName("Objects with different fields should not be equal")
    void testDataInequality(String field) {
      DatabaseDescriptor differentData;

      switch (field) {
        case "active":
          differentData =
              ImmutableDatabaseDescriptor.builder().from(testTarget).active(!TEST_ACTIVE).build();
          break;
        case "tenant":
          differentData =
              ImmutableDatabaseDescriptor.builder()
                  .from(testTarget)
                  .tenant(TEST_TENANT + "-different")
                  .build();
          break;
        case "database":
          differentData =
              ImmutableDatabaseDescriptor.builder()
                  .from(testTarget)
                  .database(TEST_DATABASE + "-different")
                  .build();
          break;
        case "comment":
          differentData =
              ImmutableDatabaseDescriptor.builder()
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
    @ValueSource(strings = {"tenant", "database"})
    @DisplayName("Exception should be thrown when required field is missing")
    void testMissingRequiredField(String excludedField) {
      ImmutableDatabaseDescriptor.Builder builder =
          ImmutableDatabaseDescriptor.builder().active(TEST_ACTIVE).comment(TEST_COMMENT);

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
      DatabaseDescriptor defaultDatabase =
          ImmutableDatabaseDescriptor.builder().tenant(TEST_TENANT).database(TEST_DATABASE).build();

      assertTrue(defaultDatabase.active());
      assertEquals(Constant.DEFAULT_COMMENT, defaultDatabase.comment());
    }

    private void configureBuilderExcept(
        ImmutableDatabaseDescriptor.Builder builder, String excludedField) {
      if (!"tenant".equals(excludedField)) {
        builder.tenant(TEST_TENANT);
      }
      if (!"database".equals(excludedField)) {
        builder.database(TEST_DATABASE);
      }
    }
  }
}
