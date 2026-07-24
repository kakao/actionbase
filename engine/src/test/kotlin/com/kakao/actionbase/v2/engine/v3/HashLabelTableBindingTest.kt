package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.engine.metadata.MutationMode as EngineMutationMode

import com.kakao.actionbase.core.edge.payload.EdgeBulkMutationRequest
import com.kakao.actionbase.engine.service.MutationService
import com.kakao.actionbase.engine.service.QueryService
import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest
import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.metadata.DirectionType
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.label.EdgeOperationStatus
import com.kakao.actionbase.v2.engine.label.hbase.HBaseHashLabel
import com.kakao.actionbase.v2.engine.label.hbase.HBaseIndexedLabel
import com.kakao.actionbase.v2.engine.service.ddl.DdlStatus
import com.kakao.actionbase.v2.engine.service.ddl.LabelCreateRequest
import com.kakao.actionbase.v2.engine.test.GraphFixtures

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import reactor.kotlin.test.test

/**
 * Backward-compatibility guard for the two intertwined transitions on HASH labels:
 * Storage metadata removal in favor of `datastore://` URIs (#410), and HashLabel
 * removal in favor of IndexedLabel (#296 for HBase, #459 for datastore).
 *
 * | step                   | what it proves                                                |
 * |------------------------|---------------------------------------------------------------|
 * | create                 | bare-name resolves via storages map; `datastore://` needs no Storage metadata |
 * | instanceof check       | materializes as the IndexedLabel, not old HashLabel           |
 * | V3 mutate + gets       | V3 API resolves the table binding and round-trips             |
 * | legacy write → V3 read | rows written by the removed DatastoreHashLabel stay readable  |
 */
@DisplayName("V2BackedEngine — HASH label table binding")
class HashLabelTableBindingTest {
    private lateinit var graph: Graph
    private lateinit var mutationService: MutationService
    private lateinit var queryService: QueryService

    @BeforeEach
    fun setUp() {
        graph = GraphFixtures.create()
        val engine = V2BackedEngine(graph)
        mutationService = MutationService(engine)
        queryService = QueryService(engine)
    }

    @AfterEach
    fun tearDown() {
        graph.close()
    }

    private val database = GraphFixtures.serviceName

    private val mutationRequest =
        """
        {
          "mutations": [
            {"type": "INSERT", "edge": {"version": 10, "source": 100, "target": 1000, "properties": {"createdAt": 10, "permission": "na"}}},
            {"type": "INSERT", "edge": {"version": 11, "source": 100, "target": 1001, "properties": {"createdAt": 11, "permission": "others"}}}
          ]
        }
        """.trimIndent()

    private fun createHashLabel(
        labelName: EntityName,
        storageUri: String,
    ) {
        val request =
            LabelCreateRequest(
                desc = "HASH table binding compatibility coverage",
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
    }

    private fun mutateV3(table: String) {
        val request = mapper.readValue<EdgeBulkMutationRequest>(mutationRequest)
        mutationService
            .mutate(database, table, request.mutations, syncMode = EngineMutationMode.SYNC)
            .test()
            .assertNext { results ->
                results.size shouldBe 2
                results.all { it.status == "CREATED" } shouldBe true
            }.verifyComplete()
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - storage: storage_metadata
        - storage: datastore_uri
        """,
    )
    fun `HASH label mutates and queries through the V3 API`(storage: String) {
        val storageUri = if (storage == "storage_metadata") GraphFixtures.hbaseStorage else GraphFixtures.datastoreStorage
        val labelName = EntityName(database, "matrix_hash_$storage")
        createHashLabel(labelName, storageUri)

        graph.getLabel(labelName).shouldBeInstanceOf<HBaseIndexedLabel>()

        mutateV3(labelName.nameNotNull)

        queryService
            .gets(database, labelName.nameNotNull, listOf(100L), listOf(1000L, 1001L))
            .test()
            .assertNext { result ->
                result.count shouldBe 2
                result.edges.map { it.target } shouldBe listOf(1000L, 1001L)
            }.verifyComplete()
    }

    @Test
    fun `V3 API reads and extends rows written by the removed DatastoreHashLabel`() {
        val storageUri = GraphFixtures.datastoreStorage
        val labelName = EntityName(database, "legacy_hash_datastore")
        createHashLabel(labelName, storageUri)

        // Same shape as the removed DatastoreHashLabel: HBaseHashLabel base, byte-array
        // key-value coder, table resolved from the datastore URI.
        val legacyEntity =
            LabelEntity(
                active = true,
                name = labelName,
                desc = "legacy writer",
                type = LabelType.HASH,
                schema = GraphFixtures.sampleSchema,
                dirType = DirectionType.OUT,
                storage = storageUri,
            )
        val legacyLabel =
            HBaseHashLabel(
                entity = legacyEntity,
                coder = graph.edgeEncoderFactory.bytesKeyValueEncoder,
                tables = graph.datastore.getTable(storageUri).cache(),
            )

        val legacyEdge = Edge(12L, 100L, 1002L, mapOf("createdAt" to 12L, "permission" to "me"))
        graph
            .mutate(labelName, legacyLabel, listOf(legacyEdge.toTraceEdge()), EdgeOperation.INSERT)
            .test()
            .assertNext { it.result.single().status shouldBe EdgeOperationStatus.CREATED }
            .verifyComplete()

        queryService
            .gets(database, labelName.nameNotNull, listOf(legacyEdge.src), listOf(legacyEdge.tgt))
            .test()
            .assertNext { result ->
                result.count shouldBe 1
                result.edges.single().properties["permission"] shouldBe legacyEdge.props["permission"]
            }.verifyComplete()

        mutateV3(labelName.nameNotNull)

        queryService
            .gets(database, labelName.nameNotNull, listOf(100L), listOf(1000L, 1001L, 1002L))
            .test()
            .assertNext { result -> result.count shouldBe 3 }
            .verifyComplete()
    }

    companion object {
        private val mapper = jacksonObjectMapper()
    }
}
