package actionbase.pipeline.bulkload.step01

import actionbase.core.model.AbKeyValue
import com.kakao.actionbase.v2.core.code.{BulkEdgeEncoder, EdgeEncoderFactory}
import com.kakao.actionbase.v2.core.edge.BulkLoadEdge
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Dataset, SparkSession}

import scala.jdk.CollectionConverters.asScalaBufferConverter

class EdgeEncoder extends Serializable {
  import com.kakao.actionbase.v2.core.metadata.{LabelDTO => AbTable}

  /**
    * Encode All edges.
    *
    * @note When a edgeCount is encoded, a key is empty
    *
    * @param rdd
    * @param newTable
    * @return
    */
  def encode(rdd: RDD[BulkLoadEdge], newTable: AbTable)(implicit spark: SparkSession): Dataset[AbKeyValue] = {
    import spark.implicits._
    val encodedAllEdges: Dataset[AbKeyValue] = bulkEncodeAll(rdd, newTable)

    val edgeStatesAndIndexes = encodedAllEdges.filter(!_.isCounterKey)

    val edgeCounters =
      encodedAllEdges
        .filter(_.isCounterKey)
        .groupByKey(_.value)
        .count()
        .map { case (value, count) => AbKeyValue(value, Bytes.toBytes(count)) }

    edgeStatesAndIndexes.union(edgeCounters)
  }

  private def bulkEncodeAll(edges: RDD[BulkLoadEdge], abTable: AbTable)(
      implicit spark: SparkSession
  ): Dataset[AbKeyValue] = {
    import spark.implicits._
    edges.mapPartitions { partitionedEdge =>
      val factory = new EdgeEncoderFactory(1)
      val encoder = factory.getBytesKeyValueEncoder

      partitionedEdge
        .flatMap { edge =>
          BulkEdgeEncoder.bulkEncodeAll(encoder, edge, abTable).asScala
        }
        .map { keyFieldValue =>
          keyFieldValue.getField
          AbKeyValue(keyFieldValue.getKey, keyFieldValue.getValue)
        }
    }.toDS
  }
}
