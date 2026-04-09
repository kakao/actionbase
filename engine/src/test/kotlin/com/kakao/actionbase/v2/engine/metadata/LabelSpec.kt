package com.kakao.actionbase.v2.engine.metadata

import com.kakao.actionbase.v2.core.code.Index
import com.kakao.actionbase.v2.core.code.hbase.Order
import com.kakao.actionbase.v2.core.metadata.DirectionType
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.core.metadata.MutationMode
import com.kakao.actionbase.v2.core.types.DataType
import com.kakao.actionbase.v2.core.types.EdgeSchema
import com.kakao.actionbase.v2.core.types.Field
import com.kakao.actionbase.v2.core.types.VertexField
import com.kakao.actionbase.v2.core.types.VertexType
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.service.ddl.AliasCreateRequest
import com.kakao.actionbase.v2.engine.service.ddl.DdlStatus
import com.kakao.actionbase.v2.engine.service.ddl.LabelCreateRequest
import com.kakao.actionbase.v2.engine.service.ddl.LabelDeleteRequest
import com.kakao.actionbase.v2.engine.service.ddl.LabelUpdateRequest
import com.kakao.actionbase.v2.engine.test.GraphFixtures
import com.kakao.actionbase.v2.engine.test.shouldContainSystemLabelsExactly
import com.kakao.actionbase.v2.engine.test.testFixtures

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import reactor.core.publisher.Mono
import reactor.kotlin.test.test

class LabelSpec :
    StringSpec({

        lateinit var graph: Graph

        beforeTest {
            graph = GraphFixtures.create()
        }

        afterTest {
            graph.close()
        }

        "getAll" {
            graph shouldContainSystemLabelsExactly GraphFixtures.defaultLabels
        }

        "create" {
            graph.testFixtures.createLabel(EntityName("sys", "test"))
            graph shouldContainSystemLabelsExactly GraphFixtures.defaultLabels + EntityName("sys", "test")
        }

        "get" {
            graph.testFixtures.createLabel(EntityName("sys", "test"))

            graph.labelDdl
                .getSingle(EntityName("sys", "service"))
                .test()
                .assertNext { labelDTO ->
                    labelDTO.name shouldBe EntityName("sys", "service")
                }.verifyComplete()

            graph.labelDdl
                .getSingle(EntityName("sys", "test"))
                .test()
                .assertNext { labelDTO ->
                    labelDTO.name shouldBe EntityName("sys", "test")
                }.verifyComplete()
        }

        "update" {
            graph.testFixtures.createLabel(EntityName("sys", "test"))

            val updateRequest =
                LabelUpdateRequest(
                    active = null,
                    desc = "updated desc",
                    type = null,
                    readOnly = null,
                    mode = null,
                    schema = null,
                    groups = null,
                    indices = null,
                    caches = null,
                )

            graph.labelDdl
                .update(EntityName("sys", "test"), updateRequest)
                .test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.UPDATED
                }.verifyComplete()

            graph.labelDdl
                .getSingle(EntityName("sys", "test"))
                .test()
                .assertNext { labelDTO ->
                    labelDTO.desc shouldBe updateRequest.desc
                }.verifyComplete()
        }

        "update on non-existing label should be idle" {
            val updateRequest =
                LabelUpdateRequest(
                    active = null,
                    desc = "updated desc",
                    type = null,
                    readOnly = null,
                    mode = null,
                    schema = null,
                    groups = null,
                    indices = null,
                    caches = null,
                )

            graph.labelDdl
                .update(EntityName("sys", "test"), updateRequest)
                .test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.IDLE
                }.verifyComplete()
        }

        "update should not allow mutation on LocalBackedJdbcHashLabel" {
            val updateRequest =
                LabelUpdateRequest(
                    active = null,
                    desc = "updated desc",
                    type = null,
                    readOnly = null,
                    mode = null,
                    schema = null,
                    groups = null,
                    indices = null,
                    caches = null,
                )

            graph.labelDdl
                .update(EntityName("sys", "service"), updateRequest)
                .test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.IDLE
                }.verifyComplete()
        }

        "update schema and indices" {
            graph.testFixtures.createLabel(EntityName("sys", "test"))

            val schema =
                EdgeSchema(
                    VertexField(VertexType.STRING),
                    VertexField(VertexType.STRING),
                    listOf(
                        Field("s", DataType.STRING, true),
                        Field("l", DataType.LONG, true),
                    ),
                )

            val indices =
                listOf(
                    Index(
                        "ts_desc",
                        listOf(
                            Index.Field("ts", Order.DESC),
                        ),
                    ),
                    Index(
                        "s_asc",
                        listOf(
                            Index.Field("s", Order.ASC),
                        ),
                    ),
                    Index(
                        "l_asc",
                        listOf(
                            Index.Field("l", Order.ASC),
                        ),
                    ),
                )

            val updateRequest =
                LabelUpdateRequest(
                    active = null,
                    desc = "updated desc",
                    type = null,
                    readOnly = null,
                    mode = null,
                    schema = schema,
                    groups = null,
                    indices = indices,
                    caches = null,
                )

            graph.labelDdl
                .update(EntityName("sys", "test"), updateRequest)
                .test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.UPDATED
                }.verifyComplete()

            graph.labelDdl
                .getSingle(EntityName("sys", "test"))
                .test()
                .assertNext { labelDTO ->
                    labelDTO.desc shouldBe updateRequest.desc
                }.verifyComplete()
        }

        "delete" {
            graph.testFixtures.createLabel(EntityName("sys", "test"))

            graph shouldContainSystemLabelsExactly GraphFixtures.defaultLabels + EntityName("sys", "test")

            graph.labelDdl
                .delete(EntityName("sys", "test"), LabelDeleteRequest())
                .test()
                .verifyError(IllegalArgumentException::class.java)

            graph shouldContainSystemLabelsExactly GraphFixtures.defaultLabels + EntityName("sys", "test")
        }

        "delete on non-existing label should be idle" {
            graph.labelDdl
                .delete(EntityName("sys", "test"), LabelDeleteRequest())
                .test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.IDLE
                }.verifyComplete()
        }

        "delete should not allow mutation on LocalBackedJdbcHashLabel" {
            graph.labelDdl
                .delete(EntityName("sys", "service"), LabelDeleteRequest())
                .test()
                .verifyError(IllegalArgumentException::class.java)
        }

        "label cannot be made if a alias has a same name" {
            graph.labelDdl
                .create(
                    EntityName("test", "label1"),
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
                        storage = GraphFixtures.jdbcStorage,
                    ),
                ).then(
                    Mono.defer {
                        graph.aliasDdl.create(
                            EntityName("test", "alias"),
                            AliasCreateRequest("", "test.label1"),
                        )
                    },
                ).then(
                    Mono.defer {
                        graph.labelDdl.create(
                            EntityName("test", "alias"),
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
                                storage = GraphFixtures.jdbcStorage,
                            ),
                        )
                    },
                ).test()
                .verifyError(IllegalArgumentException::class.java)
        }

        "create async label" {
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
                                    Field("test", DataType.STRING, false),
                                ),
                            ),
                        dirType = DirectionType.OUT,
                        storage = GraphFixtures.jdbcStorage,
                        mode = MutationMode.ASYNC,
                    ),
                ).test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.CREATED
                }.verifyComplete()

            graph.labelDdl
                .getSingle(EntityName("test", "async_label"))
                .test()
                .assertNext { labelDTO ->
                    labelDTO.mode shouldBe MutationMode.ASYNC
                }.verifyComplete()
        }
    })
