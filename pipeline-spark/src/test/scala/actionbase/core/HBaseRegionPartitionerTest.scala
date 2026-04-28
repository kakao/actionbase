package actionbase.core

import org.apache.hadoop.hbase.util.Bytes
import actionbase.pipeline.testsupport.BaseTest

class HBaseRegionPartitionerTest extends BaseTest {
  import HBaseRegionPartitioner._

  test("partitioning based on region start~end keys") {
    // region ranges:
    // 0: [min, "c")
    // 1: ["c", "m")
    // 2: ["m", max)
    val regionRanges = Array(
      (MIN_KEY, Bytes.toBytes("c")),
      (Bytes.toBytes("c"), Bytes.toBytes("m")),
      (Bytes.toBytes("m"), MAX_KEY) // open-ended
    )
    val regionSplits = Array(
      MIN_KEY,
      Bytes.toBytes("c"),
      Bytes.toBytes("m")
    )

    val partitionerByRange     = new HBaseRegionPartitioner(regionRanges)
    val partitionerBySplitKeys = HBaseRegionPartitioner(regionSplits)

    partitionerByRange.numPartitions shouldBe 3
    partitionerBySplitKeys.numPartitions shouldBe 3

    Seq(partitionerByRange, partitionerBySplitKeys).foreach { partitioner =>
      // region 0
      partitioner.getPartition(Bytes.toBytes("")) shouldBe 0
      partitioner.getPartition(Bytes.toBytes("a")) shouldBe 0
      partitioner.getPartition(Bytes.toBytes("b")) shouldBe 0

      // region 1
      partitioner.getPartition(Bytes.toBytes("c")) shouldBe 1
      partitioner.getPartition(Bytes.toBytes("g")) shouldBe 1
      partitioner.getPartition(Bytes.toBytes("l")) shouldBe 1

      // region 2
      partitioner.getPartition(Bytes.toBytes("m")) shouldBe 2
      partitioner.getPartition(Bytes.toBytes("z")) shouldBe 2
    }
  }

  test("partitioning base on region start-end(open-ended) keys") {
    // region ranges:
    // 0: [min, "c")
    // 1: ["c", "m")
    // 2: ["m", "")
    val regionRanges = Array(
      (MIN_KEY, Bytes.toBytes("c")),
      (Bytes.toBytes("c"), Bytes.toBytes("m")),
      (Bytes.toBytes("m"), Bytes.toBytes("")) // open-ended
    )

    val partitioner = new HBaseRegionPartitioner(regionRanges)

    partitioner.numPartitions shouldBe 3

    // region 0
    partitioner.getPartition(Bytes.toBytes("")) shouldBe 0
    partitioner.getPartition(Bytes.toBytes("a")) shouldBe 0
    partitioner.getPartition(Bytes.toBytes("b")) shouldBe 0

    // region 1
    partitioner.getPartition(Bytes.toBytes("c")) shouldBe 1
    partitioner.getPartition(Bytes.toBytes("g")) shouldBe 1
    partitioner.getPartition(Bytes.toBytes("l")) shouldBe 1

    // region 2
    partitioner.getPartition(Bytes.toBytes("m")) shouldBe 2
    partitioner.getPartition(Bytes.toBytes("z")) shouldBe 2
  }

  test("edge case: key equal to start/end") {
    val regionRanges = Array(
      (Bytes.toBytes("a"), Bytes.toBytes("b")),
      (Bytes.toBytes("b"), Bytes.toBytes("c")),
      (Bytes.toBytes("c"), MAX_KEY)
    )

    val partitioner = new HBaseRegionPartitioner(regionRanges)

    partitioner.numPartitions shouldBe 3

    // region 0: [a, b)
    partitioner.getPartition(Bytes.toBytes("a")) shouldBe 0
    partitioner.getPartition(Bytes.toBytes("ab")) shouldBe 0

    // region 1: [b, c)
    partitioner.getPartition(Bytes.toBytes("b")) shouldBe 1
    partitioner.getPartition(Bytes.toBytes("bb")) shouldBe 1

    // region 2: [c, max)
    partitioner.getPartition(Bytes.toBytes("c")) shouldBe 2
    partitioner.getPartition(Bytes.toBytes("z")) shouldBe 2
  }

  test("(1) HBaseRegionPartitioner should split regions into sub-partitions correctly") {
    val regionRanges = Array(
      (Bytes.toBytes("a"), Bytes.toBytes("c")),
      (Bytes.toBytes("c"), Bytes.toBytes("e")),
      (Bytes.toBytes("e"), OPEN_ENDED_KEY)
    )

    val partitioner = HBaseRegionPartitioner(regionRanges, 2)
    partitioner.numPartitions shouldBe 6

    // region 0: [a, c)
    partitioner.getPartition(Bytes.toBytes("a")) shouldBe 0
    partitioner.getPartition(Bytes.toBytes("b")) shouldBe 1

    // region 1: [c, e)
    partitioner.getPartition(Bytes.toBytes("c")) shouldBe 2
    partitioner.getPartition(Bytes.toBytes("d")) shouldBe 3

    // region 2: [e, max)
    partitioner.getPartition(Bytes.toBytes("e")) shouldBe 4
    partitioner.getPartition(Bytes.toBytes("f")) shouldBe 4
    partitioner.getPartition(Array[Byte](-1, -1, -1, -1, -1, -1, -1, 0)) shouldBe 5

    // check default
    val partitionerWithoutSubSplit = HBaseRegionPartitioner(regionRanges, 1)
    partitionerWithoutSubSplit.numPartitions shouldBe 3
    partitionerWithoutSubSplit.regionRanges should contain theSameElementsInOrderAs regionRanges
  }

  test("(2) HBaseRegionPartitioner should split regions into sub-partitions correctly") {
    val input = Array(
      (Bytes.toBytes(0L), Bytes.toBytes(100L)),
      (Bytes.toBytes(100L), Bytes.toBytes(200L))
    )
    val expected = Array(
      (0L, 50L),
      (50L, 100L),
      (100L, 150L),
      (150L, 200L)
    )

    val partitioner = HBaseRegionPartitioner(input, 2)
    partitioner.numPartitions shouldBe 4
    val actual = partitioner.regionRanges.map {
      case (start, end) => (Bytes.toLong(start), Bytes.toLong(end))
    }
    actual should contain theSameElementsInOrderAs expected
  }

  test("open-ended range handling in HBaseRegionPartitioner") {
    val regionRanges = Array(
      (Bytes.toBytes("a"), Bytes.toBytes("b")),
      (Bytes.toBytes("b"), Bytes.toBytes("c")),
      (Bytes.toBytes("c"), Array.emptyByteArray) // open-ended range
    )

    val partitioner = new HBaseRegionPartitioner(regionRanges)
    partitioner.numPartitions shouldBe 3
    partitioner.getPartition(Bytes.toBytes("a")) shouldBe 0
    partitioner.getPartition(Bytes.toBytes("b")) shouldBe 1
    partitioner.getPartition(Bytes.toBytes("c")) shouldBe 2
    partitioner.getPartition(Bytes.toBytes("z")) shouldBe 2
    partitioner.getPartition(Bytes.toBytes("zzz")) shouldBe 2
  }

  test("HBaseRegionPartitioner.apply with splitKeys") {
    // given
    val splitKeys = Array(
      Bytes.toBytes("key1"),
      Bytes.toBytes("key2"),
      Bytes.toBytes("key3")
    )

    // when
    val partitioner = HBaseRegionPartitioner(splitKeys)

    // then
    partitioner.numPartitions shouldBe splitKeys.length
    val expectedRanges = Array(
      (Bytes.toBytes("key1"), Bytes.toBytes("key2")),
      (Bytes.toBytes("key2"), Bytes.toBytes("key3")),
      (Bytes.toBytes("key3"), Array.emptyByteArray)
    )

    partitioner.regionRanges.length shouldBe expectedRanges.length
    expectedRanges.zip(partitioner.regionRanges).foreach {
      case ((expectedStart, expectedEnd), (actualStart, actualEnd)) =>
        Bytes.equals(expectedStart, actualStart) shouldBe true
        Bytes.equals(expectedEnd, actualEnd) shouldBe true
    }
  }
}
