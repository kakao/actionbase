package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.core.metadata.DirectionType
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.label.EdgeOperationStatus
import com.kakao.actionbase.v2.engine.label.hbase.HBaseIndexedLabel
import com.kakao.actionbase.v2.engine.service.ddl.DdlStatus
import com.kakao.actionbase.v2.engine.service.ddl.LabelCreateRequest
import com.kakao.actionbase.v2.engine.test.GraphFixtures

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import reactor.kotlin.test.test

/**
 * Backward-compatibility guard for the two intertwined transitions on HASH labels:
 * Storage metadata removal in favor of `datastore://` URIs (#410), and HashLabel
 * removal in favor of IndexedLabel (#296 for HBase, #459 for datastore).
 *
 * | step             | what it proves                                               |
 * |------------------|--------------------------------------------------------------|
 * | create           | bare-name resolves via storages map; `datastore://` needs no Storage metadata |
 * | instanceof check | materializes as the IndexedLabel, not old HashLabel          |
 * | getTableBinding  | V3 binding gate accepts it                                   |
 * | mutate + get     | HASH label is actually usable, not just structurally routed  |
 */
@DisplayName("V2BackedEngine — HASH label table binding")
class HashLabelTableBindingTest {
    private lateinit var graph: Graph

    @BeforeEach
    fun setUp() {
        graph = GraphFixtures.create()
    }

    @AfterEach
    fun tearDown() {
        graph.close()
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - storage: storage_metadata
        - storage: datastore_uri
        """,
    )
    fun `HASH label on storage resolves a V3 table binding`(storage: String) {
        val storageUri = if (storage == "storage_metadata") GraphFixtures.hbaseStorage else GraphFixtures.datastoreStorage
        val labelName = EntityName(GraphFixtures.serviceName, "matrix_hash_$storage")
        val request =
            LabelCreateRequest(
                desc = "HASH table binding regression coverage",
                type = LabelType.HASH,
                schema = GraphFixtures.sampleSchema,
                dirType = DirectionType.OUT,
                storage = storageUri,
            )

        graph.labelDdl
            .create(labelName, request)
            .test()
            .assertNext { it.status shouldBe DdlStatus.Status.CREATED }
            .verifyComplete()

        val label = graph.getLabel(labelName)
        label.shouldBeInstanceOf<HBaseIndexedLabel>()

        val engine = V2BackedEngine(graph)
        engine.getTableBinding(GraphFixtures.serviceName, labelName.nameNotNull)

        graph
            .mutate(label.name, label, GraphFixtures.sampleEdges.map { it.toTraceEdge() }, EdgeOperation.INSERT)
            .test()
            .assertNext {
                it.result.count { r -> r.status == EdgeOperationStatus.CREATED } shouldBe GraphFixtures.sampleEdges.size
            }.verifyComplete()

        label
            .get(100, 1000, Direction.OUT, emptySet())
            .test()
            .assertNext { it.rows.size shouldBe 1 }
            .verifyComplete()
    }
}
