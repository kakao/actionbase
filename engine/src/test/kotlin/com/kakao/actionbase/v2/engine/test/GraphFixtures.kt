package com.kakao.actionbase.v2.engine.test

import com.kakao.actionbase.v2.core.code.DecodedEdge
import com.kakao.actionbase.v2.core.code.EncodedKey
import com.kakao.actionbase.v2.core.code.Index
import com.kakao.actionbase.v2.core.code.KeyValue
import com.kakao.actionbase.v2.core.code.hbase.Order
import com.kakao.actionbase.v2.core.edge.Edge
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
import com.kakao.actionbase.v2.engine.label.AbstractLabel
import com.kakao.actionbase.v2.engine.label.EdgeOperationStatus
import com.kakao.actionbase.v2.engine.metadata.Metadata
import com.kakao.actionbase.v2.engine.metadata.StorageType
import com.kakao.actionbase.v2.engine.service.ddl.DdlStatus
import com.kakao.actionbase.v2.engine.service.ddl.LabelCreateRequest
import com.kakao.actionbase.v2.engine.service.ddl.ServiceCreateRequest
import com.kakao.actionbase.v2.engine.service.ddl.StorageCreateRequest
import com.kakao.actionbase.v2.engine.storage.jdbc.MetadataTable
import com.kakao.actionbase.v2.engine.test.cdc.InMemoryCdcFactory
import com.kakao.actionbase.v2.engine.test.wal.InMemoryWalFactory
import com.kakao.actionbase.v2.engine.util.getLogger

import java.util.UUID

import kotlin.math.absoluteValue
import kotlin.random.Random

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import reactor.blockhound.BlockHound
import reactor.core.publisher.Flux
import reactor.kotlin.core.publisher.toFlux
import reactor.kotlin.test.test

@Suppress("PropertyName")
object GraphFixtures {
    private val logger = getLogger()

    const val serviceName = "test"

    const val jdbcStorage = "mock_jdbc"

    const val hbaseStorage = "mock_hbase"

    const val jdbcHash = "jdbc_hash"

    const val hbaseHash = "hbase_hash"

    const val hbaseIndexed = "hbase_indexed"

    const val index1 = "permission_created_at_desc"

    const val index2 = "created_at_desc"

    const val index3 = "created_at_asc"

    val datastoreStorage: String
        get() = "datastore://namespace_${Random.nextLong().absoluteValue}/table_${Random.nextLong().absoluteValue}"

    fun mockStorageConf() =
        jacksonObjectMapper().createObjectNode().apply {
            put("mock", true)
            put("namespace", "namespace_${Random.nextLong()}") // for hbase
            put("url", "mock_url_${Random.nextLong()}") // for jdbc
        }

    val sampleSchema =
        EdgeSchema(
            VertexField(VertexType.LONG),
            VertexField(VertexType.LONG),
            listOf(
                Field("createdAt", DataType.LONG, false),
                Field("permission", DataType.STRING, true),
                Field("receivedFrom", DataType.STRING, true),
            ),
        )

    val sampleIndices =
        listOf(
            Index(
                index1,
                listOf(
                    Index.Field("permission", Order.ASC),
                    Index.Field("createdAt", Order.DESC),
                ),
            ),
            Index(
                index2,
                listOf(
                    Index.Field("createdAt", Order.DESC),
                ),
            ),
            Index(
                index3,
                listOf(
                    Index.Field("createdAt", Order.ASC),
                ),
            ),
        )

    val sampleHashLabel =
        LabelEntity(
            active = true,
            name = EntityName("test", "like_hash"),
            desc = "test like (hash)",
            type = LabelType.HASH,
            schema = sampleSchema,
            dirType = DirectionType.OUT,
            storage = "mock",
        )

    val sampleEdges =
        listOf(
            Edge(10, 100, 1000, mapOf("permission" to "na", "createdAt" to 10)),
            Edge(11, 100, 1001, mapOf("permission" to "others", "createdAt" to 11)),
            Edge(12, 100, 1002, mapOf("permission" to "me", "createdAt" to 12)),
            Edge(13, 100, 1003, mapOf("permission" to "others", "createdAt" to 13)),
            Edge(14, 100, 1004, mapOf("permission" to "me", "createdAt" to 14)),
            Edge(15, 100, 1005, mapOf("permission" to "na", "createdAt" to 15)),
            Edge(16, 101, 1000, mapOf("permission" to "na", "createdAt" to 16)),
            Edge(17, 101, 1001, mapOf("permission" to "others", "createdAt" to 17)),
            Edge(18, 101, 1002, mapOf("permission" to "me", "createdAt" to 18)),
            Edge(19, 101, 1003, mapOf("permission" to "others", "createdAt" to 19)),
            Edge(20, 101, 1004, mapOf("permission" to "me", "createdAt" to 20)),
            Edge(21, 101, 1005, mapOf("permission" to "na", "createdAt" to 21)),
        )

    private fun createService(
        graph: Graph,
        name: String,
    ) {
        logger.debug("Creating $name service")
        val request =
            ServiceCreateRequest(
                desc = "$name service",
            )

        graph.serviceDdl
            .create(EntityName.fromOrigin(name), request)
            .test()
            .assertNext { ddlResult ->
                ddlResult.status shouldBe DdlStatus.Status.CREATED
            }.verifyComplete()
    }

    fun createStorage(
        graph: Graph,
        name: String,
        type: StorageType,
        conf: JsonNode,
    ) {
        logger.debug("Creating $name storage")
        val request =
            StorageCreateRequest(
                desc = "mock $type storage",
                type = type,
                conf = conf,
            )
        graph.storageDdl
            .create(EntityName.fromOrigin(name), request)
            .test()
            .assertNext { ddlResult ->
                ddlResult.status shouldBe DdlStatus.Status.CREATED
            }.verifyComplete()
    }

    private fun performSampleDDLAndDML(
        graph: Graph,
        service: String,
        name: String,
        type: LabelType,
        storageName: String,
    ) {
        val entity =
            LabelEntity(
                active = true,
                name = EntityName(service, name),
                desc = "$service.$name label",
                type = type,
                schema = sampleSchema,
                dirType = if (type == LabelType.INDEXED) DirectionType.BOTH else DirectionType.OUT,
                storage = storageName,
                indices = if (type == LabelType.INDEXED) sampleIndices else emptyList(),
            )

        graph.labelDdl
            .create(entity.name, entity.toRequest())
            .test()
            .assertNext { ddlResult ->
                ddlResult.status shouldBe DdlStatus.Status.CREATED
            }.verifyComplete()

        val label = graph.getLabel(entity.name)
        graph
            .mutate(label.name, label, sampleEdges.map { it.toTraceEdge() }, EdgeOperation.INSERT)
            .test()
            .assertNext {
                it.result.count { result -> result.status == EdgeOperationStatus.CREATED } shouldBe sampleEdges.size
            }.verifyComplete()
    }

    fun create(withTestData: Boolean = true): Graph {
        BlockHound
            .builder()
            .allowBlockingCallsInside("org.apache.hadoop.hbase.client.mock.MockHTable", "mutateRow")
            .install()

        val config =
            GraphConfig
                .Builder()
                .withMetastoreUrl("jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1;MODE=MYSQL")
                .build()

        val graph = Graph.create(config, InMemoryWalFactory, InMemoryCdcFactory, DefaultKafkaClientFactory, DefaultWebClientFactory)

        if (withTestData) {
            createService(graph, serviceName)

            createStorage(graph, jdbcStorage, StorageType.JDBC, mockStorageConf())
            createStorage(graph, hbaseStorage, StorageType.HBASE, mockStorageConf())

            performSampleDDLAndDML(graph, serviceName, jdbcHash, LabelType.HASH, jdbcStorage)

            performSampleDDLAndDML(graph, serviceName, hbaseHash, LabelType.HASH, hbaseStorage)
            performSampleDDLAndDML(graph, serviceName, hbaseIndexed, LabelType.INDEXED, datastoreStorage)
        }
        graph.updateAllMetadata().test().verifyComplete()
        return graph
    }

    val defaultServices =
        listOf(
            EntityName.fromOrigin(Metadata.sysServiceName),
        )

    val defaultStorages =
        listOf(
            EntityName.fromOrigin(Metadata.localOnlyStorageName),
            EntityName.fromOrigin(Metadata.localBackedMetastoreName),
            EntityName.fromOrigin(Metadata.metastoreName),
        )

    val defaultLabels =
        listOf(
            Metadata.serviceLabelEntity.name,
            Metadata.storageLabelEntity.name,
            Metadata.labelLabelEntity.name,
            Metadata.infoLabelEntity.name,
            Metadata.queryLabelEntity.name,
            Metadata.aliasLabelEntity.name,
            Metadata.onlineMetadataLabelV2Entity.name,
            Metadata.sysNilLabelEntity.name,
        )

    val defaultMetadata =
        listOf(
            // "origin" -> "service"
            listOf(EntityName.withPhase(Metadata.origin, Metadata.sysServiceName), Metadata.serviceLabelEntity.id, null),
            // "origin" -> storage
            listOf(
                EntityName.withPhase(Metadata.origin, Metadata.localOnlyStorageName),
                Metadata.storageLabelEntity.id,
                null,
            ),
            listOf(
                EntityName.withPhase(Metadata.origin, Metadata.localBackedMetastoreName),
                Metadata.storageLabelEntity.id,
                null,
            ),
            listOf(EntityName.withPhase(Metadata.origin, Metadata.metastoreName), Metadata.storageLabelEntity.id, null),
            // (service -> label) edges for label label (includes label information)
            listOf(
                EntityName.withPhase(Metadata.sysServiceName, Metadata.sysServiceLabelName),
                Metadata.labelLabelEntity.id,
                null,
            ),
            listOf(
                EntityName.withPhase(Metadata.sysServiceName, Metadata.sysStorageLabelName),
                Metadata.labelLabelEntity.id,
                null,
            ),
            listOf(
                EntityName.withPhase(Metadata.sysServiceName, Metadata.sysLabelLabelName),
                Metadata.labelLabelEntity.id,
                null,
            ),
            listOf(
                EntityName.withPhase(Metadata.sysServiceName, Metadata.sysInfoLabelName),
                Metadata.labelLabelEntity.id,
                null,
            ),
            listOf(
                EntityName.withPhase(Metadata.sysServiceName, Metadata.sysQueryLabelName),
                Metadata.labelLabelEntity.id,
                null,
            ),
            listOf(
                EntityName.withPhase(Metadata.sysServiceName, Metadata.sysAliasLabelName),
                Metadata.labelLabelEntity.id,
                null,
            ),
            listOf(
                EntityName.withPhase(Metadata.sysServiceName, Metadata.sysOnlineMetadataLabelV2Name),
                Metadata.labelLabelEntity.id,
                null,
            ),
            listOf(
                EntityName.withPhase(Metadata.sysServiceName, Metadata.sysNilLabelName),
                Metadata.labelLabelEntity.id,
                null,
            ),
        )
}

fun <T> AbstractLabel<T>.setStaleLock(edge: Edge) {
    val lockEdge = coder.encodeLockEdge(edge, entity.id)
    setnxOnLock(EncodedKey(lockEdge.key), coder.encodeLockEdgeValue(0L)).block()
}

val Graph.testFixtures: GraphTestFixtures
    get() = GraphTestFixtures(this)

class GraphTestFixtures(
    private val graph: Graph,
) {
    fun createLabel(name: EntityName) {
        val request =
            LabelCreateRequest(
                desc = "test",
                type = LabelType.HASH,
                schema =
                    EdgeSchema(
                        VertexField(VertexType.STRING),
                        VertexField(VertexType.STRING),
                        listOf(
                            Field("permission", DataType.STRING, false),
                            Field("memo", DataType.STRING, true),
                            Field("json", DataType.JSON, true),
                        ),
                    ),
                dirType = DirectionType.OUT,
                indices = emptyList(),
                storage = Metadata.metastoreName,
            )

        graph.labelDdl
            .create(name, request)
            .test()
            .assertNext { ddlResult ->
                ddlResult.status shouldBe DdlStatus.Status.CREATED
            }.verifyComplete()
    }

    fun createService(name: EntityName) {
        val request =
            ServiceCreateRequest(
                desc = "test",
            )
        graph.serviceDdl
            .create(name, request)
            .test()
            .assertNext { ddlResult ->
                ddlResult.status shouldBe DdlStatus.Status.CREATED
            }.verifyComplete()
    }

    fun createStorage(
        name: EntityName,
        type: StorageType = StorageType.NIL,
    ) {
        val conf = jacksonObjectMapper().createObjectNode()
        conf.put("mock", true)

        val request =
            StorageCreateRequest(
                desc = "test",
                type = type,
                conf = conf,
            )
        graph.storageDdl
            .create(name, request)
            .test()
            .assertNext { ddlResult ->
                ddlResult.status shouldBe DdlStatus.Status.CREATED
            }.verifyComplete()
    }

    private fun getMetadata(
        db: Database,
        metadataTable: MetadataTable,
    ): Flux<DecodedEdge> =
        transaction(db) {
            metadataTable
                .selectAll()
                .filter { ":" in it[metadataTable.k] }
                .map { DecodedEdge.fromMetastore(KeyValue(it[metadataTable.k], it[metadataTable.v]), emptyMap()) }
        }.toFlux()

    fun getLocalMetadata(): Flux<DecodedEdge> = getMetadata(graph.localMetastore, graph.metadataTable)

    fun getGlobalMetadata(): Flux<DecodedEdge> = getMetadata(graph.metastore, graph.metadataTable)
}

infix fun Graph.shouldContainServicesExactly(names: List<EntityName>) {
    val services = this.serviceDdl.getAll(EntityName(Metadata.origin))
    services
        .test()
        .assertNext { list ->
            println("!!!!!!!!!!!!1")
            println(list)
            list.content.map { it.name.fullQualifiedName } shouldContainExactlyInAnyOrder names.map { it.fullQualifiedName }
        }.verifyComplete()
}

infix fun Graph.shouldContainStoragesExactly(names: List<EntityName>) {
    val storages = this.storageDdl.getAll(EntityName.origin)
    storages
        .test()
        .assertNext { list ->
            list.content.map { it.name } shouldContainExactlyInAnyOrder names
        }.verifyComplete()
}

infix fun Graph.shouldContainSystemLabelsExactly(names: List<EntityName>) {
    val labels = this.labelDdl.getAll(EntityName("sys"))
    labels
        .test()
        .assertNext { list ->
            list.content.map { it.name } shouldContainExactlyInAnyOrder names
        }.verifyComplete()
}

infix fun Graph.shouldContainTestLabel(name: EntityName) {
    val labels = this.labelDdl.getAll(EntityName("test"))
    labels
        .test()
        .assertNext { list ->
            list.content.map { it.name } shouldContain name
        }.verifyComplete()
}

fun LabelEntity.toRequest(): LabelCreateRequest =
    LabelCreateRequest(
        desc = desc,
        type = type,
        schema = schema,
        dirType = dirType,
        storage = storage,
        indices = indices,
    )
