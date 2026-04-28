package actionbase.pipeline.bulkload.step02.splitter.dynamic

import actionbase.pipeline.testsupport.BaseTest

class RegionSizeTest extends BaseTest {

  test("RegionSize should convert bytes to megabytes correctly") {
    val bytes  = 1024L * 1024L * 100L // 100 MB
    val result = RegionSize.toMB(bytes)

    result.value shouldBe 100.0
    result.unit shouldBe RegionSizeUnit.Megabytes
  }

  test("RegionSize should convert bytes to megabytes with scale") {
    val bytes  = 1024L * 1024L * 100L + 512L * 1024L // 100.5 MB
    val result = RegionSize.toMB(bytes, scale = 2)

    result.value shouldBe 100.5
    result.unit shouldBe RegionSizeUnit.Megabytes
  }

  test("RegionSize should parse string format correctly") {
    RegionSize.of("100MB").toBytes shouldBe 100.0 * 1024 * 1024
    RegionSize.of("100 MB").toBytes shouldBe 100.0 * 1024 * 1024
    RegionSize.of("100mb").toBytes shouldBe 100.0 * 1024 * 1024
    RegionSize.of("50KB").toBytes shouldBe 50.0 * 1024
    RegionSize.of("1024B").toBytes shouldBe 1024.0
  }

  test("RegionSize should throw exception for invalid format") {
    assertThrows[IllegalArgumentException] {
      RegionSize.of("invalid")
    }
    assertThrows[IllegalArgumentException] {
      RegionSize.of("100GB") // GB is not supported
    }
  }

  test("RegionSize toBytes should calculate correctly") {
    val size = RegionSize(100, RegionSizeUnit.Megabytes)
    size.toBytes shouldBe 100.0 * 1024 * 1024
  }

  test("RegionSize to should convert between units") {
    val sizeInMB = RegionSize(1, RegionSizeUnit.Megabytes)
    val sizeInKB = sizeInMB.to(RegionSizeUnit.Kilobytes)

    sizeInKB.value shouldBe 1024.0
    sizeInKB.unit shouldBe RegionSizeUnit.Kilobytes
  }

  test("RegionSize toMB should handle large byte values") {
    val bytes  = 9L * 1024L * 1024L * 1024L // 9 GB
    val result = RegionSize.toMB(bytes, scale = 2)

    result.value shouldBe 9216.0 // 9 * 1024 MB
    result.unit shouldBe RegionSizeUnit.Megabytes
  }
}
