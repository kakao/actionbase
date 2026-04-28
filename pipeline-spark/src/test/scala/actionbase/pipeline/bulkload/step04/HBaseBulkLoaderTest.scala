package actionbase.pipeline.bulkload.step04

import org.apache.hadoop.conf.Configuration
import actionbase.pipeline.testsupport.BaseTest

import java.io.File
import java.nio.file.Files

class HBaseBulkLoaderTest extends BaseTest {
  private val loader = new HBaseBulkLoader()
  private val conf   = new Configuration()

  private def createTempDir(): File = {
    val dir = Files.createTempDirectory("hbase-bulkloader-test").toFile
    dir.deleteOnExit()
    dir
  }

  test("throws IllegalArgumentException when the path does not exist") {
    val nonExistentPath = "/tmp/non-existent-path-" + System.currentTimeMillis()

    val ex = intercept[IllegalArgumentException] {
      loader.validate(nonExistentPath, conf)
    }
    ex.getMessage should include("does not exist")
  }

  test("throws IllegalStateException when the directory has no sub-directories") {
    val tempDir = createTempDir()

    val ex = intercept[IllegalStateException] {
      loader.validate(tempDir.getAbsolutePath, conf)
    }
    ex.getMessage should include("column family")
    tempDir.delete()
  }

  test("throws IllegalStateException when sub-directory has no HFile") {
    val tempDir = createTempDir()
    val cfDir   = new File(tempDir, "cf1")
    cfDir.mkdirs()

    val ex = intercept[IllegalStateException] {
      loader.validate(tempDir.getAbsolutePath, conf)
    }
    ex.getMessage should include("HFile")
    cfDir.delete()
    tempDir.delete()
  }

  test("validation passes when sub-directory has HFile") {
    val tempDir = createTempDir()
    val cfDir   = new File(tempDir, "cf1")
    cfDir.mkdirs()
    val hfile = new File(cfDir, "hfile_001")
    hfile.createNewFile()

    noException should be thrownBy {
      loader.validate(tempDir.getAbsolutePath, conf)
    }

    hfile.delete()
    cfDir.delete()
    tempDir.delete()
  }
}
