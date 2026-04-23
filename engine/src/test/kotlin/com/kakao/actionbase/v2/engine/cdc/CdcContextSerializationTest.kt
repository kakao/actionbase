package com.kakao.actionbase.v2.engine.cdc

import com.kakao.actionbase.core.storage.StorageOp
import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest
import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.label.EdgeOperationStatus

import kotlin.test.assertFalse
import kotlin.test.assertTrue

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CdcContextSerializationTest {
    @BeforeAll
    fun setup() {
        CdcContext.initialize(phase = "test", tenant = "test")
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        cases = """
        - name: storageOps null is excluded
          state: none

        - name: storageOps empty is excluded
          state: empty

        - name: storageOps populated is excluded
          state: populated
        """,
    )
    fun `storageOps is always excluded from CDC JSON`(state: String) {
        val storageOps: List<StorageOp>? =
            when (state) {
                "none" -> null
                "empty" -> emptyList()
                "populated" -> listOf(StorageOp.Put(table = "t", row = "deadbeef", cells = emptyList()))
                else -> error("unknown state: $state")
            }

        val context =
            CdcContext(
                label = EntityName("db", "tbl"),
                edge = Edge(1, "src", "tgt", emptyMap<String, Any>()).toTraceEdge(),
                op = EdgeOperation.INSERT,
                status = EdgeOperationStatus.CREATED,
                before = null,
                after = null,
                acc = 0,
                alias = null,
                deferredRequests = emptyList(),
                storageOps = storageOps,
                storageOpsTruncated = true,
            )

        val (_, json) = context.toJsonString()

        assertFalse(json.contains("storageOps"), "CDC JSON must not include storageOps: $json")
        assertFalse(json.contains("storageOpsTruncated"), "CDC JSON must not include storageOpsTruncated: $json")
        assertFalse(json.contains("deadbeef"), "CDC JSON must not leak raw row bytes")
        assertTrue(json.contains("\"status\":\"CREATED\""))
        assertTrue(json.contains("\"op\":\"INSERT\""))
    }
}
