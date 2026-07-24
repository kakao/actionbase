package com.kakao.actionbase.core

import com.kakao.actionbase.test.documentations.params.ObjectSource
import com.kakao.actionbase.test.documentations.params.ObjectSourceParameterizedTest

import kotlin.test.assertFalse
import kotlin.test.assertTrue

import org.junit.jupiter.api.Nested

class StorageUriPatternTest {
    private val regex = Regex(Constants.Name.STORAGE_URI_PATTERN)

    @Nested
    inner class Accepts {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            - uri: datastore://my_namespace/my_table
            - uri: datastore://ns/t
            # namespace omitted, resolved to the configured default
            - uri: datastore:///my_table
            - uri: datastore:///t
            """,
        )
        fun `valid storage URIs`(uri: String) {
            assertTrue(regex.matches(uri), "expected $uri to match")
        }
    }

    @Nested
    inner class Rejects {
        @ObjectSourceParameterizedTest
        @ObjectSource(
            """
            # table segment is always required
            - uri: datastore://ns/
            - uri: datastore:///
            # namespace, when present, must start with a lowercase letter
            - uri: datastore://1ns/t
            - uri: datastore://_ns/t
            # single segment (no ///) is not the omitted form
            - uri: datastore://t
            # uppercase and hyphen are not allowed
            - uri: datastore://Ns/t
            - uri: datastore:///my-table
            """,
        )
        fun `invalid storage URIs`(uri: String) {
            assertFalse(regex.matches(uri), "expected $uri not to match")
        }
    }
}
