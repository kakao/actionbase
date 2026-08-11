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
            - uri: datastore://ns1/table1
            # namespace omitted, resolved to the configured default
            - uri: datastore:///my_table
            - uri: datastore:///t
            # a trailing double underscore is an ordinary name
            - uri: datastore://sys__/t
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
            # the reserved __x__ form is out of reach of user input
            - uri: datastore://__sys__/metastore
            - uri: datastore://ns/__count__
            - uri: datastore:///__reserved
            # segments start with a lowercase letter, as every other name does
            - uri: datastore://_ns/t
            - uri: datastore:///_expire
            - uri: datastore://1ns/t
            - uri: datastore:///9t
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
