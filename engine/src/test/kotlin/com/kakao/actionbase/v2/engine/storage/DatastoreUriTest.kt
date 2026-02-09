package com.kakao.actionbase.v2.engine.storage

import kotlin.test.assertEquals

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DatastoreUriTest {
    @Nested
    @DisplayName("parse")
    inner class ParseTest {
        @Test
        fun `parses valid URI`() {
            val (namespace, tableName) = DatastoreUri.parse("datastore://my_namespace/my_table")

            assertEquals("my_namespace", namespace)
            assertEquals("my_table", tableName)
        }

        @Test
        fun `parses URI with empty namespace`() {
            val (namespace, tableName) = DatastoreUri.parse("datastore:///my_table")

            assertEquals("", namespace)
            assertEquals("my_table", tableName)
        }

        @Test
        fun `throws for invalid prefix`() {
            assertThrows<IllegalArgumentException> {
                DatastoreUri.parse("invalid://namespace/table")
            }.also {
                assert(it.message!!.contains("Must start with"))
            }
        }

        @Test
        fun `throws for missing prefix`() {
            assertThrows<IllegalArgumentException> {
                DatastoreUri.parse("namespace/table")
            }.also {
                assert(it.message!!.contains("Must start with"))
            }
        }

        @Test
        fun `throws for missing table name`() {
            assertThrows<IllegalArgumentException> {
                DatastoreUri.parse("datastore://namespace")
            }.also {
                assert(it.message!!.contains("Expected format"))
            }
        }

        @Test
        fun `throws for too many path segments`() {
            assertThrows<IllegalArgumentException> {
                DatastoreUri.parse("datastore://namespace/table/extra")
            }.also {
                assert(it.message!!.contains("Expected format"))
            }
        }

        @Test
        fun `throws for empty URI`() {
            assertThrows<IllegalArgumentException> {
                DatastoreUri.parse("")
            }
        }

        @Test
        fun `throws for invalid namespace characters`() {
            assertThrows<IllegalArgumentException> {
                DatastoreUri.parse("datastore://name space/table")
            }.also {
                assert(it.message!!.contains("Invalid namespace"))
            }
        }

        @Test
        fun `throws for invalid table name characters`() {
            assertThrows<IllegalArgumentException> {
                DatastoreUri.parse("datastore://namespace/table;drop")
            }.also {
                assert(it.message!!.contains("Invalid table name"))
            }
        }

        @Test
        fun `accepts underscore and digits in names`() {
            val (namespace, tableName) = DatastoreUri.parse("datastore://my_namespace_1/my_table_2")

            assertEquals("my_namespace_1", namespace)
            assertEquals("my_table_2", tableName)
        }

        @Test
        fun `throws for uppercase characters`() {
            assertThrows<IllegalArgumentException> {
                DatastoreUri.parse("datastore://MyNamespace/table")
            }.also {
                assert(it.message!!.contains("Invalid namespace"))
            }
        }

        @Test
        fun `throws for hyphen in name`() {
            assertThrows<IllegalArgumentException> {
                DatastoreUri.parse("datastore://namespace/my-table")
            }.also {
                assert(it.message!!.contains("Invalid table name"))
            }
        }
    }
}
