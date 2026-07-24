package com.kakao.actionbase.server.api.graph.v3.metadata

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

// TODO(topk-port): this is the old score-model query e2e. Rewrite against the new
//   rank-table query API once `AggregationQueryService.topk` is ported.
//   The original suite is preserved on `feature/per-entity-top-k-query`.
@Disabled("topk query API pending port to the new rank-table model")
class MetadataAggQueryControllerE2ETest {
    @Test
    fun placeholder() {
    }
}
