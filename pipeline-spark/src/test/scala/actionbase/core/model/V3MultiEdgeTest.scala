package actionbase.core.model

import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import org.scalatest.prop.Tables.Table
import actionbase.pipeline.testsupport.BaseTest

class V3MultiEdgeTest extends BaseTest {
  test("from") {
    // Synthetic identifiers: version/ts are fixed placeholders, src/tgt/_id use
    // 10xx/20xx/30xx ranges so they can't be mistaken for production IDs.
    val actual =
      AbWal
        .parse(
          """{"alias":"example.example_sent_v1","label":"example.example_sent_v1_20260101_000000","edge":{"ts":1700000000000000000,"src":1001,"tgt":1002,"props":{"idA":2001,"tsA":1700000000000,"count":1,"amount":9400,"status":601,"tsB":1700000000000,"kind":"others","flagA":"Y","flagB":"1","idB":2002,"labelC":"103107114100100","idC":2003,"flagC":true,"flagD":true,"_id":3001},"traceId":"01TESTTRACE000000000000000"},"op":"INSERT","mode":{"l":"SYNC","r":"SYNC","queue":false},"audit":{"actor":"ExampleSentV1Loader"},"requestId":"00000000000000000000000000000000","ts":1700000000000,"tenant":"stg","phase":"stg","version":"2"}"""
        )
        .map { wal =>
          V3MultiEdge.from(traceEdge = wal.edge)
        }
        .get

    actual.version shouldBe 1700000000000000000L
    actual.id shouldBe 3001L
    actual.source shouldBe 1001
    actual.target shouldBe 1002
    actual.properties.get("_id") shouldBe Some(3001L)
  }

  test("V3MultiEdge deserialization") {
    val version = System.currentTimeMillis()
    val cases = Table(
      ("version", "id", "source", "target", "properties", "expected"),
      (
        version,
        1,
        100,
        200,
        """{"key1": 1, "key2": "value2"}""",
        V3MultiEdge(
          version = version,
          id = 1,
          source = 100,
          target = 200,
          properties = Map("key1" -> 1, "key2" -> "value2")
        )
      ),
      (
        version,
        "\"id\"",
        "\"source\"",
        "\"target\"",
        """{"key1": null, "key2": {"nested": true, "innerKey": "innerValue"}}""",
        V3MultiEdge(
          version = version,
          id = "id",
          source = "source",
          target = "target",
          properties = Map("key1" -> null, "key2" -> Map("nested" -> true, "innerKey" -> "innerValue"))
        )
      )
    )

    forAll(cases) { (version, id, source, target, properties, expected) =>
      // Given
      val json =
        s"""{"version": $version, "id": $id, "source": $source, "target": $target, "properties": $properties}"""

      // When
      val edge = ActionbaseObjectMapper.scala.readValue(json, classOf[V3MultiEdge])

      // Then
      edge shouldBe expected
    }
  }
}
