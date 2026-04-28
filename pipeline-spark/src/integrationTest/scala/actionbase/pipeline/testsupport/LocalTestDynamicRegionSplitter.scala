package actionbase.pipeline.testsupport

import actionbase.core.AbInfo
import actionbase.core.model.HBaseTableSchema
import actionbase.pipeline.adapter.HdfsMeta
import actionbase.pipeline.bulkload.step02.splitter.dynamic.DynamicRegionSplitter
import actionbase.pipeline.bulkload.step02.splitter.dynamic.RegionSize
import org.apache.spark.sql.SparkSession

/**
 * Top-level test variant of `DynamicRegionSplitter` that overrides the tmp
 * storage root so HFiles stay inside the `file://` test directory (host
 * volume-mounted into the HBase container via `HBaseContainer.sharedRootDir`).
 *
 * Placed as a top-level class (not inside a test suite) so that closures
 * inside `DynamicRegionSplitter` that reference this splitter instance
 * don't capture the ScalaTest `Engine` via `$outer` — that engine is not
 * `Serializable` and Spark closure serialization fails when it walks the
 * graph during `rdd.map` / `saveAsNewAPIHadoopFile`.
 */
class LocalTestDynamicRegionSplitter(
    override val newAbInfo: AbInfo,
    override val tableSchema: HBaseTableSchema,
    tmpHdfsRoot: String,
    numPartitionForKeyValueDS: Int,
    maxRegionSize: RegionSize,
    printDebug: Boolean
)(implicit spark: SparkSession)
    extends DynamicRegionSplitter(
      newAbInfo = newAbInfo,
      tableSchema = tableSchema,
      // Empty nameServiceUri — paths stay as bare absolute filesystem
      // paths (`/tmp/...`); `FileSystem.get(conf)` resolves them against
      // `fs.defaultFS = file:///`. Using a non-empty prefix here causes
      // double-slash URIs (e.g. `file:////tmp/...`) that Hadoop's
      // LocalFileSystem and HBase's bulkload client disagree about.
      targetHDFSMeta = HdfsMeta("local-insecure", ""),
      tmpRootPath = tmpHdfsRoot,
      numPartitionForKeyValueDS = numPartitionForKeyValueDS,
      maxRegionSize = maxRegionSize,
      printDebug = printDebug
    )

