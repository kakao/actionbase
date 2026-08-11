package com.kakao.actionbase.v2.engine.v3

import com.kakao.actionbase.v2.core.metadata.Direction
import com.kakao.actionbase.v2.core.metadata.DirectionType
import com.kakao.actionbase.v2.core.metadata.EdgeOperation
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.label.EdgeOperationStatus
import com.kakao.actionbase.v2.engine.label.hbase.HBaseIndexedLabel
import com.kakao.actionbase.v2.engine.service.ddl.DdlStatus
import com.kakao.actionbase.v2.engine.test.GraphFixtures
import com.kakao.actionbase.v2.engine.test.toRequest

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import reactor.kotlin.test.test

class DatastoreHashLabelSpec :
    StringSpec({

        lateinit var graph: Graph

        beforeTest {
            graph = GraphFixtures.create()
        }

        afterTest {
            graph.close()
        }

        val labelName = EntityName(GraphFixtures.serviceName, "datastore_hash_regression")

        fun createDatastoreHashLabel() {
            val entity =
                LabelEntity(
                    active = true,
                    name = labelName,
                    desc = "datastore hash label",
                    type = LabelType.HASH,
                    schema = GraphFixtures.sampleSchema,
                    dirType = DirectionType.OUT,
                    storage = GraphFixtures.datastoreStorage,
                )

            graph.labelDdl
                .create(labelName, entity.toRequest())
                .test()
                .assertNext { it.status shouldBe DdlStatus.Status.CREATED }
                .verifyComplete()
        }

        "datastore HASH label materializes as an IndexedLabel-backed label" {
            createDatastoreHashLabel()

            graph.getLabel(labelName).shouldBeInstanceOf<HBaseIndexedLabel>()
        }

        "V2BackedEngine resolves a table binding for a datastore HASH label" {
            createDatastoreHashLabel()

            val engine = V2BackedEngine(graph)

            engine.getTableBinding(GraphFixtures.serviceName, labelName.nameNotNull)
        }

        "datastore HASH label accepts edge mutation" {
            createDatastoreHashLabel()

            val label = graph.getLabel(labelName)

            graph
                .mutate(label.name, label, GraphFixtures.sampleEdges.map { it.toTraceEdge() }, EdgeOperation.INSERT)
                .test()
                .assertNext {
                    it.result.count { result -> result.status == EdgeOperationStatus.CREATED } shouldBe
                        GraphFixtures.sampleEdges.size
                }.verifyComplete()
        }

        "datastore HASH label serves get after edge mutation" {
            createDatastoreHashLabel()

            val label = graph.getLabel(labelName)

            graph
                .mutate(label.name, label, GraphFixtures.sampleEdges.map { it.toTraceEdge() }, EdgeOperation.INSERT)
                .test()
                .assertNext {
                    it.result.count { result -> result.status == EdgeOperationStatus.CREATED } shouldBe
                        GraphFixtures.sampleEdges.size
                }.verifyComplete()

            label
                .get(src = 100, tgt = 1000, dir = Direction.OUT, stats = emptySet())
                .test()
                .assertNext { it.rows.size shouldBe 1 }
                .verifyComplete()
        }
    })
