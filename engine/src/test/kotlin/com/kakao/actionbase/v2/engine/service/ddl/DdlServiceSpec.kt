package com.kakao.actionbase.v2.engine.service.ddl

import com.kakao.actionbase.v2.core.code.Index
import com.kakao.actionbase.v2.core.code.hbase.Order
import com.kakao.actionbase.v2.core.edge.Edge
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
import com.kakao.actionbase.v2.engine.GraphConfig
import com.kakao.actionbase.v2.engine.audit.Audit
import com.kakao.actionbase.v2.engine.cdc.CdcContext
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.label.EdgeOperationStatus
import com.kakao.actionbase.v2.engine.metadata.Metadata
import com.kakao.actionbase.v2.engine.metadata.StorageType
import com.kakao.actionbase.v2.engine.sql.ScanFilter
import com.kakao.actionbase.v2.engine.test.GraphFixtures
import com.kakao.actionbase.v2.engine.wal.WalLog

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.spyk
import reactor.core.publisher.Mono
import reactor.kotlin.test.test

@Suppress("LargeClass")
class DdlServiceSpec :
    StringSpec({
        lateinit var graph: Graph
        beforeTest {
            graph = GraphFixtures.create()
        }

        afterTest {
            graph.close()
        }

        "label can be created if service exists" {
            val name = EntityName("test", "some_label")
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

        "label cannot be created if service does not exist" {
            val name = EntityName("no_exists", "wrong_label")
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
                            ),
                        ),
                    dirType = DirectionType.OUT,
                    indices = emptyList(),
                    storage = Metadata.metastoreName,
                )

            graph.labelDdl
                .create(name, request)
                .test()
                .verifyError(IllegalArgumentException::class.java)
        }

        "alias cannot be created if service does not exist" {
            val name = EntityName("no_exists", "wrong_alias")
            val request =
                AliasCreateRequest(
                    desc = "test",
                    target = "no_exists.wrong_label",
                )

            graph.aliasDdl
                .create(name, request)
                .test()
                .verifyError(IllegalArgumentException::class.java)
        }

        "query cannot be created if service does not exist" {
            val name = EntityName("no_exists", "wrong_query")
            val request =
                QueryCreateRequest(
                    desc = "test",
                    query = "select * from no_exists.wrong_label",
                    stats = emptyList(),
                )

            graph.queryDdl
                .create(name, request)
                .test()
                .verifyError(IllegalArgumentException::class.java)
        }

        "label can be deactivate if alias for other label exists" {
            val aliasRequest =
                AliasCreateRequest(
                    desc = "test",
                    target = EntityName(GraphFixtures.serviceName, GraphFixtures.hbaseHash).fullQualifiedName,
                )

            graph.aliasDdl
                .create(EntityName(GraphFixtures.serviceName, "hbase_hash_alias"), aliasRequest)
                .test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.CREATED
                }.verifyComplete()

            val labelName =
                EntityName(
                    GraphFixtures.serviceName,
                    GraphFixtures.jdbcHash,
                )

            val labelRequest =
                LabelUpdateRequest(
                    active = false,
                    desc = null,
                    type = null,
                    readOnly = null,
                    mode = null,
                    schema = null,
                    groups = null,
                    indices = null,
                )

            graph.labelDdl
                .update(labelName, labelRequest)
                .test()
                .assertNext {
                    it.status shouldBe DdlStatus.Status.UPDATED
                }.verifyComplete()
        }

        "label can be deactivate if query for other label exists" {
            val queryRequest =
                QueryCreateRequest(
                    desc = "test",
                    query = "select * from test.hbase_hash",
                    stats = emptyList(),
                )

            graph.queryDdl
                .create(EntityName(GraphFixtures.serviceName, GraphFixtures.hbaseHash), queryRequest)
                .test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.CREATED
                }.verifyComplete()

            val labelName =
                EntityName(
                    GraphFixtures.serviceName,
                    GraphFixtures.jdbcHash,
                )

            val labelRequest =
                LabelUpdateRequest(
                    active = false,
                    desc = null,
                    type = null,
                    readOnly = null,
                    mode = null,
                    schema = null,
                    groups = null,
                    indices = null,
                )

            graph.labelDdl
                .update(labelName, labelRequest)
                .test()
                .assertNext {
                    it.status shouldBe DdlStatus.Status.UPDATED
                }.verifyComplete()
        }

        "label cannot be deactivate if active query exists" {
            val labelName =
                EntityName(
                    GraphFixtures.serviceName,
                    GraphFixtures.jdbcHash,
                )

            val queryRequest =
                QueryCreateRequest(
                    desc = "test",
                    query = "select * from ${labelName.fullQualifiedName}",
                    stats = emptyList(),
                )

            graph.queryDdl
                .create(labelName, queryRequest)
                .test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.CREATED
                }.verifyComplete()

            val labelRequest =
                LabelUpdateRequest(
                    active = false,
                    desc = null,
                    type = null,
                    readOnly = null,
                    mode = null,
                    schema = null,
                    groups = null,
                    indices = null,
                )

            graph.labelDdl
                .update(labelName, labelRequest)
                .test()
                .verifyError(IllegalArgumentException::class.java)
        }

        "label cannot be deactivate if active alias exists" {
            val labelName =
                EntityName(
                    GraphFixtures.serviceName,
                    GraphFixtures.jdbcHash,
                )

            val aliasName =
                EntityName(
                    GraphFixtures.serviceName,
                    "jdbc_alias",
                )

            val aliasRequest =
                AliasCreateRequest(
                    desc = "test",
                    target = labelName.fullQualifiedName,
                )

            graph.aliasDdl
                .create(aliasName, aliasRequest)
                .test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.CREATED
                }.verifyComplete()

            val labelRequest =
                LabelUpdateRequest(
                    active = false,
                    desc = null,
                    type = null,
                    readOnly = null,
                    mode = null,
                    schema = null,
                    groups = null,
                    indices = null,
                )

            graph.labelDdl
                .update(labelName, labelRequest)
                .test()
                .verifyError(IllegalArgumentException::class.java)
        }

        "service cannot be deactivate if active label exists" {
            val serviceName = EntityName.fromOrigin("test")
            val serviceUpdateRequest =
                ServiceUpdateRequest(
                    active = false,
                    desc = null,
                )
            graph.serviceDdl
                .update(serviceName, serviceUpdateRequest)
                .test()
                .verifyError(IllegalArgumentException::class.java)
        }

        "service can be deactivate if active label not exist" {
            val serviceName = EntityName.fromOrigin("test2")
            val serviceCreateRequest = ServiceCreateRequest("test2")
            val serviceUpdateRequest = ServiceUpdateRequest(active = false, desc = null)

            graph.serviceDdl
                .create(serviceName, serviceCreateRequest)
                .test()
                .assertNext {
                    it.status shouldBe DdlStatus.Status.CREATED
                }.verifyComplete()

            graph.serviceDdl
                .update(serviceName, serviceUpdateRequest)
                .test()
                .assertNext {
                    it.status shouldBe DdlStatus.Status.UPDATED
                }.verifyComplete()
        }

        "storage cannot be deactivate if active label exists" {
            val storageName = EntityName.fromOrigin(GraphFixtures.jdbcStorage)
            val storageUpdateRequest = StorageUpdateRequest(false, null, null, null)
            graph.storageDdl
                .update(storageName, storageUpdateRequest)
                .test()
                .verifyError(IllegalArgumentException::class.java)
        }

        "storage can be deactivate if active label not exists" {
            val storageName = EntityName.fromOrigin("test_storage")
            val storageCreateRequest =
                StorageCreateRequest(
                    desc = "test",
                    type = StorageType.NIL,
                    conf = jacksonObjectMapper().createObjectNode(),
                )
            graph.storageDdl
                .create(storageName, storageCreateRequest)
                .test()
                .assertNext {
                    it.status shouldBe DdlStatus.Status.CREATED
                }.verifyComplete()

            val storageUpdateRequest = StorageUpdateRequest(false, null, null, null)
            graph.storageDdl
                .update(storageName, storageUpdateRequest)
                .test()
                .assertNext {
                    it.status shouldBe DdlStatus.Status.UPDATED
                }.verifyComplete()
        }

        "deactivate alias cannot be read" {
            val aliasName = EntityName(GraphFixtures.serviceName, "hbase_hash_alias")
            val aliasCreateRequest =
                AliasCreateRequest(
                    desc = "test",
                    target = EntityName(GraphFixtures.serviceName, GraphFixtures.hbaseHash).fullQualifiedName,
                )
            val aliasUpdateRequest =
                AliasUpdateRequest(
                    active = false,
                    desc = null,
                    target = null,
                )

            graph.aliasDdl
                .create(aliasName, aliasCreateRequest)
                .test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.CREATED
                }.then { graph.updateAliases() }
                .verifyComplete()

            graph.aliasDdl
                .update(aliasName, aliasUpdateRequest)
                .test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.UPDATED
                }.verifyComplete()

            graph
                .updateAliases()
                .then(
                    Mono.fromCallable {
                        val scanFilter = ScanFilter(name = aliasName, srcSet = setOf(100))
                        shouldThrow<UnsupportedOperationException> {
                            graph.singleStepQuery(scanFilter)
                        }
                    },
                ).test()
                .expectNextMatches {
                    it is UnsupportedOperationException
                }.verifyComplete()
        }

        "deactivate query cannot be read" {
            val labelName =
                EntityName(
                    GraphFixtures.serviceName,
                    GraphFixtures.jdbcHash,
                )

            val queryName =
                EntityName(
                    GraphFixtures.serviceName,
                    "test_query",
                )

            val queryCreateRequest =
                QueryCreateRequest(
                    desc = "test",
                    query = "select * from ${labelName.fullQualifiedName}",
                    stats = emptyList(),
                )

            val queryUpdateRequest =
                QueryUpdateRequest(
                    active = false,
                    desc = null,
                    query = null,
                    stats = null,
                )

            graph.queryDdl
                .create(queryName, queryCreateRequest)
                .test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.CREATED
                }.verifyComplete()

            graph.queryDdl
                .update(queryName, queryUpdateRequest)
                .test()
                .assertNext {
                    it.status shouldBe DdlStatus.Status.UPDATED
                }.verifyComplete()
        }

        "deactivate label cannot be read" {
            val labelName =
                EntityName(
                    GraphFixtures.serviceName,
                    "test_label",
                )

            val labelCreateRequest =
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
                            ),
                        ),
                    dirType = DirectionType.OUT,
                    indices = emptyList(),
                    storage = Metadata.metastoreName,
                )

            val labelUpdateRequest =
                LabelUpdateRequest(
                    active = false,
                    desc = null,
                    type = null,
                    readOnly = null,
                    mode = null,
                    schema = null,
                    groups = null,
                    indices = null,
                )

            graph.labelDdl
                .create(labelName, labelCreateRequest)
                .test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.CREATED
                }.verifyComplete()

            graph.labelDdl
                .update(labelName, labelUpdateRequest)
                .test()
                .assertNext {
                    it.status shouldBe DdlStatus.Status.UPDATED
                }.verifyComplete()

            graph
                .updateLabels()
                .then(
                    Mono.fromCallable {
                        shouldThrow<UnsupportedOperationException> {
                            graph.singleStepQuery(ScanFilter(name = labelName, srcSet = setOf(100)))
                        }
                    },
                ).test()
                .expectNextMatches {
                    it is UnsupportedOperationException
                }.verifyComplete()
        }

        "deactivate service cannot be used" {

            val service2Name = EntityName.fromOrigin("test2")
            val serviceCreateRequest = ServiceCreateRequest("test2")
            val serviceUpdateRequest = ServiceUpdateRequest(active = false, desc = null)

            graph.serviceDdl
                .create(service2Name, serviceCreateRequest)
                .test()
                .assertNext {
                    it.status shouldBe DdlStatus.Status.CREATED
                }.verifyComplete()

            graph.serviceDdl
                .update(service2Name, serviceUpdateRequest)
                .test()
                .assertNext {
                    it.status shouldBe DdlStatus.Status.UPDATED
                }.verifyComplete()

            val labelName =
                EntityName(
                    "test2",
                    "test_label",
                )

            val labelCreateRequest =
                LabelCreateRequest(
                    desc = "test label",
                    type = LabelType.HASH,
                    schema =
                        EdgeSchema(
                            VertexField(VertexType.STRING),
                            VertexField(VertexType.STRING),
                            listOf(
                                Field("permission", DataType.STRING, false),
                                Field("memo", DataType.STRING, true),
                            ),
                        ),
                    dirType = DirectionType.OUT,
                    indices = emptyList(),
                    storage = Metadata.metastoreName,
                )

            graph
                .updateServices()
                .then(graph.labelDdl.create(labelName, labelCreateRequest))
                .test()
                .verifyError(IllegalArgumentException::class.java)
        }

        "deactivate storage cannot be used" {
            val storageName = EntityName.fromOrigin("test_storage")
            val storageCreateRequest =
                StorageCreateRequest(
                    desc = "test",
                    type = StorageType.NIL,
                    conf = jacksonObjectMapper().createObjectNode(),
                )
            val storageUpdateRequest = StorageUpdateRequest(active = false, desc = null, type = null, conf = null)

            graph.storageDdl
                .create(storageName, storageCreateRequest)
                .test()
                .assertNext {
                    it.status shouldBe DdlStatus.Status.CREATED
                }.verifyComplete()

            graph.storageDdl
                .update(storageName, storageUpdateRequest)
                .test()
                .assertNext {
                    it.status shouldBe DdlStatus.Status.UPDATED
                }.verifyComplete()

            val labelName =
                EntityName(
                    "test",
                    "test_label",
                )

            val labelCreateRequest =
                LabelCreateRequest(
                    desc = "test label",
                    type = LabelType.HASH,
                    schema =
                        EdgeSchema(
                            VertexField(VertexType.STRING),
                            VertexField(VertexType.STRING),
                            listOf(
                                Field("permission", DataType.STRING, false),
                                Field("memo", DataType.STRING, true),
                            ),
                        ),
                    dirType = DirectionType.OUT,
                    indices = emptyList(),
                    storage = storageName.name!!,
                )

            graph
                .updateStorages()
                .then(graph.labelDdl.create(labelName, labelCreateRequest))
                .test()
                .verifyError(IllegalArgumentException::class.java)
        }

        "alias cannot be created if target not exists" {
            val aliasName =
                EntityName(
                    GraphFixtures.serviceName,
                    "jdbc_alias",
                )

            val aliasRequest =
                AliasCreateRequest(
                    desc = "test",
                    target = "test.not_exists",
                )

            graph.aliasDdl
                .create(aliasName, aliasRequest)
                .test()
                .verifyError(IllegalArgumentException::class.java)
        }

        "label readOnly should be updated" {
            val labelName =
                EntityName(
                    GraphFixtures.serviceName,
                    GraphFixtures.jdbcHash,
                )

            val labelUpdateRequest =
                LabelUpdateRequest(
                    active = null,
                    desc = null,
                    type = null,
                    readOnly = true,
                    mode = null,
                    schema = null,
                    groups = null,
                    indices = null,
                )
            graph.labelDdl
                .update(labelName, labelUpdateRequest)
                .test()
                .assertNext {
                    it.status shouldBe DdlStatus.Status.UPDATED
                    it.result!!.readOnly shouldBe true
                }.verifyComplete()
        }

        "create request with name already exists fail" {
            val alreadyExistsEntityName = EntityName.fromOrigin("test")
            val request = ServiceCreateRequest("test")
            graph.serviceDdl
                .create(alreadyExistsEntityName, request)
                .test()
                .assertNext { it.status shouldBe DdlStatus.Status.ERROR }
                .verifyComplete()
        }

        "delete request on active entity fail" {
            val activeEntityName = EntityName(GraphFixtures.serviceName, GraphFixtures.hbaseIndexed)
            val request = LabelDeleteRequest()
            graph.labelDdl
                .delete(activeEntityName, request)
                .test()
                .verifyError(IllegalArgumentException::class.java)
        }

        "recreate should be success" {
            val labelCreateRequest =
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
                            ),
                        ),
                    dirType = DirectionType.OUT,
                    indices = emptyList(),
                    storage = Metadata.metastoreName,
                )

            graph.labelDdl
                .create(EntityName("test", "some_label"), labelCreateRequest)
                .test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.CREATED
                }.verifyComplete()

            graph.labelDdl
                .update(
                    EntityName("test", "some_label"),
                    LabelUpdateRequest(false, null, null, null, null, null, null, null),
                ).test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.UPDATED
                }.verifyComplete()

            graph.labelDdl
                .delete(EntityName("test", "some_label"), LabelDeleteRequest())
                .test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.DELETED
                }.verifyComplete()

            graph.labelDdl
                .create(EntityName("test", "some_label"), labelCreateRequest)
                .test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.CREATED
                }.verifyComplete()
        }

        "update label schema" {

            val labelName = EntityName("test", "some_label2")

            val labelCreateRequest =
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
                            ),
                        ),
                    dirType = DirectionType.OUT,
                    indices = emptyList(),
                    storage = Metadata.metastoreName,
                )

            graph.labelDdl
                .create(labelName, labelCreateRequest)
                .test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.CREATED
                }.verifyComplete()

            val newSchema =
                EdgeSchema(
                    VertexField(VertexType.STRING),
                    VertexField(VertexType.STRING),
                    listOf(
                        Field("permission", DataType.STRING, false),
                        Field("memo", DataType.STRING, true),
                        Field("new_field", DataType.STRING, true),
                    ),
                )

            graph.labelDdl
                .update(
                    labelName,
                    LabelUpdateRequest(
                        active = null,
                        desc = "test2",
                        type = null,
                        readOnly = null,
                        mode = null,
                        schema = newSchema,
                        groups = null,
                        indices = null,
                    ),
                ).test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.UPDATED
                }.verifyComplete()

            graph.labelDdl
                .getSingle(labelName)
                .test()
                .assertNext { labelEntity ->
                    // change label desc
                    labelEntity.desc shouldBe "test2"
                    // change label schema
                    labelEntity.schema shouldBe newSchema
                }.verifyComplete()
        }

        "read old data after label schema update" {
            val labelName = EntityName("test", "some_label3")

            val labelCreateRequest =
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
                            ),
                        ),
                    dirType = DirectionType.OUT,
                    indices = emptyList(),
                    storage = Metadata.metastoreName,
                )

            graph.labelDdl
                .create(labelName, labelCreateRequest)
                .test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.CREATED
                }.verifyComplete()

            val edges =
                listOf(
                    Edge(10, 100, 1000, mapOf("permission" to "me", "memo" to "mo")).toTraceEdge(),
                )

            graph
                .mutate(labelName, graph.getLabel(labelName), edges, EdgeOperation.INSERT)
                .test()
                .assertNext {
                    it.result[0].status shouldBe EdgeOperationStatus.CREATED
                }.verifyComplete()

            val newSchema =
                EdgeSchema(
                    VertexField(VertexType.STRING),
                    VertexField(VertexType.STRING),
                    listOf(
                        Field("permission", DataType.STRING, false),
                        Field("memo", DataType.STRING, true),
                        Field("new_field", DataType.STRING, true),
                    ),
                )

            graph.labelDdl
                .update(
                    labelName,
                    LabelUpdateRequest(
                        active = null,
                        desc = "test2",
                        type = null,
                        readOnly = null,
                        mode = null,
                        schema = newSchema,
                        groups = null,
                        indices = null,
                    ),
                ).test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.UPDATED
                }.verifyComplete()

            graph.labelDdl
                .getSingle(labelName)
                .test()
                .assertNext { labelEntity ->
                    // change label desc
                    labelEntity.desc shouldBe "test2"
                    // change label schema
                    labelEntity.schema shouldBe newSchema
                }.verifyComplete()

            graph
                .singleStepQuery(ScanFilter(name = labelName, srcSet = setOf(100), tgt = setOf(1000)))
                .test()
                .assertNext {
                    it.toRowWithSchema().size shouldBe 1
                }.verifyComplete()
        }

        "read old and new data using old and new indices" {
            // 1. Create label
            val labelName = EntityName("test", "some_label_for_newIndices")
            val oldIndices =
                listOf(
                    Index(
                        "paid_at_desc",
                        listOf(
                            Index.Field("paidAt", Order.DESC),
                        ),
                    ),
                )
            val labelCreateRequest =
                LabelCreateRequest(
                    desc = "test",
                    type = LabelType.INDEXED,
                    schema =
                        EdgeSchema(
                            VertexField(VertexType.STRING),
                            VertexField(VertexType.STRING),
                            listOf(
                                Field("paidAt", DataType.LONG, false),
                                Field("decidedAt", DataType.LONG, true),
                            ),
                        ),
                    dirType = DirectionType.OUT,
                    indices = oldIndices,
                    storage = GraphFixtures.hbaseStorage,
                )

            graph.labelDdl
                .create(labelName, labelCreateRequest)
                .test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.CREATED
                    ddlResult.result?.indices shouldBe oldIndices
                }.verifyComplete()

            // 2. old edge Insert
            val oldEdges =
                listOf(
                    Edge(10L, 100, 1000, mapOf("paidAt" to 1111, "decidedAt" to 1111)).toTraceEdge(),
                )

            graph
                .mutate(labelName, graph.getLabel(labelName), oldEdges, EdgeOperation.INSERT)
                .test()
                .assertNext {
                    it.result[0].status shouldBe EdgeOperationStatus.CREATED
                }.verifyComplete()

            // 3. Add new index (decided_at_desc) (label update)
            val newIndices =
                listOf(
                    Index(
                        "paid_at_desc",
                        listOf(
                            Index.Field("paidAt", Order.DESC),
                        ),
                    ),
                    Index(
                        "decided_at_desc",
                        listOf(
                            Index.Field("decidedAt", Order.DESC),
                        ),
                    ),
                )

            graph.labelDdl
                .update(
                    labelName,
                    LabelUpdateRequest(
                        active = null,
                        desc = "desc2",
                        type = null,
                        readOnly = null,
                        mode = null,
                        schema = null,
                        groups = null,
                        indices = newIndices,
                    ),
                ).test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.UPDATED
                }.verifyComplete()

            graph.labelDdl
                .getSingle(labelName)
                .test()
                .assertNext { labelEntity ->
                    // change label desc
                    labelEntity.desc shouldBe "desc2"
                    // change label schema
                    labelEntity.indices shouldBe newIndices
                }.verifyComplete()

            // 4. new edge Insert
            val newEdges =
                listOf(
                    Edge(10L, 100L, 2000L, mapOf("paidAt" to 2222, "decidedAt" to 2222)).toTraceEdge(),
                )

            graph
                .mutate(labelName, graph.getLabel(labelName), newEdges, EdgeOperation.INSERT)
                .test()
                .assertNext {
                    it.result[0].status shouldBe EdgeOperationStatus.CREATED
                }.verifyComplete()

            // 5. Query with old index
            graph
                .singleStepQuery(ScanFilter(name = labelName, srcSet = setOf(100), indexName = "paid_at_desc"))
                .test()
                .assertNext {
                    it.toRowWithSchema().size shouldBe 2
                }.verifyComplete()

            // 6. Query with new index
            graph
                .singleStepQuery(ScanFilter(name = labelName, srcSet = setOf(100), indexName = "decided_at_desc"))
                .test()
                .assertNext {
                    it.toRowWithSchema().forEach { row -> println(row) }
                    it.toRowWithSchema().size shouldBe 1
                }.verifyComplete()
        }

        "adding not nullable schema fails" {
            val labelName = EntityName("test", "some_label3")

            val labelCreateRequest =
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
                            ),
                        ),
                    dirType = DirectionType.OUT,
                    indices = emptyList(),
                    storage = Metadata.metastoreName,
                )

            graph.labelDdl
                .create(labelName, labelCreateRequest)
                .test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.CREATED
                }.verifyComplete()

            val newSchema =
                EdgeSchema(
                    VertexField(VertexType.STRING),
                    VertexField(VertexType.STRING),
                    listOf(
                        Field("permission", DataType.STRING, false),
                        Field("memo", DataType.STRING, true),
                        Field("new_field", DataType.STRING, false),
                    ),
                )

            graph.labelDdl
                .update(
                    labelName,
                    LabelUpdateRequest(
                        active = null,
                        desc = "test2",
                        type = null,
                        readOnly = null,
                        mode = null,
                        schema = newSchema,
                        groups = null,
                        indices = null,
                    ),
                ).test()
                .verifyError(IllegalArgumentException::class.java)
        }

        "check wal and cdc audit log" {
            val spyWal = spyk(graph.wal)
            val walField = Graph::class.java.getDeclaredField("wal")
            walField.isAccessible = true
            walField.set(graph, spyWal)

            val spyCdc = spyk(graph.cdc)
            val cdcField = Graph::class.java.getDeclaredField("cdc")
            cdcField.isAccessible = true
            cdcField.set(graph, spyCdc)

            val walQueue = mutableListOf<WalLog>()
            every { spyWal.write(any()) } answers {
                Mono
                    .fromCallable {
                        walQueue.add(firstArg())
                    }.then(Mono.defer { Mono.empty() })
            }

            val cdcQueue = mutableListOf<CdcContext>()
            every { spyCdc.write(any()) } answers {
                Mono
                    .fromCallable {
                        cdcQueue.add(firstArg())
                    }.then(Mono.defer { Mono.empty() })
            }

            val auditLog = Audit(actor = "test_actor")

            graph.labelDdl
                .create(
                    EntityName("test", "label5"),
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
                        audit = auditLog,
                    ),
                ).test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.CREATED
                }.verifyComplete()

            walQueue[0].audit shouldBe auditLog
            cdcQueue[0].audit shouldBe auditLog
        }

        "DDL operations should be synchronous even with systemMutationMode=ASYNC" {
            val graphGlobalAsync =
                GraphFixtures.create(
                    GraphConfig.Builder().withSystemMutationMode(MutationMode.ASYNC),
                    withTestData = false,
                )

            graphGlobalAsync.use { graph ->
                val serviceName = "test_async_service"
                graph.serviceDdl
                    .create(
                        EntityName.fromOrigin(serviceName),
                        ServiceCreateRequest(desc = "test service"),
                    ).test()
                    .assertNext { ddlResult ->
                        ddlResult.status shouldBe DdlStatus.Status.CREATED
                    }.verifyComplete()

                val labelName = EntityName(serviceName, "ddl_async_test_label")
                val labelCreateRequest =
                    LabelCreateRequest(
                        desc = "test",
                        type = LabelType.HASH,
                        schema =
                            EdgeSchema(
                                VertexField(VertexType.STRING),
                                VertexField(VertexType.STRING),
                                listOf(
                                    Field("permission", DataType.STRING, false),
                                ),
                            ),
                        dirType = DirectionType.OUT,
                        indices = emptyList(),
                        storage = Metadata.metastoreName,
                    )

                graph.labelDdl
                    .create(labelName, labelCreateRequest)
                    .test()
                    .assertNext { ddlResult ->
                        ddlResult.status shouldBe DdlStatus.Status.CREATED
                    }.verifyComplete()

                val labelUpdateRequest =
                    LabelUpdateRequest(
                        active = null,
                        desc = "updated desc",
                        type = null,
                        readOnly = null,
                        mode = null,
                        schema = null,
                        groups = null,
                        indices = null,
                    )

                graph.labelDdl
                    .update(labelName, labelUpdateRequest)
                    .test()
                    .assertNext { ddlResult ->
                        ddlResult.status shouldBe DdlStatus.Status.UPDATED
                    }.verifyComplete()

                val deactivateRequest =
                    LabelUpdateRequest(
                        active = false,
                        desc = null,
                        type = null,
                        readOnly = null,
                        mode = null,
                        schema = null,
                        groups = null,
                        indices = null,
                    )

                graph.labelDdl
                    .update(labelName, deactivateRequest)
                    .test()
                    .assertNext { ddlResult ->
                        ddlResult.status shouldBe DdlStatus.Status.UPDATED
                    }.verifyComplete()

                graph.labelDdl
                    .delete(labelName, LabelDeleteRequest())
                    .test()
                    .assertNext { ddlResult ->
                        ddlResult.status shouldBe DdlStatus.Status.DELETED
                    }.verifyComplete()
            }
        }
    })
