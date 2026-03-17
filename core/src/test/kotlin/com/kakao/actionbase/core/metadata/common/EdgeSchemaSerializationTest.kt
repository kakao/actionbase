package com.kakao.actionbase.core.metadata.common

import com.kakao.actionbase.core.java.codec.common.hbase.Order
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Field
import com.kakao.actionbase.core.metadata.common.Index
import com.kakao.actionbase.core.metadata.common.IndexField
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.test.json.PrettyObjectWriter

import kotlin.test.assertEquals

import org.junit.jupiter.api.Test

import com.fasterxml.jackson.module.kotlin.readValue

class EdgeSchemaSerializationTest {
    val prettyWriter = PrettyObjectWriter(indentSize = 2, lineLengthLimit = 80)

    val objectMapper = prettyWriter.objectMapper

    @Test
    fun `deserialize struct type`() {
        // given
        val schemaJson =
            """
            {
              "type": "edge",
              "source": {"type": "long", "comment": "Source node ID"},
              "target": {"type": "long", "comment": "Target node ID"},
              "properties": [
                {
                  "name": "id",
                  "type": "long",
                  "comment": "Identifier",
                  "nullable": false
                },
                {
                  "name": "name",
                  "type": "string",
                  "comment": "name",
                  "nullable": false
                }
              ],
              "direction": "BOTH",
              "indexes": [
                {
                  "index": "updated_at_desc",
                  "fields": [{"field": "version", "order": "DESC"}],
                  "comment": "recent updates"
                }
              ],
              "groups": []
            }
            """.trimIndent()

        // when
        val actual = objectMapper.readValue<ModelSchema>(schemaJson)

        // then
        val expected =
            ModelSchema.Edge(
                source = Field(type = PrimitiveType.LONG, comment = "Source node ID"),
                target = Field(type = PrimitiveType.LONG, comment = "Target node ID"),
                properties =
                    listOf(
                        StructField(name = "id", type = PrimitiveType.LONG, comment = "Identifier", nullable = false),
                        StructField(name = "name", type = PrimitiveType.STRING, comment = "name", nullable = false),
                    ),
                direction = DirectionType.BOTH,
                groups = emptyList(),
                indexes =
                    listOf(
                        Index(
                            index = "updated_at_desc",
                            fields = listOf(IndexField(field = "version", order = Order.DESC)),
                            comment = "recent updates",
                        ),
                    ),
            )
        assertEquals(expected, actual)
    }

    @Test
    fun `serialize edge schema`() {
        // given
        val edgeSchema =
            ModelSchema.Edge(
                source = Field(type = PrimitiveType.LONG, comment = "Source node ID"),
                target = Field(type = PrimitiveType.LONG, comment = "Target node ID"),
                properties =
                    listOf(
                        StructField(name = "id", type = PrimitiveType.LONG, comment = "Identifier", nullable = false),
                        StructField(name = "name", type = PrimitiveType.STRING, comment = "name", nullable = false),
                    ),
                direction = DirectionType.BOTH,
                groups = emptyList(),
                indexes =
                    listOf(
                        Index(
                            index = "updated_at_desc",
                            fields = listOf(IndexField(field = "version", order = Order.DESC)),
                            comment = "recent updates",
                        ),
                    ),
            )

        // when
        val actual = prettyWriter.writeValueAsString(edgeSchema)

        // then
        val expected =
            """
            {
              "type": "edge",
              "source": {"type": "long", "comment": "Source node ID"},
              "target": {"type": "long", "comment": "Target node ID"},
              "properties": [
                {
                  "name": "id",
                  "type": "long",
                  "comment": "Identifier",
                  "nullable": false
                },
                {
                  "name": "name",
                  "type": "string",
                  "comment": "name",
                  "nullable": false
                }
              ],
              "direction": "BOTH",
              "indexes": [
                {
                  "index": "updated_at_desc",
                  "fields": [{"field": "version", "order": "DESC"}],
                  "comment": "recent updates",
                  "primary": -1,
                  "batch": 0
                }
              ],
              "groups": [],
              "caches": []
            }
            """.trimIndent()
        assertEquals(expected, actual)
    }
}
