package com.kakao.actionbase.v2.engine.storage

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

import kotlin.test.assertEquals

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
    }
}
