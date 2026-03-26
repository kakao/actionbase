package com.kakao.actionbase.v2.engine.label

import com.kakao.actionbase.v2.core.metadata.Active
import com.kakao.actionbase.v2.core.types.DataType
import com.kakao.actionbase.v2.core.types.Field
import com.kakao.actionbase.v2.core.types.StructType
import com.kakao.actionbase.v2.engine.Graph
import com.kakao.actionbase.v2.engine.edge.HashEdge
import com.kakao.actionbase.v2.engine.entity.LabelEntity
import com.kakao.actionbase.v2.engine.metadata.Metadata
import com.kakao.actionbase.v2.engine.sql.Row
import com.kakao.actionbase.v2.engine.sql.RowWithSchema
import com.kakao.actionbase.v2.engine.test.GraphFixtures

import io.kotest.core.spec.style.StringSpec

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
    })
