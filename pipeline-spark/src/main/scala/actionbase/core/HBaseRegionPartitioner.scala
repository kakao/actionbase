package actionbase.core

import org.apache.hadoop.hbase.client.RegionLocator
import org.apache.hadoop.hbase.shaded.org.apache.commons.lang3.ArrayUtils
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.Partitioner

import scala.jdk.CollectionConverters.collectionAsScalaIterableConverter

class HBaseRegionPartitioner(val regionRanges: Array[(Array[Byte], Array[Byte])]) extends Partitioner {
  private val splitKeys: Array[Array[Byte]] =
    regionRanges.map(_._2).filter(_.nonEmpty)

  override def numPartitions: Int = regionRanges.length

  override def getPartition(key: Any): Int = {
    val bytesKey       = key.asInstanceOf[Array[Byte]]
    val idx            = java.util.Arrays.binarySearch(splitKeys, bytesKey, Bytes.BYTES_COMPARATOR)
    val insertionPoint = if (idx >= 0) idx + 1 else -idx - 1
    val partitionIndex = math.min(insertionPoint, splitKeys.length)
    partitionIndex
  }

  override def toString: String = {
    val lines = regionRanges
      .map {
        case (start, end) =>
          val startHex = Bytes.toHex(start)
          val endHex   = if (end.isEmpty) "MAX" else Bytes.toHex(end)
          s"$startHex ---> $endHex"
      }
      .mkString("\n")
    s"""
       |HBaseRegionPartitioner(
       |  numPartitions = $numPartitions
       |[regionRanges]
       |$lines
       |)
     """.stripMargin
  }
}

object HBaseRegionPartitioner {
  val MIN_KEY: Array[Byte] = ArrayUtils.EMPTY_BYTE_ARRAY
  val MAX_KEY: Array[Byte] = Array[Byte](-1, -1, -1, -1, -1, -1, -1, -1)
  val OPEN_ENDED_KEY       = MIN_KEY

  def apply(splitKeys: Array[Array[Byte]]): HBaseRegionPartitioner = {
    if (splitKeys.isEmpty) {
      // Handle empty case - create a single region covering everything
      val regionRanges = Array((MIN_KEY, OPEN_ENDED_KEY))
      new HBaseRegionPartitioner(regionRanges)
    } else if (splitKeys.length == 1) {
      // Handle single split key case - create two regions
      val singleKey = splitKeys(0)
      val regionRanges = Array(
        (MIN_KEY, singleKey),
        (singleKey, OPEN_ENDED_KEY)
      )
      new HBaseRegionPartitioner(regionRanges)
    } else {
      val regionRanges = splitKeys
        .sliding(2)
        .map {
          case Array(start, end) => (start, end)
        }
        .toSeq ++ Seq((splitKeys.last, OPEN_ENDED_KEY))
      new HBaseRegionPartitioner(regionRanges.toArray)
    }
  }

  def apply(regionLocator: RegionLocator, splitPerRegion: Int = 1): HBaseRegionPartitioner = {
    val regionRanges = regionLocator.getAllRegionLocations.asScala.map { loc =>
      val region   = loc.getRegion
      val startKey = region.getStartKey
      val endKey   = region.getEndKey
      (startKey, endKey)
    }.toArray

    HBaseRegionPartitioner.apply(regionRanges, splitPerRegion)
  }

  def apply(regionRanges: Array[(Array[Byte], Array[Byte])], splitPerRegion: Int): HBaseRegionPartitioner = {
    val splitRanges = regionRanges.flatMap {
      case (start, end) =>
        if (splitPerRegion == 1) {
          Seq(start -> end)
        } else {
          val (paddedStart, paddedEnd) = padToSameLength(start, end)
          val arr                      = Bytes.split(paddedStart, paddedEnd, true, splitPerRegion - 1)
          arr // to tuple
            .sliding(2)
            .collect {
              case Array(s, e) if s != null && e != null => (s, e)
            }
            .toSeq
        }
    }
    // change MAX_KEY to open-ended end key
    val splitRangesWithOpenEnded = splitRanges.map {
      case (start, end) if end.sameElements(MAX_KEY) => (start, Array[Byte]())
      case other                                     => other
    }
    new HBaseRegionPartitioner(splitRangesWithOpenEnded)
  }

  private def padToSameLength(start: Array[Byte], _end: Array[Byte]): (Array[Byte], Array[Byte]) = {
    // Open-ended end key — substitute MAX_KEY for length alignment.
    val end    = if (_end.isEmpty) MAX_KEY else _end
    val maxLen = math.max(start.length, end.length)
    val s      = if (start.length < maxLen) Array.fill(maxLen - start.length)(0.toByte) ++ start else start
    val e      = if (end.length < maxLen) Array.fill(maxLen - end.length)(0.toByte) ++ end else end
    (s, e)
  }
}
