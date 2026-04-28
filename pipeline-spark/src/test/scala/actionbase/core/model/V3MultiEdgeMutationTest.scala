package actionbase.core.model

import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import actionbase.pipeline.testsupport.BaseTest

class V3MultiEdgeMutationTest extends BaseTest {
  // Synthetic identifiers: fixed placeholder timestamps, sequential IDs in 10xx/20xx/30xx
  // ranges so they can't be mistaken for production values.
  private val multiEdgeWalJson =
    """{"alias":"example.example_sent_v1","label":"example.example_sent_v1_20260101_000000","edge":{"ts":1700000000000000000,"src":1001,"tgt":1002,"props":{"idA":2001,"tsA":1700000000000,"count":1,"amount":9400,"status":601,"tsB":1700000000000,"kind":"others","flagA":"Y","flagB":"1","idB":2002,"labelC":"103107114100100","idC":2003,"flagC":true,"flagD":true,"_id":3001},"traceId":"01TESTTRACE000000000000000"},"op":"INSERT","mode":{"l":"SYNC","r":"SYNC","queue":false},"audit":{"actor":"ExampleSentV1Loader"},"requestId":"00000000000000000000000000000000","ts":1700000000000,"tenant":"stg","phase":"stg","version":"2"}"""

  test("of should convert AbWal to V3MultiEdgeMutation") {
    // Given
    val wal = AbWal.parse(multiEdgeWalJson).get

    // When
    val mutation = V3MultiEdgeMutation.of(wal)

    // Then
    mutation.`type` shouldBe EdgeOperation.INSERT
    mutation.edge.version shouldBe 1700000000000000000L
    mutation.edge.id shouldBe 3001L
    mutation.edge.source shouldBe 1001
    mutation.edge.target shouldBe 1002
    mutation.edge.properties.get("_id") shouldBe Some(3001L)
  }
}
