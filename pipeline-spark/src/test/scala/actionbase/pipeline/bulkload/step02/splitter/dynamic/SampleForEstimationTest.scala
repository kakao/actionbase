package actionbase.pipeline.bulkload.step02.splitter.dynamic

import actionbase.pipeline.testsupport.BaseTest

class SampleForEstimationTest extends BaseTest {

  test("calculatedParquetFileSizeLimit should calculate correct limit based on compression ratio") {
    // Given: parquet 100MB, hfile 50MB (compression ratio 2:1)
    val sample = SampleForEstimation(
      parquetPath = "/sample/parquet",
      parquetSizeByte = 100L * 1024 * 1024, // 100 MB
      hfileHdfsUri = "/sample/hfile",
      hfileSizeByte = 50L * 1024 * 1024 // 50 MB
    )
    val maxRegionSize = RegionSize(1000, RegionSizeUnit.Megabytes) // 1000 MB

    // When
    val result = sample.calculatedParquetFileSizeLimit(maxRegionSize)

    // Then: parquetSize : hfileSize = x : regionSize
    // 100 : 50 = x : 1000 => x = 2000 MB
    val expectedBytes = 2000L * 1024 * 1024
    result shouldBe expectedBytes
  }

  test("calculatedParquetFileSizeLimit should handle 1:1 compression ratio") {
    // Given: parquet 100MB, hfile 100MB (compression ratio 1:1)
    val sample = SampleForEstimation(
      parquetPath = "/sample/parquet",
      parquetSizeByte = 100L * 1024 * 1024,
      hfileHdfsUri = "/sample/hfile",
      hfileSizeByte = 100L * 1024 * 1024
    )
    val maxRegionSize = RegionSize(9000, RegionSizeUnit.Megabytes)

    // When
    val result = sample.calculatedParquetFileSizeLimit(maxRegionSize)

    // Then: 100 : 100 = x : 9000 => x = 9000 MB
    val expectedBytes = 9000L * 1024 * 1024
    result shouldBe expectedBytes
  }

  test("calculatedParquetFileSizeLimit should handle expansion ratio (hfile larger than parquet)") {
    // Given: parquet 100MB, hfile 200MB (expansion ratio 1:2)
    val sample = SampleForEstimation(
      parquetPath = "/sample/parquet",
      parquetSizeByte = 100L * 1024 * 1024,
      hfileHdfsUri = "/sample/hfile",
      hfileSizeByte = 200L * 1024 * 1024
    )
    val maxRegionSize = RegionSize(1000, RegionSizeUnit.Megabytes)

    // When
    val result = sample.calculatedParquetFileSizeLimit(maxRegionSize)

    // Then: 100 : 200 = x : 1000 => x = 500 MB
    val expectedBytes = 500L * 1024 * 1024
    result shouldBe expectedBytes
  }

  test("calculatedParquetFileSizeLimit should handle large values without overflow") {
    // Given: large values that could cause overflow with simple Long multiplication
    val sample = SampleForEstimation(
      parquetPath = "/sample/parquet",
      parquetSizeByte = 10L * 1024 * 1024 * 1024, // 10 GB
      hfileHdfsUri = "/sample/hfile",
      hfileSizeByte = 5L * 1024 * 1024 * 1024 // 5 GB
    )
    val maxRegionSize = RegionSize(9000, RegionSizeUnit.Megabytes) // 9000 MB

    // When
    val result = sample.calculatedParquetFileSizeLimit(maxRegionSize)

    // Then: 10GB : 5GB = x : 9000MB
    // 10240MB : 5120MB = x : 9000MB => x = (10240 * 9000) / 5120 = 18000 MB
    val expectedBytes = 18000L * 1024 * 1024
    result shouldBe expectedBytes
  }

  test("calculatedParquetFileSizeLimit should handle realistic scenario") {
    // Given: typical production scenario - parquet 1GB, hfile 800MB
    val sample = SampleForEstimation(
      parquetPath = "/sample/parquet",
      parquetSizeByte = 1L * 1024 * 1024 * 1024, // 1 GB
      hfileHdfsUri = "/sample/hfile",
      hfileSizeByte = 800L * 1024 * 1024 // 800 MB
    )
    val maxRegionSize = RegionSize(9000, RegionSizeUnit.Megabytes) // default 9GB

    // When
    val result = sample.calculatedParquetFileSizeLimit(maxRegionSize)

    // Then: 1GB : 800MB = x : 9000MB
    // 1024MB : 800MB = x : 9000MB => x = (1024 * 9000) / 800 = 11520 MB
    val expectedBytes = 11520L * 1024 * 1024
    result shouldBe expectedBytes
  }
}
