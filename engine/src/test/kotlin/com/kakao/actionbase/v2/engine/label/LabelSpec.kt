package com.kakao.actionbase.v2.engine.label

import com.kakao.actionbase.v2.core.code.EncodedKey
import com.kakao.actionbase.v2.core.edge.Edge
import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.core.metadata.DirectionType
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.core.metadata.MutationMode
import com.kakao.actionbase.v2.core.types.DataType
import com.kakao.actionbase.v2.core.types.EdgeSchema
import com.kakao.actionbase.v2.core.types.Field
import com.kakao.actionbase.v2.core.types.VertexField
import com.kakao.actionbase.v2.core.types.VertexType
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.cdc.CdcContext
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.label.hbase.HBaseIndexedLabel
import com.kakao.actionbase.v2.engine.label.metastore.JdbcHashLabel
import com.kakao.actionbase.v2.engine.metadata.StorageType
import com.kakao.actionbase.v2.engine.service.ddl.DdlStatus
import com.kakao.actionbase.v2.engine.service.ddl.LabelCreateRequest
import com.kakao.actionbase.v2.engine.sql.ScanFilter
import com.kakao.actionbase.v2.engine.sql.toRowFlux
import com.kakao.actionbase.v2.engine.test.GraphFixtures
import com.kakao.actionbase.v2.engine.test.dsl.dml
import com.kakao.actionbase.v2.engine.test.setStaleLock
import com.kakao.actionbase.v2.engine.test.toRequest
import com.kakao.actionbase.v2.engine.util.getLogger
import com.kakao.actionbase.v2.engine.wal.WalLog

import java.time.Duration

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import reactor.core.publisher.Mono
import reactor.kotlin.test.expectError
import reactor.kotlin.test.test

class LabelSpec :
    StringSpec({

        lateinit var graph: Graph

        lateinit var hbase: Label
        lateinit var jdbc: Label

        lateinit var hbaseIndexed: Label

        val testLockEdge =
            Edge(
                3000L,
                999,
                9000,
                mapOf(
                    "created_at" to 3000L,
                    "permission" to "me",
                ),
            ).toTraceEdge()

        beforeSpec {
            graph = GraphFixtures.create()

            hbase = graph.getLabel(EntityName(GraphFixtures.serviceName, GraphFixtures.hbaseHash))
            jdbc = graph.getLabel(EntityName(GraphFixtures.serviceName, GraphFixtures.jdbcHash))

            hbaseIndexed = graph.getLabel(EntityName(GraphFixtures.serviceName, GraphFixtures.hbaseIndexed))
        }

        "release lock test" {
            val rawEdge =
                Edge(
                    3000L,
                    999,
                    9000,
                    mapOf(
                        "created_at" to 3000L,
                        "permission" to "me",
                    ),
                ).toTraceEdge()

            listOf(hbase).forEach {
                val label = it as AbstractLabel<Any>
                val lockEdge = label.coder.encodeLockEdge(rawEdge, label.entity.id)
                val actual =
                    label
                        .setnxOnLock(EncodedKey(lockEdge.key), lockEdge.value)
                        .flatMap {
                            label.releaseLock(rawEdge.traceId, lockEdge)
                        }.block()

                actual shouldBe true
            }
        }

        "lock clear test" {
            listOf(hbase).forEach {
                val sut = spyk(it as AbstractLabel<Any>)
                sut.setStaleLock(testLockEdge)
                val lockEdge = sut.coder.encodeLockEdge(testLockEdge, sut.entity.id)

                sut.findStaleLockAndClear(lockEdge, 500L).block()

                verify(exactly = 1) { sut.clearLock(any(), any()) }
            }
        }

        fun testMultiSrcCount(label: Label) {
            label
                .count(setOf(100, 101), Direction.OUT)
                .toRowFlux()
                .test()
                .assertNext {
                    it["src"] shouldBe 100
                    it["COUNT(1)"] shouldBe 6L
                    it["dir"] shouldBe Direction.OUT
                }.assertNext {
                    it["src"] shouldBe 101
                    it["COUNT(1)"] shouldBe 6L
                    it["dir"] shouldBe Direction.OUT
                }.verifyComplete()
        }

        "multi src count test - hbase" {
            testMultiSrcCount(hbase)
        }

        fun testMultiSrcZeroCount(label: Label) {
            label
                .count(setOf(100, 999), Direction.OUT)
                .toRowFlux()
                .test()
                .assertNext {
                    it["src"] shouldBe 100
                    it["COUNT(1)"] shouldBe 6L
                    it["dir"] shouldBe Direction.OUT
                }.assertNext {
                    it["src"] shouldBe 999
                    it["COUNT(1)"] shouldBe 0L
                    it["dir"] shouldBe Direction.OUT
                }.verifyComplete()
        }

        "multi src zero count test - hbase" {
            testMultiSrcZeroCount(hbase)
        }

        fun testEmptySrcGet(label: Label) {
            val dfMono = label.get(999, 10000, Direction.OUT, emptySet(), graph.idEdgeEncoder)
            dfMono
                .test()
                .assertNext { it.rows.isEmpty() }
                .verifyComplete()
        }

        "empty src get test - hbase" {
            testEmptySrcGet(hbase)
        }

        "empty src get test - jdbc" {
            testEmptySrcGet(jdbc)
        }

        fun testEmptySrcScan(label: Label) {
            val scanFilter =
                ScanFilter(
                    name = label.name,
                    srcSet = setOf(789),
                    dir = Direction.OUT,
                    limit = 999,
                    offset = "100",
                    indexName = "created_at_desc",
                )
            val dfMono = label.scan(scanFilter, emptySet(), graph.idEdgeEncoder)
            dfMono
                .test()
                .assertNext { it.rows.isEmpty() }
                .verifyComplete()
        }

        "empty src scan test - jdbc" {
            testEmptySrcScan(jdbc)
        }

        fun scanIgnoreDirtyData(
            label: Label,
            insertDirtyData: (label: Label, edge: Edge) -> Unit,
        ) {
            val edge = Edge(106L, 123L, 10006L, mapOf("permission" to null, "created_at" to 106L))

            insertDirtyData(label, edge)

            val scanFilter =
                ScanFilter(
                    name = label.name,
                    srcSet = setOf(100),
                    indexName = "created_at_desc",
                )

            label
                .scan(scanFilter, emptySet(), graph.idEdgeEncoder)
                .test()
                .assertNext { it.rows.size shouldBe 6 }
                .verifyComplete()
        }

        "scan ignore dirty data - hbase indexed" {
            scanIgnoreDirtyData(hbaseIndexed) { label, edge ->
                val kfv =
                    (label as HBaseIndexedLabel).coder.encodeIndexedEdge(
                        edge.ts,
                        edge.src,
                        edge.tgt,
                        edge.props,
                        Direction.OUT,
                        label.entity.id,
                        label.indexNameToIndex["created_at_desc"],
                    )
                label.create(EncodedKey(kfv.key, kfv.field), "dirtyData".toByteArray()).block()
            }
        }

        "scan ignore dirty data - jdbc" {
            scanIgnoreDirtyData(jdbc) { label, edge ->
                val kfv = (label as JdbcHashLabel).coder.encodeHashEdgeKey(edge, label.entity.id)
                label.create(EncodedKey(kfv.key, kfv.field), "dirtyData").block()
            }
        }

        "read only label fail mutate edge" {
            graph.labelDdl
                .create(
                    EntityName(GraphFixtures.serviceName, "readOnlyLabel"),
                    LabelCreateRequest(
                        "desc",
                        LabelType.HASH,
                        EdgeSchema(
                            VertexField(VertexType.LONG),
                            VertexField(VertexType.LONG),
                            listOf(),
                        ),
                        DirectionType.OUT,
                        GraphFixtures.jdbcStorage,
                        emptyList(),
                        emptyList(),
                        event = false,
                        readOnly = true,
                    ),
                ).test()
                .assertNext { it.status shouldBe DdlStatus.Status.CREATED }
                .verifyComplete()

            graph
                .dml(EntityName(GraphFixtures.serviceName, "readOnlyLabel")) {
                    insert(100L, 1000L)
                }.test()
                .expectError(UnsupportedOperationException::class)
        }

        "single src multi tgt get - hbase" {
            val label = hbase
            val scanFilter =
                ScanFilter(
                    name = label.name,
                    srcSet = setOf(100),
                    tgt = setOf(1000, 1001),
                    indexName = "created_at_desc",
                )
            graph
                .singleStepQuery(scanFilter, emptySet())
                .test()
                .assertNext { it.rows.size shouldBe 2 }
                .verifyComplete()
        }

        "single src mulit tgt get with empty edge - hbase" {
            val label = hbase
            val scanFilter =
                ScanFilter(
                    name = label.name,
                    srcSet = setOf(100),
                    tgt = setOf(1001, 9999),
                    indexName = "created_at_desc",
                )
            graph
                .singleStepQuery(scanFilter, emptySet())
                .test()
                .assertNext { it.rows.size shouldBe 1 }
                .verifyComplete()
        }

        "single src multi tgt get" {
            val label = jdbc
            val scanFilter =
                ScanFilter(
                    name = label.name,
                    srcSet = setOf(100),
                    tgt = setOf(1000, 1001),
                    indexName = "created_at_desc",
                )
            graph
                .singleStepQuery(scanFilter, emptySet())
                .test()
                .assertNext { it.rows.size shouldBe 2 }
                .verifyComplete()
        }

        "single src mulit tgt get with empty edge" {
            val label = jdbc
            val scanFilter =
                ScanFilter(
                    name = label.name,
                    srcSet = setOf(100),
                    tgt = setOf(1001, 9999),
                    indexName = "created_at_desc",
                )
            graph
                .singleStepQuery(scanFilter, emptySet())
                .test()
                .assertNext { it.rows.size shouldBe 1 }
                .verifyComplete()
        }

        "delete with props throws exception" {
            val label = jdbc
            label
                .mutate(Edge(10, 100, 1000, mapOf("permission" to "me")).toTraceEdge(), EdgeOperation.DELETE)
                .test()
                .expectError(IllegalArgumentException::class)
        }

        "delete without props success" {
            val label = jdbc
            label
                .mutate(Edge(10, 100, 1000).toTraceEdge(), EdgeOperation.DELETE)
                .test()
                .assertNext { it.status shouldBe EdgeOperationStatus.DELETED }
                .verifyComplete()
        }

        "test recover fall-backed label" {
            val testLabel = EntityName("test", "test_label")
            val testStorageName = "storage_not_loaded_yet"

            graph.labelDdl
                .createMetadataOnlyForTest(
                    testLabel,
                    LabelCreateRequest(
                        "desc",
                        LabelType.HASH,
                        EdgeSchema(
                            VertexField(VertexType.LONG),
                            VertexField(VertexType.LONG),
                            listOf(),
                        ),
                        DirectionType.OUT,
                        testStorageName,
                        emptyList(),
                        emptyList(),
                        event = false,
                        readOnly = false,
                    ),
                ).test()
                .assertNext { it.result.first().status shouldBe EdgeOperationStatus.CREATED }
                .verifyComplete()

            graph.updateLabels().test().verifyComplete()

            graph
                .dml(testLabel) {
                    insert(100L, 1000L)
                }.test()
                .assertNext { it.status shouldBe EdgeOperationStatus.IDLE }
                .verifyComplete()

            GraphFixtures.createStorage(graph, testStorageName, StorageType.JDBC, GraphFixtures.mockStorageConf())

            graph.updateLabels().test().verifyComplete()

            graph
                .dml(testLabel) {
                    insert(100L, 1000L)
                }.test()
                .assertNext { it.status shouldBe EdgeOperationStatus.CREATED }
                .verifyComplete()
        }

        "update null on non-nullable field throws exception" {
            val schema =
                EdgeSchema(
                    VertexField(VertexType.LONG),
                    VertexField(VertexType.LONG),
                    listOf(
                        Field("a", DataType.STRING, false),
                    ),
                )

            val entity =
                LabelEntity(
                    active = true,
                    name = EntityName(GraphFixtures.serviceName, "nullable_tree_property"),
                    desc = "",
                    type = LabelType.HASH,
                    schema = schema,
                    dirType = DirectionType.OUT,
                    storage = GraphFixtures.hbaseStorage,
                )

            graph.labelDdl
                .create(entity.name, entity.toRequest())
                .test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.CREATED
                }.verifyComplete()

            val label = graph.getLabel(entity.name)

            label
                .mutate(Edge(10L, 1L, 1L, mapOf("a" to "a")).toTraceEdge(), EdgeOperation.INSERT)
                .test()
                .assertNext { it.status shouldBe EdgeOperationStatus.CREATED }
                .verifyComplete()

            label
                .mutate(Edge(11L, 1L, 1L, mapOf("a" to null)).toTraceEdge(), EdgeOperation.UPDATE)
                .test()
                .expectError(java.lang.RuntimeException::class.java)
        }

        "mutate on async label not store data" {
            graph.labelDdl
                .create(
                    EntityName("test", "async_label"),
                    LabelCreateRequest(
                        desc = "test",
                        type = LabelType.HASH,
                        schema =
                            EdgeSchema(
                                VertexField(VertexType.STRING),
                                VertexField(VertexType.STRING),
                                listOf(
                                    Field("test", DataType.STRING, true),
                                ),
                            ),
                        dirType = DirectionType.OUT,
                        storage = GraphFixtures.jdbcStorage,
                        mode = MutationMode.ASYNC,
                    ),
                ).test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.CREATED
                    ddlResult.result!!.mode shouldBe MutationMode.ASYNC
                }.verifyComplete()

            val spyWal = spyk(graph.wal)
            val walField = Graph::class.java.getDeclaredField("wal")
            walField.isAccessible = true
            walField.set(graph, spyWal)

            val logQueue = mutableListOf<WalLog>()
            every { spyWal.write(any()) } answers {
                getLogger().warn("async label mutation is not supported yet")
                Mono
                    .fromCallable {
                        logQueue.add(firstArg())
                    }.then(Mono.defer { Mono.empty() })
            }

            verify(exactly = 0) { spyWal.write(any()) }

            val entityName = EntityName("test", "async_label")

            graph
                .mutate(
                    entityName,
                    graph.getLabel(entityName),
                    listOf(Edge(10, 1, 1, mapOf("test" to "test")).toTraceEdge()),
                    EdgeOperation.INSERT,
                    mode = MutationMode.ASYNC,
                ).test()
                .assertNext { it.result[0].status shouldBe EdgeOperationStatus.QUEUED }
                .verifyComplete()

            graph
                .getLabel(EntityName("test", "async_label"))
                .get("a", "b", Direction.OUT, emptySet(), graph.idEdgeEncoder)
                .test()
                .assertNext { it.rows.isEmpty() }
                .verifyComplete()

            verify(exactly = 1) { spyWal.write(any()) }
            logQueue[0].mode.queue shouldBe true
        }

        "mutate on async label with queued param store data" {
            graph.labelDdl
                .create(
                    EntityName("test", "async_label2"),
                    LabelCreateRequest(
                        desc = "test",
                        type = LabelType.HASH,
                        schema =
                            EdgeSchema(
                                VertexField(VertexType.STRING),
                                VertexField(VertexType.STRING),
                                listOf(
                                    Field("test", DataType.STRING, true),
                                ),
                            ),
                        dirType = DirectionType.OUT,
                        storage = GraphFixtures.jdbcStorage,
                        mode = MutationMode.ASYNC,
                    ),
                ).test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.CREATED
                    ddlResult.result!!.mode shouldBe MutationMode.ASYNC
                }.verifyComplete()

            val entityName = EntityName("test", "async_label2")

            val spyWal = spyk(graph.wal)
            val walField = Graph::class.java.getDeclaredField("wal")
            walField.isAccessible = true
            walField.set(graph, spyWal)

            val logQueue = mutableListOf<WalLog>()
            every { spyWal.write(any()) } answers {
                getLogger().warn("async label mutation is not supported yet")
                Mono
                    .fromCallable {
                        logQueue.add(firstArg())
                    }.then(Mono.defer { Mono.empty() })
            }

            graph
                .mutate(
                    entityName,
                    graph.getLabel(entityName),
                    listOf(Edge(10, 1, 950322, mapOf("test" to "test")).toTraceEdge()),
                    EdgeOperation.INSERT,
                    mode = MutationMode.SYNC,
                ).test()
                .assertNext { it.result[0].status shouldBe EdgeOperationStatus.CREATED }
                .verifyComplete()

            logQueue[0].mode.queue shouldBe false
        }

        "check wal log of mutation on sync label" {
            graph.labelDdl
                .create(
                    EntityName("test", "label3"),
                    LabelCreateRequest(
                        desc = "test",
                        type = LabelType.HASH,
                        schema =
                            EdgeSchema(
                                VertexField(VertexType.STRING),
                                VertexField(VertexType.STRING),
                                listOf(
                                    Field("test", DataType.STRING, true),
                                ),
                            ),
                        dirType = DirectionType.OUT,
                        storage = GraphFixtures.jdbcStorage,
                    ),
                ).test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.CREATED
                    ddlResult.result!!.mode shouldBe MutationMode.SYNC
                }.verifyComplete()

            val entityName = EntityName("test", "label3")

            val spyWal = spyk(graph.wal)
            val walField = Graph::class.java.getDeclaredField("wal")
            walField.isAccessible = true
            walField.set(graph, spyWal)

            val logQueue = mutableListOf<WalLog>()
            every { spyWal.write(any()) } answers {
                Mono
                    .fromCallable {
                        logQueue.add(firstArg())
                    }.then(Mono.defer { Mono.empty() })
            }

            graph
                .mutate(
                    entityName,
                    graph.getLabel(entityName),
                    listOf(Edge(10, 1, 950322, mapOf("test" to "test")).toTraceEdge()),
                    EdgeOperation.INSERT,
                ).test()
                .assertNext { it.result[0].status shouldBe EdgeOperationStatus.CREATED }
                .verifyComplete()

            logQueue[0].mode.queue shouldBe false
        }

        "check cdc log of mutation error" {
            graph.labelDdl
                .create(
                    EntityName("test", "label4"),
                    LabelCreateRequest(
                        desc = "test",
                        type = LabelType.HASH,
                        schema =
                            EdgeSchema(
                                VertexField(VertexType.STRING),
                                VertexField(VertexType.STRING),
                                listOf(
                                    Field("test", DataType.STRING, false),
                                ),
                            ),
                        dirType = DirectionType.OUT,
                        storage = GraphFixtures.hbaseStorage,
                    ),
                ).test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.CREATED
                }.verifyComplete()

            val entityName = EntityName("test", "label4")

            val spyCdc = spyk(graph.cdc)
            val cdcField = Graph::class.java.getDeclaredField("cdc")
            cdcField.isAccessible = true
            cdcField.set(graph, spyCdc)

            val logQueue = mutableListOf<CdcContext>()
            every { spyCdc.write(any()) } answers {
                Mono
                    .fromCallable {
                        logQueue.add(firstArg())
                    }.then(Mono.defer { Mono.empty() })
            }

            val requestId = "test_request_id"

            graph
                .mutate(
                    entityName,
                    graph.getLabel(entityName),
                    listOf(Edge(10, 1, 950322).toTraceEdge()),
                    EdgeOperation.INSERT,
                    requestId = requestId,
                )
                // Wait for cdc subscribe
                .delayElement(Duration.ofSeconds(1))
                .test()
                .assertNext { it.result[0].status shouldBe EdgeOperationStatus.ERROR }
                .verifyComplete()

            logQueue[0].requestId shouldBe requestId
        }
    })
