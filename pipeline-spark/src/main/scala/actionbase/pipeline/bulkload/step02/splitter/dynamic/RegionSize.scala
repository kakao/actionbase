package actionbase.pipeline.bulkload.step02.splitter.dynamic

import scala.math.BigDecimal.RoundingMode
import scala.util.matching.Regex

case class RegionSize(value: Double, unit: RegionSizeUnit) {
  def toBytes: Double = value * unit.bytes

  def to(unit: RegionSizeUnit): RegionSize = RegionSize(toBytes / unit.bytes, unit)
}

object RegionSize {
  def toMB(bytes: Double): RegionSize = RegionSize(bytes, RegionSizeUnit.Bytes).to(RegionSizeUnit.Megabytes)

  def toMB(bytes: Double, scale: Int): RegionSize = {
    val converted = toMB(bytes)
    RegionSize(
      BigDecimal.decimal(converted.value).setScale(scale, RoundingMode.HALF_EVEN).doubleValue(),
      RegionSizeUnit.Megabytes
    )
  }

  def of(size: String): RegionSize = {
    val (value, unit) = parseCaseInsensitive(input = size)

    RegionSize(value, unit = RegionSizeUnit.findByName(name = unit))
  }

  private val sizeRegex: Regex = """(?i)(\d+(?:\.\d+)?)\s*(B|KB|MB)""".r

  private def parseCaseInsensitive(input: String): (Double, String) = {
    sizeRegex.findFirstMatchIn(source = input.trim) match {
      case Some(matched) => matched.group(1).toDouble -> matched.group(2).toUpperCase
      case _             => throw new IllegalArgumentException(s"Invalid format: $input")
    }
  }
}
