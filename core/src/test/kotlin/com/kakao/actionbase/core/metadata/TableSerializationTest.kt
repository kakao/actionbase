package com.kakao.actionbase.core.metadata

import com.kakao.actionbase.core.java.codec.common.hbase.Order
import com.kakao.actionbase.core.metadata.common.Bucket
import com.kakao.actionbase.core.metadata.common.DirectionType
import com.kakao.actionbase.core.metadata.common.Field
import com.kakao.actionbase.core.metadata.common.Group
import com.kakao.actionbase.core.metadata.common.GroupType
import com.kakao.actionbase.core.metadata.common.Index
import com.kakao.actionbase.core.metadata.common.IndexField
import com.kakao.actionbase.core.metadata.common.ModelSchema
import com.kakao.actionbase.core.metadata.common.MutationMode
import com.kakao.actionbase.core.metadata.common.StructField
import com.kakao.actionbase.core.types.PrimitiveType
import com.kakao.actionbase.test.json.PrettyObjectWriter

import kotlin.test.Test
import kotlin.test.assertEquals

import com.fasterxml.jackson.module.kotlin.readValue

class TableSerializationTest {
    val prettyWriter = PrettyObjectWriter(indentSize = 2, lineLengthLimit = 80)

    val objectMapper = prettyWriter.objectMapper

    @Test
    fun `test edge table serialization`() {
        // given
        val edgeTableDescriptor =
            TableDescriptor.Edge(
                tenant = "test_tenant",
                database = "test_database",
                table = "test_table",
                schema =
                    ModelSchema.Edge(
                        source =
                            Field(
                                type = PrimitiveType.LONG,
                                comment = "Source node ID",
                            ),
                        target =
                            Field(
                                type = PrimitiveType.LONG,
                                comment = "Target node ID",
                            ),
                        properties =
                            listOf(
                                StructField(name = "id", type = PrimitiveType.LONG, comment = "Identifier", nullable = false),
                                StructField(name = "name", type = PrimitiveType.STRING, comment = "name", nullable = false),
                            ),
                        direction = DirectionType.BOTH,
                        groups =
                            listOf(
                                Group(
                                    group = "by_day",
                                    type = GroupType.COUNT,
                                    fields =
                                        listOf(
                                            Group.Field(name = "version", bucket = Bucket.Date("date_id", Bucket.ValueUnit.MILLISECOND, "+09:00", "yyyy-MM-dd")),
                                        ),
                                    comment = "group by day",
                                ),
                            ),
                        indexes =
                            listOf(
                                Index(
                                    index = "updated_at_desc",
                                    fields = listOf(IndexField(field = "version", order = Order.DESC)),
                                    comment = "recent updates",
                                ),
                            ),
                    ),
                mode = MutationMode.SYNC,
                storage = "datastore://test_namespace/test_tenant:test_table",
            )

        // when
        val actual = prettyWriter.writeValueAsString(edgeTableDescriptor)

        // then
        val expected =
            """
            {
              "type": "edge",
              "tenant": "test_tenant",
              "database": "test_database",
              "table": "test_table",
              "schema": {
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
                "groups": [
                  {
                    "group": "by_day",
                    "type": "COUNT",
                    "fields": [
                      {
                        "name": "version",
                        "bucket": {
                          "type": "date",
                          "name": "date_id",
                          "unit": "MILLISECOND",
                          "timezone": "+09:00",
                          "format": "yyyy-MM-dd"
                        }
                      }
                    ],
                    "valueField": "-",
                    "comment": "group by day",
                    "directionType": "BOTH",
                    "ttl": 691200000
                  }
                ],
                "caches": []
              },
              "mode": "SYNC",
              "storage": "datastore://test_namespace/test_tenant:test_table",
              "active": true,
              "comment": "",
              "revision": -1,
              "createdAt": -1,
              "createdBy": "",
              "updatedAt": -1,
              "updatedBy": ""
            }
            """.trimIndent()
        assertEquals(expected, actual)
    }

    @Test
    fun `test edge table deserialization`() {
        // given
        val json =
            """
            {
              "type": "edge",
              "tenant": "test_tenant",
              "database": "test_database",
              "table": "test_table",
              "schema": {
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
                "groups": [
                  {
                    "group": "by_day",
                    "type": "COUNT",
                    "fields": [
                      {
                        "name": "version",
                        "bucket": {
                          "type": "date",
                          "name": "date_id",
                          "unit": "MILLISECOND",
                          "timezone": "+09:00",
                          "format": "yyyy-MM-dd"
                        }
                      }
                    ],
                    "comment": "group by day"
                  }
                ]
              },
              "mode": "SYNC",
              "storage": "datastore://test_namespace/test_tenant:test_table"
            }
            """.trimIndent()

        // when
        val actual = objectMapper.readValue<TableDescriptor<*>>(json)

        // then
        val expected =
            TableDescriptor.Edge(
                tenant = "test_tenant",
                database = "test_database",
                table = "test_table",
                schema =
                    ModelSchema.Edge(
                        source =
                            Field(
                                type = PrimitiveType.LONG,
                                comment = "Source node ID",
                            ),
                        target =
                            Field(
                                type = PrimitiveType.LONG,
                                comment = "Target node ID",
                            ),
                        properties =
                            listOf(
                                StructField(name = "id", type = PrimitiveType.LONG, comment = "Identifier", nullable = false),
                                StructField(name = "name", type = PrimitiveType.STRING, comment = "name", nullable = false),
                            ),
                        direction = DirectionType.BOTH,
                        groups =
                            listOf(
                                Group(
                                    group = "by_day",
                                    type = GroupType.COUNT,
                                    fields =
                                        listOf(
                                            Group.Field(name = "version", bucket = Bucket.Date("date_id", Bucket.ValueUnit.MILLISECOND, "+09:00", "yyyy-MM-dd")),
                                        ),
                                    comment = "group by day",
                                ),
                            ),
                        indexes =
                            listOf(
                                Index(
                                    index = "updated_at_desc",
                                    fields = listOf(IndexField(field = "version", order = Order.DESC)),
                                    comment = "recent updates",
                                ),
                            ),
                    ),
                mode = MutationMode.SYNC,
                storage = "datastore://test_namespace/test_tenant:test_table",
            )
        assertEquals(expected, actual)
    }
}
