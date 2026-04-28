package actionbase.pipeline.bulkload.step02.splitter.dynamic

sealed trait RegionSizeUnit {
  def abb: String
  def bytes: Long
}

object RegionSizeUnit {
  case object Bytes extends RegionSizeUnit {
    val abb         = "B"
    val bytes: Long = 1
  }

  case object Kilobytes extends RegionSizeUnit {
    val abb         = "KB"
    val bytes: Long = 1024
  }

  case object Megabytes extends RegionSizeUnit {
    val abb         = "MB"
    val bytes: Long = 1024 * 1024
  }

  private val sizeUnitByName: Map[String, RegionSizeUnit] = Map(
    Bytes.abb.toLowerCase     -> Bytes,
    Kilobytes.abb.toLowerCase -> Kilobytes,
    Megabytes.abb.toLowerCase -> Megabytes
  )

  def findByName(name: String): RegionSizeUnit = {
    val sizeUnitOpt = sizeUnitByName.get(name.toLowerCase)

    if (sizeUnitOpt.isEmpty) {
      throw new IllegalArgumentException(s"Invalid SizeUnit: $name")
    }

    sizeUnitOpt.get
  }
}
