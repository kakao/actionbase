package actionbase.pipeline.bulkload.step02.splitter.dynamic

private[dynamic] case class GroupedMeta(
    parquetFilesSizeLimit: Long,
    chunkSavedPath: String,
    chunks: Seq[LexicalOrderKeyValueMetaList]
) {
  lazy val totalSize: Long = chunks.map(_.totalSize).sum
}
