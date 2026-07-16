package com.kakao.actionbase.core.metadata

import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest
import com.kakao.actionbase.test.json.PrettyObjectWriter

import kotlin.test.assertEquals

import com.fasterxml.jackson.module.kotlin.readValue

class TableSerializationTest {
    val prettyWriter = PrettyObjectWriter.DEFAULT

    val objectMapper = prettyWriter.objectMapper

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - descriptor: {
              "type": "edge",
              "tenant": "test_tenant",
              "database": "test_database",
              "table": "test_table",
              "schema": {
                "type": "edge",
                "source": {"type": "long", "comment": "Source node ID"},
                "target": {"type": "long", "comment": "Target node ID"},
                "properties": [
                  {"name": "id", "type": "long", "comment": "Identifier", "nullable": false},
                  {"name": "name", "type": "string", "comment": "name", "nullable": false}
                ],
                "direction": "BOTH",
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
                ],
                "indexes": [
                  {
                    "index": "updated_at_desc",
                    "fields": [{"field": "version", "order": "DESC"}],
                    "comment": "recent updates"
                  }
                ]
              },
              "mode": "SYNC",
              "storage": "datastore://test_namespace/test_tenant:test_table"
            }
          expected: |-
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
        """,
    )
    fun `serializes edge table to JSON`(
        descriptor: TableDescriptor<*>,
        expected: String,
    ) {
        assertEquals(expected, prettyWriter.writeValueAsString(descriptor))
    }

    @ObjectSourceParameterizedTest
    @ObjectSource(
        """
        - input: |-
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
          expected: {
              "type": "edge",
              "tenant": "test_tenant",
              "database": "test_database",
              "table": "test_table",
              "schema": {
                "type": "edge",
                "source": {"type": "long", "comment": "Source node ID"},
                "target": {"type": "long", "comment": "Target node ID"},
                "properties": [
                  {"name": "id", "type": "long", "comment": "Identifier", "nullable": false},
                  {"name": "name", "type": "string", "comment": "name", "nullable": false}
                ],
                "direction": "BOTH",
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
                ],
                "indexes": [
                  {
                    "index": "updated_at_desc",
                    "fields": [{"field": "version", "order": "DESC"}],
                    "comment": "recent updates"
                  }
                ]
              },
              "mode": "SYNC",
              "storage": "datastore://test_namespace/test_tenant:test_table"
            }
        """,
    )
    fun `deserializes edge table from JSON`(
        input: String,
        expected: TableDescriptor<*>,
    ) {
        assertEquals(expected, objectMapper.readValue<TableDescriptor<*>>(input))
    }
}
