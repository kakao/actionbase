package com.kakao.actionbase.v2.engine.service.ddl

import com.kakao.actionbase.v2.core.metadata.DirectionType
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.GraphConfig
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.metadata.StorageType
import com.kakao.actionbase.v2.engine.test.GraphFixtures

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import reactor.kotlin.test.test

/**
 * A datastore table is reachable two ways - through a named storage entity, or straight from a
 * label carrying a `datastore://` URI. Only the first used to be checked, which left every
 * v3-created table unguarded: `TableCreateRequest.storage` is always the URI form.
 */
class DatastoreTableReferencesSpec :
    StringSpec({
        val defaultNamespace = "ab_default"

        lateinit var graph: Graph
        lateinit var references: DatastoreTableReferences

        beforeTest {
            graph = GraphFixtures.create()
            references = DatastoreTableReferences(graph, defaultNamespace)
        }

        afterTest { graph.close() }

        fun createLabel(
            name: String,
            storage: String,
        ) {
            graph.labelDdl
                .create(
                    EntityName(GraphFixtures.serviceName, name),
                    LabelCreateRequest(
                        desc = "reference fixture",
                        type = LabelType.HASH,
                        schema = GraphFixtures.sampleSchema,
                        dirType = DirectionType.OUT,
                        storage = storage,
                    ),
                ).test()
                .assertNext { it.status shouldBe DdlStatus.Status.CREATED }
                .verifyComplete()
        }

        fun deactivate() =
            LabelUpdateRequest(
                active = false,
                desc = null,
                type = null,
                schema = null,
                groups = null,
                indices = null,
                readOnly = null,
                mode = null,
                caches = null,
            )

        fun createHbaseStorage(
            name: String,
            namespace: String,
            tableName: String,
        ) {
            val conf =
                jacksonObjectMapper().createObjectNode().apply {
                    put("namespace", namespace)
                    put("tableName", tableName)
                }
            GraphFixtures.createStorage(graph, name, StorageType.HBASE, conf)
        }

        "a named storage binding is reported" {
            createHbaseStorage("named_storage", "ab_test", "named_bound")

            references
                .findActive("ab_test", "named_bound")
                .test()
                .assertNext { refs ->
                    refs.size shouldBe 1
                    refs.single().kind shouldBe DatastoreTableReference.Kind.STORAGE
                    refs.single().name shouldBe EntityName.fromOrigin("named_storage")
                }.verifyComplete()
        }

        "a datastore URI binding is reported" {
            createLabel("uri_label", "datastore://ab_test/uri_bound")

            references
                .findActive("ab_test", "uri_bound")
                .test()
                .assertNext { refs ->
                    refs.size shouldBe 1
                    refs.single().kind shouldBe DatastoreTableReference.Kind.LABEL
                    refs.single().name shouldBe EntityName(GraphFixtures.serviceName, "uri_label")
                }.verifyComplete()
        }

        "a URI omitting the namespace resolves to the default namespace" {
            createLabel("short_uri_label", "datastore:///short_bound")

            references
                .findActive(defaultNamespace, "short_bound")
                .test()
                .assertNext { refs -> refs.size shouldBe 1 }
                .verifyComplete()
        }

        "without a default namespace, a namespace-less URI matches on table name alone" {
            createLabel("roaming_label", "datastore:///roaming_bound")
            val clusterBlind = DatastoreTableReferences(graph, defaultNamespace = null)

            clusterBlind
                .findActive("some_other_namespace", "roaming_bound")
                .test()
                .assertNext { refs -> refs.size shouldBe 1 }
                .verifyComplete()

            clusterBlind
                .findActive("some_other_namespace", "unrelated_table")
                .test()
                .assertNext { refs -> refs.isEmpty() shouldBe true }
                .verifyComplete()
        }

        "refuses to answer when the metadata scan is truncated" {
            // A page filled to metadataFetchLimit can't prove the table is unreferenced, and the
            // caller is deciding whether to drop it.
            val truncating =
                GraphFixtures.create(GraphConfig.Builder().withMetadataFetchLimit(1), withTestData = false)
            try {
                GraphFixtures.createStorage(
                    truncating,
                    "only_storage",
                    StorageType.HBASE,
                    jacksonObjectMapper().createObjectNode().apply {
                        put("namespace", "ab_test")
                        put("tableName", "filler")
                    },
                )

                shouldThrow<IllegalStateException> {
                    DatastoreTableReferences(truncating, defaultNamespace)
                        .findActive("ab_test", "anything")
                        .block()
                }
            } finally {
                truncating.close()
            }
        }

        "a URI naming another table is not reported" {
            createLabel("other_label", "datastore://ab_test/other_bound")

            references
                .findActive("ab_test", "uri_bound")
                .test()
                .assertNext { refs -> refs.isEmpty() shouldBe true }
                .verifyComplete()
        }

        "a deactivated label no longer blocks, but is still listed" {
            createLabel("stale_label", "datastore://ab_test/stale_bound")
            graph.labelDdl
                .update(EntityName(GraphFixtures.serviceName, "stale_label"), deactivate())
                .test()
                .assertNext { it.status shouldBe DdlStatus.Status.UPDATED }
                .verifyComplete()

            references
                .findActive("ab_test", "stale_bound")
                .test()
                .assertNext { refs -> refs.isEmpty() shouldBe true }
                .verifyComplete()

            references
                .findAll("ab_test", "stale_bound")
                .test()
                .assertNext { refs ->
                    refs.size shouldBe 1
                    refs.single().active shouldBe false
                }.verifyComplete()
        }

        "groups every binding by the table it points at" {
            createHbaseStorage("grouped_storage", "ab_test", "storage_bound")
            createLabel("grouped_label", "datastore://ab_test/label_bound")
            createLabel("grouped_short", "datastore:///short_bound")

            references
                .findAllByTable()
                .test()
                .assertNext { byTable ->
                    byTable["ab_test:storage_bound"]?.single()?.kind shouldBe DatastoreTableReference.Kind.STORAGE
                    byTable["ab_test:label_bound"]?.single()?.kind shouldBe DatastoreTableReference.Kind.LABEL
                    byTable["$defaultNamespace:short_bound"]?.size shouldBe 1
                }.verifyComplete()
        }

        "the namespace filter drops bindings in other namespaces" {
            createLabel("here_label", "datastore://ab_here/here_bound")
            createLabel("there_label", "datastore://ab_there/there_bound")

            references
                .findAllByTable(namespace = "ab_here")
                .test()
                .assertNext { byTable ->
                    byTable.keys shouldBe setOf("ab_here:here_bound")
                }.verifyComplete()
        }

        "both binding forms on one table are reported together" {
            createHbaseStorage("shared_storage", "ab_test", "shared_bound")
            createLabel("shared_uri_label", "datastore://ab_test/shared_bound")

            references
                .findActive("ab_test", "shared_bound")
                .test()
                .assertNext { refs ->
                    refs.map { it.kind }.toSet() shouldBe
                        setOf(DatastoreTableReference.Kind.STORAGE, DatastoreTableReference.Kind.LABEL)
                }.verifyComplete()
        }
    })
