package actionbase.pipeline.bulkload.step02.splitter.dynamic

import actionbase.pipeline.testsupport.BaseTest

class LexicalOrderKeyValueMetaListTest extends BaseTest {

  test("groupBySizeLimit should create single group when total size is under limit") {
    // Given
    val metas = Seq(
      LexicalOrderKeyValueMeta("/path/1", Array[Byte](1), 100L),
      LexicalOrderKeyValueMeta("/path/2", Array[Byte](2), 100L),
      LexicalOrderKeyValueMeta("/path/3", Array[Byte](3), 100L)
    )
    val maxGroupSize = 500L

    // When
    val result = LexicalOrderKeyValueMetaList.groupBySizeLimit(metas, maxGroupSize)

    // Then
    result.size shouldBe 1
    result.head.files.size shouldBe 3
    result.head.totalSize shouldBe 300L
  }

  test("groupBySizeLimit should split into multiple groups when exceeding limit") {
    // Given
    val metas = Seq(
      LexicalOrderKeyValueMeta("/path/1", Array[Byte](1), 100L),
      LexicalOrderKeyValueMeta("/path/2", Array[Byte](2), 100L),
      LexicalOrderKeyValueMeta("/path/3", Array[Byte](3), 100L),
      LexicalOrderKeyValueMeta("/path/4", Array[Byte](4), 100L)
    )
    val maxGroupSize = 250L

    // When
    val result = LexicalOrderKeyValueMetaList.groupBySizeLimit(metas, maxGroupSize)

    // Then
    result.size shouldBe 2
    result(0).files.size shouldBe 2
    result(0).totalSize shouldBe 200L
    result(1).files.size shouldBe 2
    result(1).totalSize shouldBe 200L
  }

  test("groupBySizeLimit should handle single file per group") {
    // Given
    val metas = Seq(
      LexicalOrderKeyValueMeta("/path/1", Array[Byte](1), 100L),
      LexicalOrderKeyValueMeta("/path/2", Array[Byte](2), 100L),
      LexicalOrderKeyValueMeta("/path/3", Array[Byte](3), 100L)
    )
    val maxGroupSize = 100L

    // When
    val result = LexicalOrderKeyValueMetaList.groupBySizeLimit(metas, maxGroupSize)

    // Then
    result.size shouldBe 3
    result.foreach(_.files.size shouldBe 1)
  }

  test("groupBySizeLimit should handle file larger than limit") {
    // Given: one file is larger than the limit
    val metas = Seq(
      LexicalOrderKeyValueMeta("/path/1", Array[Byte](1), 100L),
      LexicalOrderKeyValueMeta("/path/2", Array[Byte](2), 500L), // larger than limit
      LexicalOrderKeyValueMeta("/path/3", Array[Byte](3), 100L)
    )
    val maxGroupSize = 200L

    // When
    val result = LexicalOrderKeyValueMetaList.groupBySizeLimit(metas, maxGroupSize)

    // Then: the large file should be in its own group
    result.size shouldBe 3
    result(0).files.size shouldBe 1
    result(0).totalSize shouldBe 100L
    result(1).files.size shouldBe 1
    result(1).totalSize shouldBe 500L
    result(2).files.size shouldBe 1
    result(2).totalSize shouldBe 100L
  }

  test("groupBySizeLimit should return empty sequence for empty input") {
    // Given
    val metas        = Seq.empty[LexicalOrderKeyValueMeta]
    val maxGroupSize = 100L

    // When
    val result = LexicalOrderKeyValueMetaList.groupBySizeLimit(metas, maxGroupSize)

    // Then
    result shouldBe empty
  }

  test("groupBySizeLimit should preserve order of files") {
    // Given
    val metas = Seq(
      LexicalOrderKeyValueMeta("/path/a", Array[Byte](1), 100L),
      LexicalOrderKeyValueMeta("/path/b", Array[Byte](2), 100L),
      LexicalOrderKeyValueMeta("/path/c", Array[Byte](3), 100L),
      LexicalOrderKeyValueMeta("/path/d", Array[Byte](4), 100L)
    )
    val maxGroupSize = 200L

    // When
    val result = LexicalOrderKeyValueMetaList.groupBySizeLimit(metas, maxGroupSize)

    // Then
    result.size shouldBe 2
    result(0).files.map(_.path) shouldBe Seq("/path/a", "/path/b")
    result(1).files.map(_.path) shouldBe Seq("/path/c", "/path/d")
  }

  test("LexicalOrderKeyValueMetaList totalSize should sum all file sizes") {
    // Given
    val metaList = LexicalOrderKeyValueMetaList(
      Seq(
        LexicalOrderKeyValueMeta("/path/1", Array[Byte](1), 100L),
        LexicalOrderKeyValueMeta("/path/2", Array[Byte](2), 200L),
        LexicalOrderKeyValueMeta("/path/3", Array[Byte](3), 300L)
      )
    )

    // When & Then
    metaList.totalSize shouldBe 600L
  }

  test("LexicalOrderKeyValueMetaList startKey should return first file's start key") {
    // Given
    val metaList = LexicalOrderKeyValueMetaList(
      Seq(
        LexicalOrderKeyValueMeta("/path/1", Array[Byte](10, 20, 30), 100L),
        LexicalOrderKeyValueMeta("/path/2", Array[Byte](40, 50, 60), 200L)
      )
    )

    // When & Then
    metaList.startKey shouldBe Array[Byte](10, 20, 30)
  }

  test("groupBySizeLimit should handle boundary case where adding file exactly reaches limit") {
    // Given
    val metas = Seq(
      LexicalOrderKeyValueMeta("/path/1", Array[Byte](1), 100L),
      LexicalOrderKeyValueMeta("/path/2", Array[Byte](2), 100L), // total = 200, exactly at limit
      LexicalOrderKeyValueMeta("/path/3", Array[Byte](3), 100L)
    )
    val maxGroupSize = 200L

    // When
    val result = LexicalOrderKeyValueMetaList.groupBySizeLimit(metas, maxGroupSize)

    // Then: should create new group when exceeding, not when equal
    result.size shouldBe 2
    result(0).files.size shouldBe 2
    result(0).totalSize shouldBe 200L
    result(1).files.size shouldBe 1
    result(1).totalSize shouldBe 100L
  }
}
