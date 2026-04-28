package actionbase.pipeline.bulkload.step02.splitter.dynamic

case class LexicalOrderKeyValueMeta(
    path: String,
    startKey: Array[Byte],
    sizeBytes: Long
)
