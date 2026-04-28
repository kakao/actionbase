package actionbase.pipeline.bulkload.step02.splitter.dynamic

case class LexicalOrderKeyValueMetaList(
    files: Seq[LexicalOrderKeyValueMeta]
) {
  def totalSize: Long = files.map(_.sizeBytes).sum

  def startKey: Array[Byte] = files.head.startKey
}

object LexicalOrderKeyValueMetaList {
  def groupBySizeLimit(
      sortedMetas: Seq[LexicalOrderKeyValueMeta],
      maxGroupSizeBytes: Long
  ): Seq[LexicalOrderKeyValueMetaList] = {
    val grouped           = collection.mutable.ListBuffer.empty[LexicalOrderKeyValueMetaList]
    val current           = collection.mutable.ListBuffer.empty[LexicalOrderKeyValueMeta]
    var currentSize: Long = 0L

    sortedMetas.foreach { meta =>
      if (currentSize + meta.sizeBytes > maxGroupSizeBytes && current.nonEmpty) {
        grouped += LexicalOrderKeyValueMetaList(current.toList)
        current.clear()
        currentSize = 0L
      }
      current += meta
      currentSize += meta.sizeBytes
    }

    if (current.nonEmpty) {
      grouped += LexicalOrderKeyValueMetaList(current.toList)
    }

    grouped.toList
  }
}
