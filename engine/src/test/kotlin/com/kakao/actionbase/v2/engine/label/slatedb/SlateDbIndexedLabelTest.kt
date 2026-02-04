package com.kakao.actionbase.v2.engine.label.slatedb

import com.kakao.actionbase.v2.core.code.Index
import com.kakao.actionbase.v2.core.code.hbase.Order
import com.kakao.actionbase.v2.core.metadata.DirectionType
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.core.types.DataType
import com.kakao.actionbase.v2.core.types.EdgeSchema
import com.kakao.actionbase.v2.core.types.Field
import com.kakao.actionbase.v2.core.types.VertexField
import com.kakao.actionbase.v2.core.types.VertexType
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.GraphConfig
import com.kakao.actionbase.v2.engine.client.kafka.impl.DefaultKafkaClientFactory
import com.kakao.actionbase.v2.engine.client.web.impl.DefaultWebClientFactory
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.label.EdgeOperationStatus
import com.kakao.actionbase.v2.engine.metadata.StorageType
import com.kakao.actionbase.v2.engine.service.ddl.DdlStatus
import com.kakao.actionbase.v2.engine.service.ddl.ServiceCreateRequest
import com.kakao.actionbase.v2.engine.service.ddl.StorageCreateRequest
import com.kakao.actionbase.v2.engine.test.cdc.InMemoryCdcFactory
import com.kakao.actionbase.v2.engine.test.wal.InMemoryWalFactory

import java.nio.file.Path
import java.util.UUID

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

import io.kotest.matchers.shouldBe
import reactor.kotlin.test.test

class SlateDbIndexedLabelTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var graph: Graph
    private val serviceName = "slatedb_indexed_test_service"
    private val storageName = "slatedb_indexed_storage"
    private val labelName = "slatedb_indexed_label"

    private fun findLibraryPath(): Path {
        var dir = Path.of(System.getProperty("user.dir"))
        while (!dir.resolve("settings.gradle.kts").toFile().exists() && dir.parent != null) {
            dir = dir.parent
        }
        return dir.resolve("native/lib/libslatedb_c.dylib")
    }

    private fun createGraph(): Graph {
        val config =
            GraphConfig
                .Builder()
                .withMetastoreUrl("jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=MYSQL")
                .build()
        return Graph.create(config, InMemoryWalFactory, InMemoryCdcFactory, DefaultKafkaClientFactory, DefaultWebClientFactory)
    }

    @BeforeEach
    fun setUp() {
        // Skip test if native library not found
        assumeTrue(findLibraryPath().toFile().exists(), "SlateDB native library not found")

        graph = createGraph()
        graph.updateAllMetadata().block()

        // Create service
        graph.serviceDdl
            .create(EntityName.fromOrigin(serviceName), ServiceCreateRequest(desc = "test service"))
            .test()
            .assertNext { it.status shouldBe DdlStatus.Status.CREATED }
            .verifyComplete()

        // Create SlateDB storage
        val conf =
            jacksonObjectMapper().createObjectNode().apply {
                put("path", "test-data")
                put("url", "file://${tempDir.toAbsolutePath()}")
                put("libraryPath", findLibraryPath().toString())
            }

        graph.storageDdl
            .create(EntityName.fromOrigin(storageName), StorageCreateRequest(desc = "slatedb indexed storage", type = StorageType.SLATEDB, conf = conf))
            .test()
            .assertNext { it.status shouldBe DdlStatus.Status.CREATED }
            .verifyComplete()

        // Create INDEXED label with SlateDB storage
        val labelEntity =
            LabelEntity(
                active = true,
                name = EntityName(serviceName, labelName),
                desc = "test slatedb indexed label",
                type = LabelType.INDEXED,
                schema =
                    EdgeSchema(
                        VertexField(VertexType.LONG),
                        VertexField(VertexType.LONG),
                        listOf(
                            Field("score", DataType.LONG, false),
                            Field("memo", DataType.STRING, true),
                        ),
                    ),
                dirType = DirectionType.OUT,
                storage = storageName,
                indices =
                    listOf(
                        Index(
                            "score_desc",
                            listOf(Index.Field("score", Order.DESC)),
                            "Score descending index",
                        ),
                    ),
            )

        graph.labelDdl
            .create(labelEntity.name, labelEntity.toCreateRequest())
            .test()
            .assertNext { it.status shouldBe DdlStatus.Status.CREATED }
            .verifyComplete()

        graph.updateAllMetadata().block()
    }

    @AfterEach
    fun tearDown() {
        graph.close()
    }

    @Test
    fun `insert and get edge with index`() {
        val label = graph.getLabel(EntityName(serviceName, labelName))
        val edge =
            com.kakao.actionbase.v2.core.edge.Edge(
                System.currentTimeMillis(),
                100L,
                200L,
                mapOf("score" to 42L, "memo" to "hello"),
            )

        // Insert
        label
            .mutate(edge.toTraceEdge(), EdgeOperation.INSERT)
            .test()
            .assertNext { context ->
                context.status shouldBe EdgeOperationStatus.CREATED
            }.verifyComplete()

        // Get
        graph
            .queryGet(
                EntityName(serviceName, labelName),
                100L,
                200L,
            ).test()
            .assertNext { df ->
                df.rows.size shouldBe 1
                val row = df.toRowWithSchema().first()
                row.getLong("tgt") shouldBe 200L
                row.getLong("score") shouldBe 42L
            }.verifyComplete()
    }

    @Test
    fun `insert multiple edges and verify index order`() {
        val label = graph.getLabel(EntityName(serviceName, labelName))
        val ts = System.currentTimeMillis()

        // Insert edges with different scores
        val edges =
            listOf(
                com.kakao.actionbase.v2.core.edge
                    .Edge(ts, 100L, 201L, mapOf("score" to 10L, "memo" to "low")),
                com.kakao.actionbase.v2.core.edge
                    .Edge(ts + 1, 100L, 202L, mapOf("score" to 50L, "memo" to "medium")),
                com.kakao.actionbase.v2.core.edge
                    .Edge(ts + 2, 100L, 203L, mapOf("score" to 100L, "memo" to "high")),
            )

        edges.forEach { edge ->
            label.mutate(edge.toTraceEdge(), EdgeOperation.INSERT).block()
        }

        // Verify all edges exist
        edges.forEach { edge ->
            graph
                .queryGet(
                    EntityName(serviceName, labelName),
                    edge.src,
                    edge.tgt,
                ).test()
                .assertNext { df ->
                    df.rows.size shouldBe 1
                }.verifyComplete()
        }
    }

    @Test
    fun `delete edge removes from index`() {
        val label = graph.getLabel(EntityName(serviceName, labelName))
        val edge =
            com.kakao.actionbase.v2.core.edge.Edge(
                System.currentTimeMillis(),
                101L,
                201L,
                mapOf("score" to 100L),
            )

        // Insert
        label.mutate(edge.toTraceEdge(), EdgeOperation.INSERT).block()

        // Verify exists
        graph
            .queryGet(
                EntityName(serviceName, labelName),
                101L,
                201L,
            ).test()
            .assertNext { df ->
                df.rows.size shouldBe 1
            }.verifyComplete()

        // Delete
        label
            .mutate(edge.toTraceEdge(), EdgeOperation.DELETE)
            .test()
            .assertNext { context ->
                context.status shouldBe EdgeOperationStatus.DELETED
            }.verifyComplete()

        // Verify deleted
        graph
            .queryGet(
                EntityName(serviceName, labelName),
                101L,
                201L,
            ).test()
            .assertNext { df ->
                df.rows.size shouldBe 0
            }.verifyComplete()
    }

    @Test
    fun `update edge updates index`() {
        val label = graph.getLabel(EntityName(serviceName, labelName))
        val ts = System.currentTimeMillis()

        val edge1 =
            com.kakao.actionbase.v2.core.edge.Edge(
                ts,
                102L,
                202L,
                mapOf("score" to 50L, "memo" to "original"),
            )

        val edge2 =
            com.kakao.actionbase.v2.core.edge.Edge(
                ts + 1,
                102L,
                202L,
                mapOf("score" to 100L, "memo" to "updated"),
            )

        // Insert
        label.mutate(edge1.toTraceEdge(), EdgeOperation.INSERT).block()

        // Update
        label
            .mutate(edge2.toTraceEdge(), EdgeOperation.INSERT)
            .test()
            .assertNext { context ->
                context.status shouldBe EdgeOperationStatus.UPDATED
            }.verifyComplete()

        // Verify updated
        graph
            .queryGet(
                EntityName(serviceName, labelName),
                102L,
                202L,
            ).test()
            .assertNext { df ->
                df.rows.size shouldBe 1
                val row = df.toRowWithSchema().first()
                row.getLong("score") shouldBe 100L
                row.getString("memo") shouldBe "updated"
            }.verifyComplete()
    }
}
