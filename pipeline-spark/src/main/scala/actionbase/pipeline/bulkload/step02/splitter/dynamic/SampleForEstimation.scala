package actionbase.pipeline.bulkload.step02.splitter.dynamic

private[dynamic] case class SampleForEstimation(
    parquetPath: String,
    parquetSizeByte: Long,
    hfileHdfsUri: String,
    hfileSizeByte: Long
) {

  def calculatedParquetFileSizeLimit(maxRegionSize: RegionSize): Long = {
    // parquetSize : hfileSize = x : regionSize.toBytes
    val bigParquetSize     = BigInt(parquetSizeByte)
    val bigHfileSize       = BigInt(hfileSizeByte)
    val bigRegionSizeBytes = BigInt(maxRegionSize.toBytes.toLong)
    val x                  = (bigParquetSize * bigRegionSizeBytes) / bigHfileSize
    x.toLong
  }

}
