package com.kakao.actionbase.core.java.metadata.v3;

import static org.junit.jupiter.api.Assertions.*;

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

class StorageDescriptorTest extends AbstractUnitTest<StorageDescriptor> {

  private static final boolean TEST_ACTIVE = true;
  private static final String TEST_TENANT = "test-tenant";
  private static final String TEST_DATABASE = "test-database";
  private static final String TEST_STORAGE = "test-storage";
  private static final String TEST_COMMENT = "Test comment";
  private static final DatastoreType TEST_TYPE = DatastoreType.MEMORY;
  private static final String TEST_DATASTORE = "test-datastore";

  private Map<String, Object> baseConfiguration;

  StorageDescriptor createTarget() {
    return ImmutableStorageDescriptor.builder()
        .active(TEST_ACTIVE)
        .type(TEST_TYPE)
        .tenant(TEST_TENANT)
        .database(TEST_DATABASE)
        .storage(TEST_STORAGE)
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
      StorageDescriptor sameData = createTarget();

      assertEquals(testTarget, sameData);
      assertEquals(testTarget.hashCode(), sameData.hashCode());
    }

    @ParameterizedTest(name = "Different field case: {0}")
    @ValueSource(
        strings = {
          "active",
          "tenant",
          "type",
          "database",
          "storage",
          "comment",
          "datastore",
          "configuration"
        })
    @DisplayName("Objects with different fields should not be equal")
    void testDataInequality(String field) {
      StorageDescriptor differentData;

      switch (field) {
        case "active":
          differentData =
              ImmutableStorageDescriptor.builder().from(testTarget).active(!TEST_ACTIVE).build();
          break;
        case "tenant":
          differentData =
              ImmutableStorageDescriptor.builder()
                  .from(testTarget)
                  .tenant(TEST_TENANT + "-different")
                  .build();
          break;
        case "type":
          differentData =
              ImmutableStorageDescriptor.builder()
                  .from(testTarget)
                  .type(
                      TEST_TYPE == DatastoreType.MEMORY
                          ? DatastoreType.HBASE
                          : DatastoreType.MEMORY)
                  .build();
          break;
        case "database":
          differentData =
              ImmutableStorageDescriptor.builder()
                  .from(testTarget)
                  .database(TEST_DATABASE + "-different")
                  .build();
          break;
        case "storage":
          differentData =
              ImmutableStorageDescriptor.builder()
                  .from(testTarget)
                  .storage(TEST_STORAGE + "-different")
                  .build();
          break;
        case "datastore":
          differentData =
              ImmutableStorageDescriptor.builder()
                  .from(testTarget)
                  .datastore(TEST_DATASTORE + "-different")
                  .build();
          break;
        case "comment":
          differentData =
              ImmutableStorageDescriptor.builder()
                  .from(testTarget)
                  .comment(TEST_COMMENT + "-different")
                  .build();
          break;
        case "configuration":
          Map<String, Object> differentConfig = new HashMap<>(baseConfiguration);
          differentConfig.put("key1", "differentValue");
          differentData =
              ImmutableStorageDescriptor.builder()
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
    @ValueSource(strings = {"type", "tenant", "database", "storage"})
    @DisplayName("Exception should be thrown when required field is missing")
    void testMissingRequiredField(String excludedField) {
      ImmutableStorageDescriptor.Builder builder =
          ImmutableStorageDescriptor.builder().active(TEST_ACTIVE).comment(TEST_COMMENT);

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
      StorageDescriptor defaultStorage =
          ImmutableStorageDescriptor.builder()
              .tenant(TEST_TENANT)
              .type(TEST_TYPE)
              .database(TEST_DATABASE)
              .storage(TEST_STORAGE)
              .datastore(TEST_DATASTORE)
              .build();

      assertTrue(defaultStorage.active());
      assertEquals(Constant.DEFAULT_COMMENT, defaultStorage.comment());
      assertTrue(defaultStorage.configuration().isEmpty());
    }

    private void configureBuilderExcept(
        ImmutableStorageDescriptor.Builder builder, String excludedField) {
      if (!"type".equals(excludedField)) {
        builder.tenant(TEST_TENANT);
      }
      if (!"tenant".equals(excludedField)) {
        builder.tenant(TEST_TENANT);
      }
      if (!"database".equals(excludedField)) {
        builder.database(TEST_DATABASE);
      }
      if (!"storage".equals(excludedField)) {
        builder.storage(TEST_STORAGE);
      }
    }
  }
}
