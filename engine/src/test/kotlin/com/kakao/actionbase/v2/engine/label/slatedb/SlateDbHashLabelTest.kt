package com.kakao.actionbase.v2.engine.label.slatedb

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

class SlateDbHashLabelTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var graph: Graph
    private val serviceName = "slatedb_test_service"
    private val storageName = "slatedb_storage"
    private val labelName = "slatedb_label"

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
            .create(EntityName.fromOrigin(storageName), StorageCreateRequest(desc = "slatedb storage", type = StorageType.SLATEDB, conf = conf))
            .test()
            .assertNext { it.status shouldBe DdlStatus.Status.CREATED }
            .verifyComplete()

        // Create label with SlateDB storage
        val labelEntity =
            LabelEntity(
                active = true,
                name = EntityName(serviceName, labelName),
                desc = "test slatedb label",
                type = LabelType.HASH,
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
    fun `insert and get edge`() {
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
                // tgt is at index 1 in the schema (src, tgt, ts, ...)
                val row = df.toRowWithSchema().first()
                row.getLong("tgt") shouldBe 200L
            }.verifyComplete()
    }

    @Test
    fun `delete edge`() {
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

        // Delete
        label
            .mutate(edge.toTraceEdge(), EdgeOperation.DELETE)
            .test()
            .assertNext { context ->
                context.status shouldBe EdgeOperationStatus.DELETED
            }.verifyComplete()

        // Verify deleted (should return empty)
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
    fun `update edge`() {
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
                row.getString("memo") shouldBe "updated"
            }.verifyComplete()
    }
}
