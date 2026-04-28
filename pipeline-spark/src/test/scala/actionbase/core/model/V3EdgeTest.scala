package actionbase.core.model

import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import org.scalatest.prop.Tables.Table
import actionbase.pipeline.testsupport.BaseTest

class V3EdgeTest extends BaseTest {
  test("V3Edge deserialization") {
    val version = System.currentTimeMillis()
    val cases = Table(
      ("version", "source", "target", "properties", "expected"),
      (
        version,
        100,
        200,
        """{"key1": 1, "key2": "value2"}""",
        V3Edge(version = version, source = 100, target = 200, properties = Map("key1" -> 1, "key2" -> "value2"))
      ),
      (
        version,
        "\"source\"",
        "\"target\"",
        """{"key1": null, "key2": {"nested": true, "innerKey": "innerValue"}}""",
        V3Edge(
          version = version,
          source = "source",
          target = "target",
          properties = Map("key1" -> null, "key2" -> Map("nested" -> true, "innerKey" -> "innerValue"))
        )
      )
    )

    forAll(cases) { (version, source, target, properties, expected) =>
      // Given
      val json = s"""{"version": $version, "source": $source, "target": $target, "properties": $properties}"""

      // When
      val edge = ActionbaseObjectMapper.scala.readValue(json, classOf[V3Edge])

      // Then
      edge shouldBe expected
    }
  }
}
