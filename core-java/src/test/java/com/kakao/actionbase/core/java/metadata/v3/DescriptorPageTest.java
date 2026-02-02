package com.kakao.actionbase.core.java.metadata.v3;

import static org.junit.jupiter.api.Assertions.*;

import com.kakao.actionbase.core.java.ApiVersion;
import com.kakao.actionbase.core.java.metadata.v3.common.DatastoreType;
import com.kakao.actionbase.core.java.metadata.v3.common.MutationMode;
import com.kakao.actionbase.core.java.metadata.v3.common.Schemas;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Page Domain Tests")
class DescriptorPageTest {

  private static final String TEST_TENANT = "test-tenant";

  private static final List<ImmutableDatabaseDescriptor> TEST_DATABASES =
      Arrays.asList(
          ImmutableDatabaseDescriptor.builder()
              .active(true)
              .tenant(TEST_TENANT)
              .database("test-database1")
              .comment("Test database 1")
              .build(),
          ImmutableDatabaseDescriptor.builder()
              .active(true)
              .tenant(TEST_TENANT)
              .database("test-database2")
              .comment("Test database 2")
              .build());

  private static final List<StorageDescriptor> TEST_STORAGES =
      Arrays.asList(
          ImmutableStorageDescriptor.builder()
              .active(true)
              .tenant(TEST_TENANT)
              .type(DatastoreType.MEMORY)
              .database("test-database")
              .storage("test-storage1")
              .datastore(Datastores.DEFAULT_DATASTORE_NAME)
              .comment("Test storage 1")
              .build(),
          ImmutableStorageDescriptor.builder()
              .active(true)
              .tenant(TEST_TENANT)
              .type(DatastoreType.MEMORY)
              .database("test-database")
              .database("test-database")
              .storage("test-storage2")
              .datastore(Datastores.DEFAULT_DATASTORE_NAME)
              .comment("Test storage 2")
              .build());

  private static final List<TenantDescriptor> TEST_TENANTS =
      Arrays.asList(
          ImmutableTenantDescriptor.builder()
              .active(true)
              .tenant("test-tenant1")
              .apiVersion(ApiVersion.V3)
              .comment("Test tenant 1")
              .build(),
          ImmutableTenantDescriptor.builder()
              .active(true)
              .tenant("test-tenant2")
              .apiVersion(ApiVersion.V3)
              .comment("Test tenant 2")
              .build());

  private static final List<TableDescriptor<?>> TEST_TABLES =
      Arrays.asList(
          ImmutableEdgeTableDescriptor.builder()
              .active(true)
              .tenant(TEST_TENANT)
              .database("test-database")
              .table("test-table1")
              .storage("test-storage")
              .comment("Test table 1")
              .schema(Schemas.EMPTY_EDGE)
              .mode(MutationMode.ASYNC)
              .build(),
          ImmutableEdgeTableDescriptor.builder()
              .active(true)
              .tenant(TEST_TENANT)
              .database("test-database")
              .table("test-table2")
              .storage("test-storage")
              .comment("Test table 2")
              .schema(Schemas.EMPTY_EDGE)
              .mode(MutationMode.DENY)
              .build());

  private static final List<AliasDescriptor> TEST_ALIASES =
      Arrays.asList(
          ImmutableAliasDescriptor.builder()
              .active(true)
              .tenant(TEST_TENANT)
              .database("test-database")
              .alias("test-alias1")
              .table("test-table")
              .comment("Test alias 1")
              .build(),
          ImmutableAliasDescriptor.builder()
              .active(true)
              .tenant(TEST_TENANT)
              .database("test-database")
              .alias("test-alias2")
              .table("test-table")
              .comment("Test alias 2")
              .build());

  private Page<ImmutableDatabaseDescriptor> databasePage;
  private Page<StorageDescriptor> storagePage;
  private Page<TenantDescriptor> tenantPage;
  private Page<TableDescriptor<?>> tablePage;
  private Page<AliasDescriptor> aliasPage;

  @BeforeEach
  void setUp() {
    databasePage = ImmutablePage.of(TEST_DATABASES);
    storagePage = ImmutablePage.of(TEST_STORAGES);
    tenantPage = ImmutablePage.of(TEST_TENANTS);
    tablePage = ImmutablePage.of(TEST_TABLES);
    aliasPage = ImmutablePage.of(TEST_ALIASES);
  }

  @Nested
  @DisplayName("Domain Rules")
  class DomainRulesTests {

    @Test
    @DisplayName("Page consists of count and content")
    void testConstruction() {
      // Database
      assertEquals(TEST_DATABASES.size(), databasePage.count());
      assertEquals(TEST_DATABASES, databasePage.content());

      // Storage
      assertEquals(TEST_STORAGES.size(), storagePage.count());
      assertEquals(TEST_STORAGES, storagePage.content());

      // Tenant
      assertEquals(TEST_TENANTS.size(), tenantPage.count());
      assertEquals(TEST_TENANTS, tenantPage.content());

      // Table
      assertEquals(TEST_TABLES.size(), tablePage.count());
      assertEquals(TEST_TABLES, tablePage.content());

      // Alias
      assertEquals(TEST_ALIASES.size(), aliasPage.count());
      assertEquals(TEST_ALIASES, aliasPage.content());
    }

    @Test
    @DisplayName("Can create empty Page")
    void testEmptyPage() {
      // Database
      Page<ImmutableDatabaseDescriptor> emptyDatabasePage =
          ImmutablePage.of(Collections.emptyList());
      assertTrue(emptyDatabasePage.isEmpty());
      assertFalse(databasePage.isEmpty());

      // Storage
      Page<StorageDescriptor> emptyStoragePage = ImmutablePage.of(Collections.emptyList());
      assertTrue(emptyStoragePage.isEmpty());
      assertFalse(storagePage.isEmpty());

      // Tenant
      Page<TenantDescriptor> emptyTenantPage = ImmutablePage.of(Collections.emptyList());
      assertTrue(emptyTenantPage.isEmpty());
      assertFalse(tenantPage.isEmpty());

      // Table
      Page<TableDescriptor<?>> emptyTablePage = ImmutablePage.of(Collections.emptyList());
      assertTrue(emptyTablePage.isEmpty());
      assertFalse(tablePage.isEmpty());

      // Alias
      Page<AliasDescriptor> emptyAliasPage = ImmutablePage.of(Collections.emptyList());
      assertTrue(emptyAliasPage.isEmpty());
      assertFalse(aliasPage.isEmpty());
    }
  }
}
