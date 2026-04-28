package actionbase.pipeline.bulkload.step01

import actionbase.core.model.{AbEdge, ActionbaseObjectMapper}
import com.kakao.actionbase.v2.core.code.DecodedEdge
import com.kakao.actionbase.v2.core.metadata.{Direction, EncodedEdgeType, LabelDTO}
import org.apache.hadoop.hbase.util.Bytes
import actionbase.pipeline.testsupport.BaseSparkTest

class EdgeEncoderTest extends BaseSparkTest {

  private val encoder = new EdgeEncoder()

  // Indexed table schema for testing (single edge without _id field)
  // LabelType values: NIL, MULTI_EDGE, IMMUTABLE_INDEXED, HASH, INDEXED
  private val indexedTableSchemaJson =
    """{
      |  "active": true,
      |  "name": "test.indexed_edge_label",
      |  "desc": "Test indexed edge",
      |  "type": "INDEXED",
      |  "schema": {
      |    "src": { "type": "LONG", "desc": "source id" },
      |    "tgt": { "type": "LONG", "desc": "target id" },
      |    "fields": [
      |      { "name": "score", "type": "INT", "nullable": false, "desc": "score value" }
      |    ]
      |  },
      |  "dirType": "OUT",
      |  "storage": "test_storage",
      |  "groups": [],
      |  "indices": [],
      |  "topNCompositeKeys": [],
      |  "event": false,
      |  "readOnly": true,
      |  "mode": "ASYNC"
      |}""".stripMargin

  // Multi-edge table schema for testing
  private val multiEdgeTableSchemaJson =
    """{
      |  "active": true,
      |  "name": "test.multi_edge_label",
      |  "desc": "Test multi edge",
      |  "type": "MULTI_EDGE",
      |  "schema": {
      |    "src": { "type": "LONG", "desc": "source id" },
      |    "tgt": { "type": "LONG", "desc": "target id" },
      |    "fields": [
      |      { "name": "_id", "type": "LONG", "nullable": false, "desc": "edge id" },
      |      { "name": "timestamp", "type": "LONG", "nullable": false, "desc": "event timestamp" }
      |    ]
      |  },
      |  "dirType": "BOTH",
      |  "storage": "test_storage",
      |  "groups": [],
      |  "indices": [
      |    { "name": "ts_desc", "fields": [{ "name": "timestamp", "order": "DESC" }], "desc": "timestamp desc" }
      |  ],
      |  "topNCompositeKeys": [],
      |  "event": false,
      |  "readOnly": true,
      |  "mode": "ASYNC"
      |}""".stripMargin

  test("encode - indexed table produces encoded results") {
    val table = ActionbaseObjectMapper.scala.readValue(indexedTableSchemaJson, classOf[LabelDTO])

    val srcId = 1001L
    val tgtId = 2001L
    val ts    = System.currentTimeMillis()
    val props = Map("score" -> 100)

    val edge    = AbEdge.createJavaBulkLoadEdge(active = true, ts = ts, src = srcId, tgt = tgtId, props = props)
    val edgeRdd = spark.sparkContext.parallelize(Seq(edge))

    val result = encoder.encode(edgeRdd, table).collect()

    // Should produce encoded results
    result.nonEmpty shouldBe true

    // Non-counter results should exist
    val nonCounterResults = result.filter(kv => kv.key.nonEmpty)
    nonCounterResults.nonEmpty shouldBe true
  }

  test("encode - multi edge with BOTH direction produces IN and OUT indexes") {
    val table = ActionbaseObjectMapper.scala.readValue(multiEdgeTableSchemaJson, classOf[LabelDTO])

    val srcId  = 1001L
    val tgtId  = 2001L
    val edgeId = 3001L
    val ts     = System.currentTimeMillis()
    val props  = Map("_id" -> edgeId, "timestamp" -> ts)

    val edge    = AbEdge.createJavaBulkLoadEdge(active = true, ts = ts, src = srcId, tgt = tgtId, props = props)
    val edgeRdd = spark.sparkContext.parallelize(Seq(edge))

    val result = encoder.encode(edgeRdd, table).collect()

    result.nonEmpty shouldBe true

    val hashToFieldNameMap = table.getSchema.getHashToFieldNameMap

    val decodedEdges = result
      .filter(kv => kv.key.nonEmpty)
      .flatMap { kv =>
        try {
          val decoded = DecodedEdge.from(
            new com.kakao.actionbase.v2.core.code.KeyFieldValue(kv.key, kv.value),
            hashToFieldNameMap
          )
          if (decoded.getType == EncodedEdgeType.COUNTER_EDGE_TYPE) None else Some(decoded)
        } catch {
          case _: IllegalArgumentException => None
        }
      }

    val indexedEdges = decodedEdges.filter(_.getType == EncodedEdgeType.INDEXED_EDGE_TYPE)

    // BOTH direction should produce both IN and OUT indexes
    val outIndexes = indexedEdges.filter(_.getDirection == Direction.OUT)
    val inIndexes  = indexedEdges.filter(_.getDirection == Direction.IN)

    outIndexes.nonEmpty shouldBe true
    inIndexes.nonEmpty shouldBe true
  }

  test("encode - counter key aggregation") {
    val table = ActionbaseObjectMapper.scala.readValue(indexedTableSchemaJson, classOf[LabelDTO])

    val srcId = 1001L
    val ts    = System.currentTimeMillis()

    // Create multiple edges from same source to different targets
    val edges = (1 to 5).map { i =>
      val props = Map("score" -> (i * 10))
      AbEdge.createJavaBulkLoadEdge(active = true, ts = ts, src = srcId, tgt = 2000L + i, props = props)
    }

    val edgeRdd = spark.sparkContext.parallelize(edges)
    val result  = encoder.encode(edgeRdd, table).collect()

    // Counter keys have empty key and count as value
    val counterResults = result.filter(kv => kv.key.isEmpty)

    // Counters are grouped by value (which is the counter key bytes)
    // Each unique counter key should have a count
    counterResults.foreach { counter =>
      val count = Bytes.toLong(counter.value)
      count should be > 0L
    }
  }

  test("encode - empty RDD produces empty result") {
    val table = ActionbaseObjectMapper.scala.readValue(indexedTableSchemaJson, classOf[LabelDTO])

    val emptyRdd = spark.sparkContext.emptyRDD[com.kakao.actionbase.v2.core.edge.BulkLoadEdge]
    val result   = encoder.encode(emptyRdd, table).collect()

    result shouldBe empty
  }

  test("encode - multiple edges are processed correctly") {
    val table = ActionbaseObjectMapper.scala.readValue(indexedTableSchemaJson, classOf[LabelDTO])

    val ts = System.currentTimeMillis()
    val edges = (1 to 10).map { i =>
      val props = Map("score" -> i)
      AbEdge.createJavaBulkLoadEdge(active = true, ts = ts, src = i.toLong, tgt = (i + 100).toLong, props = props)
    }

    val edgeRdd = spark.sparkContext.parallelize(edges)
    val result  = encoder.encode(edgeRdd, table).collect()

    // Should have results for all 10 edges
    result.nonEmpty shouldBe true

    // Non-counter results should be at least 10 (one edge state per edge)
    val nonCounterResults = result.filter(kv => kv.key.nonEmpty)
    nonCounterResults.length should be >= 10
  }

  test("encode - inactive edge is encoded correctly") {
    val table = ActionbaseObjectMapper.scala.readValue(indexedTableSchemaJson, classOf[LabelDTO])

    val srcId = 1001L
    val tgtId = 2001L
    val ts    = System.currentTimeMillis()
    val props = Map("score" -> 100)

    // Create inactive edge (deleted)
    val edge    = AbEdge.createJavaBulkLoadEdge(active = false, ts = ts, src = srcId, tgt = tgtId, props = props)
    val edgeRdd = spark.sparkContext.parallelize(Seq(edge))

    val result = encoder.encode(edgeRdd, table).collect()

    // Inactive edges should still be encoded (for delete markers)
    result.nonEmpty shouldBe true
  }
}
