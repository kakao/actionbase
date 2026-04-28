package actionbase.pipeline.adapter

/**
  * Plain HDFS cluster descriptor used by pipeline algorithms.
  *
  * The former `HdfsMeta` object with baked-in cluster entries and the
  * `HdfsMetaProvider` lookup were driver concerns. OSS callers now
  * construct an [[HdfsMeta]] instance directly with the two fields
  * algorithms actually consume:
  *  - `clusterName` — stable identifier used for logging / path composition.
  *  - `nameServiceUri` — prefix used to turn a logical path into an
  *    `hdfs://...` URI (e.g. `hdfs://mycluster` or `file://`).
  *
  * Concrete cluster catalogs live in the external consumer.
  */
case class HdfsMeta(clusterName: String, nameServiceUri: String) {

  /** Convert a logical path into a full URI. */
  def pathToHdfsURI(path: String): String = s"$nameServiceUri$path"
}
