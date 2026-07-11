package com.kakao.actionbase.v2.engine.metadata

import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.service.ddl.DdlStatus
import com.kakao.actionbase.v2.engine.service.ddl.StorageDeleteRequest
import com.kakao.actionbase.v2.engine.service.ddl.StorageUpdateRequest
import com.kakao.actionbase.v2.engine.test.GraphFixtures
import com.kakao.actionbase.v2.engine.test.shouldContainStoragesExactly
import com.kakao.actionbase.v2.engine.test.testFixtures

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import reactor.kotlin.test.test

class StorageSpec :
    StringSpec({

        lateinit var graph: Graph

        beforeTest {
            graph = GraphFixtures.create(withTestData = false)
        }

        afterTest {
            graph.close()
        }

        "getAll" {
            graph shouldContainStoragesExactly GraphFixtures.defaultStorages
        }

        "create" {
            graph.testFixtures.createStorage(EntityName.fromOrigin("test"))
            graph shouldContainStoragesExactly GraphFixtures.defaultStorages + EntityName.fromOrigin("test")
        }

        "get" {
            graph.testFixtures.createStorage(EntityName.fromOrigin("test"))

            graph.storageDdl
                .getSingle(EntityName.fromOrigin("test"))
                .test()
                .assertNext { storageDTO ->
                    storageDTO.name shouldBe EntityName.fromOrigin("test")
                }.verifyComplete()
        }

        "update" {
            graph.testFixtures.createStorage(EntityName.fromOrigin("test"))

            val updateRequest =
                StorageUpdateRequest(
                    active = null,
                    desc = "updated desc",
                    type = null,
                    conf =
                        jacksonObjectMapper().createObjectNode().apply {
                            put("some", "modification")
                        },
                )

            graph.storageDdl
                .update(EntityName.fromOrigin("test"), updateRequest)
                .test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.UPDATED
                }.verifyComplete()

            graph.storageDdl
                .getSingle(EntityName.fromOrigin("test"))
                .test()
                .assertNext { storageDTO ->
                    storageDTO.desc shouldBe updateRequest.desc
                    storageDTO.type shouldBe StorageType.NIL
                    storageDTO.conf shouldBe updateRequest.conf
                }.verifyComplete()
        }

        "update on non-existing storage should be idle" {
            val updateRequest =
                StorageUpdateRequest(
                    active = null,
                    desc = "updated desc",
                    type = null,
                    conf = null,
                )

            graph.storageDdl
                .update(EntityName.fromOrigin("test"), updateRequest)
                .test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.IDLE
                }.verifyComplete()
        }

        "update should not allow mutation on LocalBackedJdbcHashLabel" {
            val updateRequest =
                StorageUpdateRequest(
                    active = null,
                    desc = "updated desc",
                    type = null,
                    conf = null,
                )

            graph.storageDdl
                .update(EntityName.fromOrigin(Metadata.localBackedMetastoreName), updateRequest)
                .test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.IDLE
                }.verifyComplete()
        }

        "delete" {
            graph.testFixtures.createStorage(EntityName.fromOrigin("test"))

            graph shouldContainStoragesExactly GraphFixtures.defaultStorages + EntityName.fromOrigin("test")

            graph.storageDdl
                .delete(EntityName.fromOrigin("test"), StorageDeleteRequest())
                .test()
                .verifyError(IllegalArgumentException::class.java)

            graph shouldContainStoragesExactly GraphFixtures.defaultStorages + EntityName.fromOrigin("test")
        }

        "delete on non-existing storage should be idle" {
            graph.storageDdl
                .delete(EntityName.fromOrigin("test"), StorageDeleteRequest())
                .test()
                .assertNext { ddlResult ->
                    ddlResult.status shouldBe DdlStatus.Status.IDLE
                }.verifyComplete()
        }

        "delete should not allow mutation on LocalBackedJdbcHashLabel" {
            graph.storageDdl
                .delete(EntityName.fromOrigin(Metadata.localBackedMetastoreName), StorageDeleteRequest())
                .test()
                .verifyError(IllegalArgumentException::class.java)
        }
    })
