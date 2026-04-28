package actionbase.core.model

import actionbase.pipeline.testsupport.{BaseTest, TestUtil}

class V3EdgeMutationTest extends BaseTest {
  test("wal containing JSON arrays converts to V3EdgeMutation preserving scala List properties") {
    // Given
    val json = TestUtil.read("actionbase/core/model/wal_json_array_1.json")
    val log  = AbWal.parse(json).get

    // When
    val edgeEvent = V3EdgeMutation.of(log)
    val edge      = edgeEvent.edge

    // Then
    val jsonArrayFields = Seq(
      "matchedIdList",
      "listA",
      "listB",
      "topListA",
      "topListB",
      "topListC"
    )
    jsonArrayFields.foreach { fieldName =>
      noException should be thrownBy {
        edge.properties(fieldName).asInstanceOf[List[_]]
      }
    }
  }
}
