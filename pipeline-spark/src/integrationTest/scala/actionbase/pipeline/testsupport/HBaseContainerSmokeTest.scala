package actionbase.pipeline.testsupport

import org.apache.hadoop.hbase.client.{ColumnFamilyDescriptorBuilder, TableDescriptorBuilder}
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.hbase.{NamespaceDescriptor, TableName}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * Smoke test for `HBaseContainer`. Proves that the image builds, the
 * container boots, and the host JVM can open an HBase `Connection`
 * against it and perform basic admin operations.
 *
 * Uses the JVM-shared `HBaseContainer.shared()` instance so the container
 * is started at most once across all integration-test suites; suite-scoped
 * namespace/table names keep state isolated.
 */
class HBaseContainerSmokeTest extends AnyFunSuite with Matchers with BeforeAndAfterAll {
  private val container = HBaseContainer.shared()
  private val nsName    = "smoke_ns"
  private val tableName = TableName.valueOf(nsName, "smoke_table")

  override def afterAll(): Unit = {
    try cleanup()
    finally super.afterAll()
  }

  private def cleanup(): Unit = {
    val conn = container.newConnection()
    try {
      val admin = conn.getAdmin
      try {
        if (admin.tableExists(tableName)) {
          if (admin.isTableEnabled(tableName)) admin.disableTable(tableName)
          admin.deleteTable(tableName)
        }
        if (admin.listNamespaceDescriptors.exists(_.getName == nsName)) {
          admin.deleteNamespace(nsName)
        }
      } finally admin.close()
    } finally conn.close()
  }

  test("namespace and table can be created via testcontainers HBase") {
    val conn = container.newConnection()
    try {
      val admin = conn.getAdmin
      try {
        admin.createNamespace(NamespaceDescriptor.create(nsName).build())
        admin.createTable(
          TableDescriptorBuilder.newBuilder(tableName)
            .setColumnFamily(ColumnFamilyDescriptorBuilder.of(Bytes.toBytes("f")))
            .build()
        )
        admin.tableExists(tableName) shouldBe true
      } finally admin.close()
    } finally conn.close()
  }
}
