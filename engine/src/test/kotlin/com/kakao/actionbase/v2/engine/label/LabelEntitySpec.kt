package com.kakao.actionbase.v2.engine.label

import com.kakao.actionbase.core.metadata.common.Cache
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.GroupType
import com.kakao.actionbase.v2.core.code.Index
import com.kakao.actionbase.v2.core.code.hbase.Order
import com.kakao.actionbase.v2.core.metadata.Active
import com.kakao.actionbase.v2.core.metadata.DirectionType
import com.kakao.actionbase.v2.core.metadata.LabelType
import com.kakao.actionbase.v2.core.types.DataType
import com.kakao.actionbase.v2.core.types.EdgeSchema
import com.kakao.actionbase.v2.core.types.Field
import com.kakao.actionbase.v2.core.types.StructType
import com.kakao.actionbase.v2.core.types.VertexField
import com.kakao.actionbase.v2.core.types.VertexType
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.edge.HashEdge
import com.kakao.actionbase.v2.engine.entity.EntityName
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.metadata.Metadata
import com.kakao.actionbase.v2.engine.sql.Row
import com.kakao.actionbase.v2.engine.sql.RowWithSchema
import com.kakao.actionbase.v2.engine.test.GraphFixtures

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class LabelEntitySpec :
    StringSpec({

        lateinit var graph: Graph

        beforeTest {
            graph = GraphFixtures.create()
        }

        afterTest {
            graph.close()
        }

        "materialize LabelEntity" {
            with(Metadata) {
                listOf(serviceLabelEntity, storageLabelEntity, labelLabelEntity).forEach {
                    it.materialize(graph)
                }
            }
        }

        "backward compatibility test - label entity" {

            val edge =
                HashEdge(
                    active = Active.ACTIVE,
                    ts = 0,
                    src = "local:test_service",
                    tgt = "test_label",
                    props =
                        mapOf(
                            "schema" to "{\"src\":{\"type\":\"STRING\",\"desc\":\"\"},\"tgt\":{\"type\":\"STRING\",\"desc\":\"\"},\"fields\":[{\"name\":\"test\",\"type\":\"STRING\",\"nullable\":false,\"desc\":\"\"}]}",
                            "indices" to "[]",
                            "readOnly" to false,
                            "storage" to "test",
                            "type" to "HASH",
                            "event" to false,
                            "dirType" to "OUT",
                            "desc" to "test",
                        ),
                )

            LabelEntity.toEntity(edge)
        }

        "backward compatibility test - rowWithSchema" {
            val structTypeList =
                listOf(
                    Field("dir", DataType.STRING, false, "direction"),
                    Field("ts", DataType.LONG, false, "ts"),
                    Field("src", DataType.STRING, false, "{{service}}"),
                    Field("tgt", DataType.STRING, false, "{{label}}"),
                    Field("props_active", DataType.BOOLEAN, true, ""),
                    Field("desc", DataType.STRING, false, ""),
                    Field("type", DataType.STRING, false, ""),
                    Field("schema", DataType.STRING, false, ""),
                    Field("dirType", DataType.STRING, false, ""),
                    Field("storage", DataType.STRING, false, ""),
                    Field("groups", DataType.STRING, true, ""),
                    Field("indices", DataType.STRING, false, ""),
                    Field("caches", DataType.STRING, true, ""),
                    Field("event", DataType.BOOLEAN, false, ""),
                    Field("readOnly", DataType.BOOLEAN, false, ""),
                    Field("mode", DataType.STRING, true, "SYNC"),
                )
            val structType = StructType(structTypeList.toTypedArray())

            val rowArray =
                arrayOf<Any?>(
                    "OUT",
                    1720488341029,
                    "alpha:t3",
                    "test_label",
                    null,
                    "test",
                    "HASH",
                    "{\"src\":{\"type\":\"STRING\",\"desc\":\"\"},\"tgt\":{\"type\":\"STRING\",\"desc\":\"\"},\"fields\":[{\"name\":\"test\",\"type\":\"STRING\",\"nullable\":false,\"desc\":\"\"}]}",
                    "OUT",
                    "test",
                    "[]",
                    "[]",
                    "[]",
                    false,
                    false,
                    null,
                )

            val row = Row(rowArray)

            val rowWithSchema = RowWithSchema(row, structType)

            LabelEntity.toEntity(rowWithSchema)
        }

        // Vertex stores State only — indices/groups/caches/IN-direction are silently dropped
        // by BulkEdgeEncoder and the v3 schema converter. Reject them at construction so the
        // data never reaches storage in an inconsistent state.
        "VERTEX label rejects non-OUT direction" {
            shouldThrow<IllegalArgumentException> {
                LabelEntity(
                    active = true,
                    name = EntityName("db", "users"),
                    desc = "",
                    type = LabelType.VERTEX,
                    schema = vertexSchema(),
                    dirType = DirectionType.BOTH,
                    storage = "test",
                )
            }
        }

        "VERTEX label rejects indices" {
            shouldThrow<IllegalArgumentException> {
                LabelEntity(
                    active = true,
                    name = EntityName("db", "users"),
                    desc = "",
                    type = LabelType.VERTEX,
                    schema = vertexSchema(),
                    dirType = DirectionType.OUT,
                    storage = "test",
                    indices = listOf(Index("name_asc", listOf(Index.Field("name", Order.ASC)))),
                )
            }
        }

        "VERTEX label rejects groups" {
            shouldThrow<IllegalArgumentException> {
                LabelEntity(
                    active = true,
                    name = EntityName("db", "users"),
                    desc = "",
                    type = LabelType.VERTEX,
                    schema = vertexSchema(),
                    dirType = DirectionType.OUT,
                    storage = "test",
                    groups = listOf(Group(group = "g1", type = GroupType.COUNT, fields = emptyList())),
                )
            }
        }

        "VERTEX label rejects caches" {
            shouldThrow<IllegalArgumentException> {
                LabelEntity(
                    active = true,
                    name = EntityName("db", "users"),
                    desc = "",
                    type = LabelType.VERTEX,
                    schema = vertexSchema(),
                    dirType = DirectionType.OUT,
                    storage = "test",
                    caches = listOf(Cache(cache = "c1", fields = emptyList())),
                )
            }
        }

        "VERTEX label accepts the canonical empty shape" {
            LabelEntity(
                active = true,
                name = EntityName("db", "users"),
                desc = "",
                type = LabelType.VERTEX,
                schema = vertexSchema(),
                dirType = DirectionType.OUT,
                storage = "test",
            )
        }

        "LabelEntity JSON serializes caches field" {
            val entity =
                Metadata.serviceLabelEntity.copy(
                    caches = listOf(Cache(cache = "c1", fields = emptyList())),
                )

            val json = jacksonObjectMapper().writeValueAsString(entity)
            json shouldContain "\"caches\""
            json shouldContain "\"c1\""

            val roundTripped = jacksonObjectMapper().readValue(json, LabelEntity::class.java)
            roundTripped.caches.size shouldBe 1
            roundTripped.caches[0].cache shouldBe "c1"
        }
    }) {
    companion object {
        private fun vertexSchema() =
            EdgeSchema(
                VertexField(VertexType.STRING, "id"),
                VertexField(VertexType.STRING, "<vertex>"),
                listOf(Field("name", DataType.STRING, false, "")),
            )
    }
}
