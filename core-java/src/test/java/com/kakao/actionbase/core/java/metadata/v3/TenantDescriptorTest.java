package com.kakao.actionbase.core.java.metadata.v3;

import static org.junit.jupiter.api.Assertions.*;

import com.kakao.actionbase.core.java.ApiVersion;
import com.kakao.actionbase.core.java.constant.Constant;
import com.kakao.actionbase.test.AbstractUnitTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TenantDescriptorTest extends AbstractUnitTest<TenantDescriptor> {

  private static final boolean TEST_ACTIVE = true;
  private static final String TEST_TENANT = "test-tenant";
  private static final ApiVersion TEST_API_VERSION = ApiVersion.V3;
  private static final String TEST_COMMENT = "Test comment";

  TenantDescriptor createTarget() {
    return ImmutableTenantDescriptor.builder()
        .active(TEST_ACTIVE)
        .tenant(TEST_TENANT)
        .apiVersion(TEST_API_VERSION)
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
      TenantDescriptor sameData = createTarget();

      assertEquals(testTarget, sameData);
      assertEquals(testTarget.hashCode(), sameData.hashCode());
    }

    @ParameterizedTest(name = "Different field case: {0}")
    @ValueSource(
        strings = {
          "active",
          "tenant",
          "apiVersion",
          "comment",
        })
    @DisplayName("Objects with different fields should not be equal")
    void testDataInequality(String field) {
      TenantDescriptor differentData;

      switch (field) {
        case "active":
          differentData =
              ImmutableTenantDescriptor.builder().from(testTarget).active(!TEST_ACTIVE).build();
          break;
        case "tenant":
          differentData =
              ImmutableTenantDescriptor.builder()
                  .from(testTarget)
                  .tenant(TEST_TENANT + "-different")
                  .build();
          break;
        case "apiVersion":
          differentData =
              ImmutableTenantDescriptor.builder()
                  .from(testTarget)
                  .apiVersion(TEST_API_VERSION == ApiVersion.V3 ? ApiVersion.V2 : ApiVersion.V3)
                  .build();
          break;
        case "comment":
          differentData =
              ImmutableTenantDescriptor.builder()
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
    @ValueSource(strings = {"tenant", "apiVersion"})
    @DisplayName("Exception should be thrown when required field is missing")
    void testMissingRequiredField(String excludedField) {
      ImmutableTenantDescriptor.Builder builder =
          ImmutableTenantDescriptor.builder().active(TEST_ACTIVE).comment(TEST_COMMENT);

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
      TenantDescriptor defaultTenant =
          ImmutableTenantDescriptor.builder()
              .tenant(TEST_TENANT)
              .apiVersion(TEST_API_VERSION)
              .build();

      assertTrue(defaultTenant.active());
      assertEquals(TenantDescriptor.DEFAULT_SOURCE, defaultTenant.origin());
      assertEquals(Constant.DEFAULT_COMMENT, defaultTenant.comment());
    }

    private void configureBuilderExcept(
        ImmutableTenantDescriptor.Builder builder, String excludedField) {
      if (!"tenant".equals(excludedField)) {
        builder.tenant(TEST_TENANT);
      }
      if (!"apiVersion".equals(excludedField)) {
        builder.apiVersion(TEST_API_VERSION);
      }
    }
  }
}
