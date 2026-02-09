package com.kakao.actionbase.engine.storage

import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest

import kotlin.test.assertEquals

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows

class DatastoreUriTest {
    @Nested
    @DisplayName("parse")
    inner class ParseTest {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - uri: datastore://my_namespace/my_table
              namespace: my_namespace
              table: my_table
            - uri: datastore:///my_table
              namespace: ""
              table: my_table
            - uri: datastore://my_namespace_1/my_table_2
              namespace: my_namespace_1
              table: my_table_2
            - uri: datastore://ns/t
              namespace: ns
              table: t
            """,
        )
        fun `valid URI`(
            uri: String,
            namespace: String,
            table: String,
        ) {
            val (ns, tbl) = DatastoreUri.parse(uri)
            assertEquals(namespace, ns)
            assertEquals(table, tbl)
        }

        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            # missing or wrong prefix
            - uri: ""
              error: Must start with
            - uri: invalid://ns/table
              error: Must start with
            - uri: ns/table
              error: Must start with

            # wrong number of path segments
            - uri: datastore://ns
              error: Expected format
            - uri: datastore://ns/table/extra
              error: Expected format

            # invalid characters
            - uri: datastore://name space/table
              error: Invalid namespace
            - uri: datastore://ns/table;drop
              error: Invalid table name

            # uppercase not allowed
            - uri: datastore://MyNamespace/table
              error: Invalid namespace

            # hyphen not allowed
            - uri: datastore://ns/my-table
              error: Invalid table name
            """,
        )
        fun `invalid URI`(
            uri: String,
            error: String,
        ) {
            assertThrows<IllegalArgumentException> {
                DatastoreUri.parse(uri)
            }.also {
                assert(it.message!!.contains(error))
            }
        }
    }
}
