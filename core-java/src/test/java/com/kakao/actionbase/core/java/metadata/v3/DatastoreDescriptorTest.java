package com.kakao.actionbase.core.java.metadata.v3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kakao.actionbase.core.java.constant.Constant;
import com.kakao.actionbase.core.java.metadata.v3.common.DatastoreType;
import com.kakao.actionbase.test.AbstractUnitTest;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DatastoreDescriptorTest extends AbstractUnitTest<DatastoreDescriptor> {

  private static final long TEST_VERSION = 1L;
  private static final boolean TEST_ACTIVE = true;
  private static final String TEST_TENANT = "test-tenant";
  private static final String TEST_DATASTORE = "test-datastore";
  private static final DatastoreType TEST_TYPE = DatastoreType.MEMORY;
  private static final String TEST_COMMENT = "Test comment";

  private Map<String, String> baseConfiguration;

  DatastoreDescriptor createTarget() {
    return ImmutableDatastoreDescriptor.builder()
        .active(TEST_ACTIVE)
        .tenant(TEST_TENANT)
        .type(TEST_TYPE)
        .datastore(TEST_DATASTORE)
        .comment(TEST_COMMENT)
        .configuration(baseConfiguration)
        .build();
  }

  @BeforeEach
  protected void setUp() {
    baseConfiguration = new HashMap<>();
    baseConfiguration.put("key1", "value1");
    testTarget = createTarget();
  }

  @Nested
  @DisplayName("Object Equality Tests")
  class EqualityTests {
    @Test
    @DisplayName("Objects with identical data fields should be equal")
    void testDataEquality() {
      DatastoreDescriptor sameData = createTarget();

      assertEquals(testTarget, sameData);
      assertEquals(testTarget.hashCode(), sameData.hashCode());
    }

    @ParameterizedTest(name = "Different field case: {0}")
    @ValueSource(strings = {"active", "type", "tenant", "datastore", "comment", "configuration"})
    @DisplayName("Objects with different fields should not be equal")
    void testDataInequality(String field) {
      DatastoreDescriptor differentData;

      switch (field) {
        case "active":
          differentData =
              ImmutableDatastoreDescriptor.builder().from(testTarget).active(!TEST_ACTIVE).build();
          break;
        case "tenant":
          differentData =
              ImmutableDatastoreDescriptor.builder()
                  .from(testTarget)
                  .tenant(TEST_TENANT + "-different")
                  .build();
          break;
        case "type":
          differentData =
              ImmutableDatastoreDescriptor.builder()
                  .from(testTarget)
                  .type(
                      TEST_TYPE == DatastoreType.MEMORY
                          ? DatastoreType.HBASE
                          : DatastoreType.MEMORY)
                  .build();
          break;
        case "datastore":
          differentData =
              ImmutableDatastoreDescriptor.builder()
                  .from(testTarget)
                  .datastore(TEST_DATASTORE + "-different")
                  .build();
          break;
        case "comment":
          differentData =
              ImmutableDatastoreDescriptor.builder()
                  .from(testTarget)
                  .comment(TEST_COMMENT + "-different")
                  .build();
          break;
        case "configuration":
          Map<String, String> differentConfig = new HashMap<>(baseConfiguration);
          differentConfig.put("key1", "differentValue");
          differentData =
              ImmutableDatastoreDescriptor.builder()
                  .from(testTarget)
                  .configuration(differentConfig)
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
    @ValueSource(strings = {"tenant", "type", "datastore"})
    @DisplayName("Exception should be thrown when required field is missing")
    void testMissingRequiredField(String excludedField) {
      ImmutableDatastoreDescriptor.Builder builder =
          ImmutableDatastoreDescriptor.builder().active(TEST_ACTIVE).comment(TEST_COMMENT);

      assertThrows(
          IllegalStateException.class,
          () -> {
            configureBuilderExcept(builder, excludedField);
            builder.build();
          });
    }

    @Test
    @DisplayName(
        "Default state should be active=true, comment=V3Metadata.DEFAULT_COMMENT, configuration={}")
    void testDefaultState() {
      DatastoreDescriptor defaultStorage =
          ImmutableDatastoreDescriptor.builder()
              .tenant(TEST_TENANT)
              .datastore(TEST_DATASTORE)
              .type(TEST_TYPE)
              .build();

      assertTrue(defaultStorage.active());
      assertEquals(Constant.DEFAULT_COMMENT, defaultStorage.comment());
      assertTrue(defaultStorage.configuration().isEmpty());
    }

    private void configureBuilderExcept(
        ImmutableDatastoreDescriptor.Builder builder, String excludedField) {
      if (!"tenant".equals(excludedField)) {
        builder.tenant(TEST_TENANT);
      }
      if (!"datastore".equals(excludedField)) {
        builder.datastore(TEST_DATASTORE);
      }
      if (!"type".equals(excludedField)) {
        builder.type(TEST_TYPE);
      }
    }
  }
}
